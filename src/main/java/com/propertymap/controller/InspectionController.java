package com.propertymap.controller;

import com.propertymap.controller.dto.CreateInspectionRequest;
import com.propertymap.model.Inspection;
import com.propertymap.model.Photo;
import com.propertymap.service.InspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.io.IOException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService inspectionService;

    @GetMapping("/properties/{propertyId}/inspections")
    public List<Inspection> getInspections(@PathVariable Long propertyId) {
        return inspectionService.getInspectionsForProperty(propertyId);
    }

    @PostMapping("/properties/{propertyId}/inspections")
    @ResponseStatus(HttpStatus.CREATED)
    public Inspection createInspection(@PathVariable Long propertyId,
                                       @RequestBody CreateInspectionRequest request) {
        return inspectionService.createInspection(
                propertyId,
                request.type(),
                request.inspectionDate(),
                request.inheritFromPrevious()
        );
    }

    @GetMapping("/inspections/{inspectionId}/rooms/{roomId}/photos")
    public List<Photo> getPhotos(@PathVariable Long inspectionId,
                                 @PathVariable Long roomId) {
        return inspectionService.getPhotosForRoom(inspectionId, roomId);
    }

    @PostMapping("/inspections/{inspectionId}/rooms/{roomId}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Photo> uploadPhotos(@PathVariable Long inspectionId,
                                    @PathVariable Long roomId,
                                    @RequestParam("files") List<MultipartFile> files) throws IOException {
        return inspectionService.uploadPhotosToInspection(inspectionId, roomId, files);
    }


    @DeleteMapping("/inspections/{inspectionId}/photos")
    public void removePhotos(@PathVariable Long inspectionId,
                             @RequestBody List<Long> photoIds) {
        inspectionService.removePhotosFromInspection(inspectionId, photoIds);
    }
}