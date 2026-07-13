package com.propertymap.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * v0.6 阶段 B:本地文件系统实现(dev 用)。
 * 现有本地存储逻辑收编于此;presignedGetUrl 返回 /uploads/{key} 相对路径,
 * dev 不做签名,保持 README 的本地开发方式不变。
 */
@Service
@Profile("!prod")
@Slf4j
public class LocalPhotoStorage implements PhotoStorage {

    @Value("${file.upload-dir}")
    private String uploadDir;

    private Path dir() {
        return Paths.get(uploadDir);
    }

    @Override
    public void save(String key, byte[] bytes, String contentType) throws IOException {
        Files.createDirectories(dir());
        Files.write(dir().resolve(key), bytes);
    }

    @Override
    public byte[] load(String key) throws IOException {
        return Files.readAllBytes(dir().resolve(key));
    }

    @Override
    public void delete(String key) {
        for (String k : new String[]{key, PhotoKeys.medium(key), PhotoKeys.thumbnail(key)}) {
            try {
                Files.deleteIfExists(dir().resolve(k));
            } catch (IOException e) {
                log.warn("Failed to delete local photo file {}: {}", k, e.getMessage());
            }
        }
    }

    @Override
    public String presignedGetUrl(String key, Duration ttl) {
        return "/uploads/" + key;
    }
}
