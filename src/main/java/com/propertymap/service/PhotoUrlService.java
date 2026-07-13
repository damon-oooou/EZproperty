package com.propertymap.service;

import com.propertymap.model.Photo;
import com.propertymap.storage.PhotoKeys;
import com.propertymap.storage.PhotoStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * v0.6 阶段 B:照片访问 URL 的唯一出口。
 *
 * TTL 24 小时(已拍板,有意偏长):URL 稳定使浏览器缓存生效;
 * 权限真正的把关在 API 层 TenantGuard —— 能调到返回 URL 的接口即已通过租户校验,
 * URL 只是短期通行证。
 */
@Service
@RequiredArgsConstructor
public class PhotoUrlService {

    private static final Duration TTL = Duration.ofHours(24);

    private final PhotoStorage photoStorage;

    public record PhotoUrls(String thumbnailUrl, String mediumUrl, String originalUrl) {}

    public PhotoUrls urlsFor(Photo photo) {
        String key = photo.getStorageKey();
        return new PhotoUrls(
                photoStorage.presignedGetUrl(PhotoKeys.thumbnail(key), TTL),
                photoStorage.presignedGetUrl(PhotoKeys.medium(key), TTL),
                photoStorage.presignedGetUrl(key, TTL));
    }
}
