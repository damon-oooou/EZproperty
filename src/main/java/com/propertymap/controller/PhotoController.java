package com.propertymap.controller;

import com.propertymap.model.Photo;
import com.propertymap.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms/{roomId}/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    @GetMapping
    public ResponseEntity<List<Photo>> getPhotos(@PathVariable Long roomId) {
        return ResponseEntity.ok(photoService.getPhotosByRoom(roomId));
    }

    @PostMapping
    public ResponseEntity<List<Photo>> uploadPhotos(
            @PathVariable Long roomId,
            @RequestParam("files") List<MultipartFile> files) throws IOException {
        return ResponseEntity.ok(photoService.uploadPhotos(roomId, files));
    }

    @DeleteMapping
    public ResponseEntity<Void> deletePhotos(@RequestBody Map<String, List<Long>> body) throws IOException {
        photoService.deletePhotos(body.get("photoIds"));
        return ResponseEntity.noContent().build();
    }
}