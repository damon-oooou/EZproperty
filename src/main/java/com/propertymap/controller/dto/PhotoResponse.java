package com.propertymap.controller.dto;

import com.propertymap.model.Photo;
import java.time.LocalDateTime;

/**
 * 注意:不暴露 filePath(服务器内部路径)。
 * 前端只拿到 url,路径规则从此只存在于后端这一处。
 */
public record PhotoResponse(Long id, String fileName, Long fileSize,
                            LocalDateTime uploadedAt, String url) {

    public static PhotoResponse from(Photo photo) {
        String path = photo.getFilePath();
        // 同时兼容 \ 和 /:现有数据是 Windows 反斜杠,将来 Linux 部署产生的是正斜杠
        int idx = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
        String diskFileName = path.substring(idx + 1);
        String url = "/uploads/" + photo.getRoom().getId() + "/" + diskFileName;
        return new PhotoResponse(photo.getId(), photo.getFileName(), photo.getFileSize(),
                photo.getUploadedAt(), url);
    }
}