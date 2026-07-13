package com.propertymap.controller.dto;

import com.propertymap.model.Photo;
import com.propertymap.service.PhotoUrlService;
import java.time.LocalDateTime;

/**
 * v0.6:三档 URL(缩略图/中间档/原图)由后端 PhotoUrlService 签好,
 * 前端不再拼接任何路径。dev 下是 /uploads/{key} 相对路径,
 * prod 下是 R2 presigned 绝对 URL(前端按"是否 http 开头"兼容两者)。
 *
 * takenAt = EXIF 拍摄时间,可 NULL。展示规则:非空显示 "Taken {日期}",
 * 为空回退 uploadedAt 显示 "Uploaded {日期}" —— 禁止拿上传时间冒充拍摄时间。
 */
public record PhotoResponse(Long id, String fileName, Long fileSize,
                            LocalDateTime uploadedAt, LocalDateTime takenAt,
                            String thumbnailUrl, String mediumUrl, String originalUrl) {

    public static PhotoResponse from(Photo photo, PhotoUrlService.PhotoUrls urls) {
        return new PhotoResponse(
                photo.getId(), photo.getFileName(), photo.getFileSize(),
                photo.getUploadedAt(), photo.getTakenAt(),
                urls.thumbnailUrl(), urls.mediumUrl(), urls.originalUrl());
    }
}
