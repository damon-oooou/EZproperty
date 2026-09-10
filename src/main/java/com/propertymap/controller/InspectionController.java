package com.propertymap.controller;

import com.propertymap.controller.dto.CreateInspectionRequest;
import com.propertymap.controller.dto.InspectionResponse;
import com.propertymap.controller.dto.PhotoResponse;
import com.propertymap.controller.dto.RoomHistoryResponse;
import com.propertymap.controller.dto.RoomWithPhotoCountResponse;
import com.propertymap.model.Photo;
import com.propertymap.service.InspectionService;
import com.propertymap.service.PhotoHistoryService;
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
    private final PhotoHistoryService photoHistoryService;
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

    /** v0.8:每张照片带 carriedForward + origin(最早引用它的 inspection)。 */
    @GetMapping("/inspections/{inspectionId}/rooms/{roomId}/photos")
    public List<PhotoResponse> getPhotos(@PathVariable Long inspectionId,
                                         @PathVariable Long roomId) {
        return photoHistoryService.photosForRoom(inspectionId, roomId);
    }

    @PostMapping("/inspections/{inspectionId}/rooms/{roomId}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    public List<PhotoResponse> uploadPhotos(@PathVariable Long inspectionId,
                                            @PathVariable Long roomId,
                                            @RequestParam("files") List<MultipartFile> files) throws IOException {
        return inspectionService.uploadPhotosToInspection(inspectionId, roomId, files)
                .stream().map(p -> PhotoResponse.from(p, photoUrlService.urlsFor(p))).toList();
    }

    /**
     * v0.8:添加更新照片。新照片替换 oldPhotoId 在"当前" inspection 里的位置,
     * 旧照片留在它出现过的所有历史 inspection 里。note 必填。
     * 409 = 旧照片已被更新过;404 = 旧照片不在这次 inspection 里;400 = 旧照片属于别的房间 / 缺 note。
     */
    @PostMapping("/inspections/{inspectionId}/rooms/{roomId}/photos/{oldPhotoId}/update")
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoResponse updatePhoto(@PathVariable Long inspectionId,
                                     @PathVariable Long roomId,
                                     @PathVariable Long oldPhotoId,
                                     @RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "note", required = false) String note)
            throws IOException {
        Photo photo = inspectionService.updatePhoto(inspectionId, roomId, oldPhotoId, file, note);
        return PhotoResponse.from(photo, photoUrlService.urlsFor(photo), false, null);
    }

    /** v0.8:房间历史面板(updated / newlyCaptured / carriedForward 三组)。 */
    @GetMapping("/inspections/{inspectionId}/rooms/{roomId}/history")
    public RoomHistoryResponse getRoomHistory(@PathVariable Long inspectionId,
                                              @PathVariable Long roomId) {
        return photoHistoryService.roomHistory(inspectionId, roomId);
    }

    @DeleteMapping("/inspections/{inspectionId}/photos")
    public void removePhotos(@PathVariable Long inspectionId,
                             @RequestBody List<Long> photoIds) {
        inspectionService.removePhotosFromInspection(inspectionId, photoIds);
    }
}
