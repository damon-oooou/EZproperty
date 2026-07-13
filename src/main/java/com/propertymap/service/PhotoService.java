package com.propertymap.service;

import com.propertymap.model.Photo;
import com.propertymap.model.Room;
import com.propertymap.repository.PhotoRepository;
import com.propertymap.repository.RoomRepository;
import com.propertymap.storage.PhotoKeys;
import com.propertymap.storage.PhotoStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    private final PhotoRepository photoRepository;
    private final RoomRepository roomRepository;
    private final PhotoIngestService photoIngestService;
    private final PhotoStorage photoStorage;

    public Room getRoomOrThrow(Long roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));
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
                saved.add(photoRepository.save(photo));
            }
            return saved;
        } catch (RuntimeException | IOException e) {
            // PhotoStorage.delete 按主 key 一并清理三档变体,且尽力而为不抛出
            for (String key : writtenMainKeys) {
                photoStorage.delete(key);
            }
            throw e;
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
