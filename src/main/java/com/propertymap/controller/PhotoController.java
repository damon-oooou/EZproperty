package com.propertymap.controller;

import com.propertymap.controller.dto.PhotoResponse;
import com.propertymap.controller.dto.UpdatePhotoNoteRequest;
import com.propertymap.model.Photo;
import com.propertymap.service.PhotoService;
import com.propertymap.service.PhotoUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** v0.8:照片级端点(不依赖 inspection 语境的照片属性)。 */
@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;
    private final PhotoUrlService photoUrlService;

    /**
     * 更新照片备注。note 为 null / 空白 = 清空;超过 500 字 → 400。
     * 返回的 PhotoResponse 没有 inspection 语境,carriedForward / origin 为 null。
     */
    @PatchMapping("/{photoId}/note")
    public PhotoResponse updateNote(@PathVariable Long photoId,
                                    @RequestBody UpdatePhotoNoteRequest request) {
        Photo photo = photoService.updateNote(photoId, request.note());
        return PhotoResponse.from(photo, photoUrlService.urlsFor(photo));
    }
}
