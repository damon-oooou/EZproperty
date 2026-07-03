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
}