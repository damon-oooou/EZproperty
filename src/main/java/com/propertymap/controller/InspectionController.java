package com.propertymap.controller;

import com.propertymap.controller.dto.CreateInspectionRequest;
import com.propertymap.controller.dto.InspectionResponse;
import com.propertymap.controller.dto.PhotoResponse;
import com.propertymap.controller.dto.RoomWithPhotoCountResponse;
import com.propertymap.service.InspectionService;
import com.propertymap.service.PhotoUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService inspectionService;
    private final PhotoUrlService photoUrlService;

    @GetMapping("/properties/{propertyId}/inspections")
    public List<InspectionResponse> getInspections(@PathVariable Long propertyId) {
        return inspectionService.getInspectionsForProperty(propertyId)
                .stream().map(InspectionResponse::from).toList();
    }

    @PostMapping("/properties/{propertyId}/inspections")
    @ResponseStatus(HttpStatus.CREATED)
    public InspectionResponse createInspection(@PathVariable Long propertyId,
                                               @RequestBody CreateInspectionRequest request) {
        return InspectionResponse.from(inspectionService.createInspection(
                propertyId,
                request.type(),
                request.inspectionDate(),
                request.inheritFromPrevious()
        ));
    }

    /** v0.3 新端点:inspection 语境的房间列表,每间带本次照片数。 */
    @GetMapping("/inspections/{inspectionId}/rooms")
    public List<RoomWithPhotoCountResponse> getRoomsForInspection(@PathVariable Long inspectionId) {
        return inspectionService.getRoomsWithPhotoCounts(inspectionId);
    }

    @GetMapping("/inspections/{inspectionId}/rooms/{roomId}/photos")
    public List<PhotoResponse> getPhotos(@PathVariable Long inspectionId,
                                         @PathVariable Long roomId) {
        return inspectionService.getPhotosForRoom(inspectionId, roomId)
                .stream().map(p -> PhotoResponse.from(p, photoUrlService.urlsFor(p))).toList();
    }

    @PostMapping("/inspections/{inspectionId}/rooms/{roomId}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    public List<PhotoResponse> uploadPhotos(@PathVariable Long inspectionId,
                                            @PathVariable Long roomId,
                                            @RequestParam("files") List<MultipartFile> files) throws IOException {
        return inspectionService.uploadPhotosToInspection(inspectionId, roomId, files)
                .stream().map(p -> PhotoResponse.from(p, photoUrlService.urlsFor(p))).toList();
    }

    @DeleteMapping("/inspections/{inspectionId}/photos")
    public void removePhotos(@PathVariable Long inspectionId,
                             @RequestBody List<Long> photoIds) {
        inspectionService.removePhotosFromInspection(inspectionId, photoIds);
    }
}
