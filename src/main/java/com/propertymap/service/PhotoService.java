package com.propertymap.service;

import com.propertymap.model.Photo;
import com.propertymap.model.Room;
import com.propertymap.repository.PhotoRepository;
import com.propertymap.repository.RoomRepository;
import com.propertymap.security.TenantGuard;
import com.propertymap.storage.PhotoKeys;
import com.propertymap.storage.PhotoStorage;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoService {

    /** v0.8:note 上限,与 V13 的 VARCHAR(500) 一致。 */
    public static final int NOTE_MAX_LENGTH = 500;

    private final PhotoRepository photoRepository;
    private final RoomRepository roomRepository;
    private final PhotoIngestService photoIngestService;
    private final PhotoStorage photoStorage;
    private final TenantGuard tenantGuard;

    public Room getRoomOrThrow(Long roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));
    }

    /**
     * v0.8:按 id 取照片并校验租户归属。TenantGuard 只守 property / inspection 两个入口,
     * 照片级端点(PATCH note)没有这两个语境,这里走 photo → room → property 用同一个 check,
     * 不属于当前 agency 同样 404(不泄露 id 存在)。
     */
    public Photo getPhotoForCurrentTenant(Long photoId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new EntityNotFoundException("Photo not found: " + photoId));
        tenantGuard.check(photo.getRoom().getProperty());
        return photo;
    }

    /**
     * v0.8:更新照片备注。备注是照片的属性,会影响所有引用该照片的 inspection 视图——
     * 这是设计意图,已生成的 PDF 是独立文件不受影响。
     * 空白一律存 NULL;超长 400。
     */
    @Transactional
    public Photo updateNote(Long photoId, String note) {
        Photo photo = getPhotoForCurrentTenant(photoId);
        photo.setNote(normalizeNote(note));
        return photoRepository.save(photo);
    }

    /** 去首尾空白,空 → null,超过 500 字 → IllegalArgumentException(400)。 */
    public static String normalizeNote(String note) {
        if (note == null) return null;
        String trimmed = note.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Note is too long (" + trimmed.length() + " characters). "
                    + "Notes must be " + NOTE_MAX_LENGTH + " characters or fewer.");
        }
        return trimmed;
    }

    /**
     * v0.6:上传入口。每个文件先过 v0.5.2 magic-bytes 校验,
     * 再走 PhotoIngestService 规格化管线,产出三档 JPEG 后经 PhotoStorage 落盘 + 入库。
     *
     * 三档必须全部成功才算该照片入库成功:任一档写入失败,本方法删除本批已写的
     * 全部对象后抛出异常,外层事务回滚 DB 行 —— 不留孤儿文件、不留孤儿行。
     */
    public List<Photo> storePhotos(Room room, List<MultipartFile> files) throws IOException {
        // 先整体校验再处理,避免一批里混入非法文件导致部分写入
        for (MultipartFile file : files) {
            validateImage(file);
        }

        List<String> writtenMainKeys = new ArrayList<>();
        List<Photo> saved = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                saved.add(storeOne(room, file, null, null, writtenMainKeys));
            }
            return saved;
        } catch (RuntimeException | IOException e) {
            cleanup(writtenMainKeys);
            throw e;
        }
    }

    /**
     * v0.8:单张上传,同时写入 note 与 replacesPhotoId(用于"添加更新照片")。
     * note / replaces 在 INSERT 时一并写入,而不是入库后再 set:
     * 这样 uq_photos_replaces 冲突会在 INSERT 处立刻抛出,走同一条"失败即清理三档文件"路径,
     * 不会留下孤儿文件(若入库后再 UPDATE,冲突要到 flush 才爆,文件已经写好了)。
     */
    public Photo storePhoto(Room room, MultipartFile file, String note, Long replacesPhotoId)
            throws IOException {
        validateImage(file);
        List<String> writtenMainKeys = new ArrayList<>();
        try {
            return storeOne(room, file, note, replacesPhotoId, writtenMainKeys);
        } catch (RuntimeException | IOException e) {
            cleanup(writtenMainKeys);
            throw e;
        }
    }

    private Photo storeOne(Room room, MultipartFile file, String note, Long replacesPhotoId,
                           List<String> writtenMainKeys) throws IOException {
        String name = file.getOriginalFilename() == null ? "photo" : file.getOriginalFilename();

        PhotoIngestService.IngestResult result =
                photoIngestService.ingest(file.getBytes(), name);

        String key = UUID.randomUUID() + ".jpg";
        writtenMainKeys.add(key); // 先登记再写:写一半失败也能被清理
        photoStorage.save(key, result.original(), "image/jpeg");
        photoStorage.save(PhotoKeys.medium(key), result.medium(), "image/jpeg");
        photoStorage.save(PhotoKeys.thumbnail(key), result.thumbnail(), "image/jpeg");

        Photo photo = new Photo();
        photo.setRoom(room);
        photo.setFileName(name);
        photo.setStorageKey(key);
        photo.setFileSize((long) result.original().length);
        photo.setTakenAt(result.takenAt());
        photo.setNote(note);
        photo.setReplacesPhotoId(replacesPhotoId);
        return photoRepository.save(photo); // IDENTITY 主键:此处立即 INSERT
    }

    /** PhotoStorage.delete 按主 key 一并清理三档变体,且尽力而为不抛出 */
    private void cleanup(List<String> writtenMainKeys) {
        for (String key : writtenMainKeys) {
            photoStorage.delete(key);
        }
    }

    // ===== v0.5.2:上传格式校验(只收 JPEG/PNG,按 magic bytes 判断,不信任声明的 content-type)=====

    private void validateImage(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();

        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        }
        if (read < 12) {
            throw new IllegalArgumentException("\"" + name + "\" is not a valid image file.");
        }

        if (isJpeg(head) || isPng(head)) {
            return;
        }
        if (isHeic(head)) {
            throw new IllegalArgumentException(
                "\"" + name + "\" is a HEIC file, which is not supported. "
                + "Please convert it to JPEG (on iPhone: Settings > Camera > Formats > Most Compatible).");
        }
        throw new IllegalArgumentException(
            "\"" + name + "\" is not a supported image format. Only JPEG and PNG are accepted.");
    }

    /** JPEG: FF D8 FF */
    private boolean isJpeg(byte[] h) {
        return (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
    }

    /** PNG: 89 50 4E 47 0D 0A 1A 0A */
    private boolean isPng(byte[] h) {
        return (h[0] & 0xFF) == 0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47
            && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A;
    }

    /** HEIC/HEIF: ISO-BMFF 容器,偏移 4 起为 "ftyp" + heic/heix/hevc/mif1 等 brand */
    private boolean isHeic(byte[] h) {
        String ftyp = new String(h, 4, 4, StandardCharsets.US_ASCII);
        if (!"ftyp".equals(ftyp)) return false;
        String brand = new String(h, 8, 4, StandardCharsets.US_ASCII);
        return brand.startsWith("hei") || brand.startsWith("hev")
            || brand.equals("mif1") || brand.equals("msf1");
    }
}
