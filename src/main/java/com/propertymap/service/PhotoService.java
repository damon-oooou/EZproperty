package com.propertymap.service;

import com.propertymap.model.Photo;
import com.propertymap.model.Room;
import com.propertymap.repository.PhotoRepository;
import com.propertymap.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final RoomRepository roomRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public Room getRoomOrThrow(Long roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));
    }

    public List<Photo> storePhotos(Room room, List<MultipartFile> files) throws IOException {
        // v0.5.2:先整体校验再落盘,避免一批里混入非法文件导致部分写入
        for (MultipartFile file : files) {
            validateImage(file);
        }

        Path uploadPath = Paths.get(uploadDir, String.valueOf(room.getId()));
        Files.createDirectories(uploadPath);

        List<Photo> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath);

            Photo photo = new Photo();
            photo.setRoom(room);
            photo.setFileName(file.getOriginalFilename());
            photo.setFilePath(filePath.toString());
            photo.setFileSize(file.getSize());
            saved.add(photoRepository.save(photo));
        }
        return saved;
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