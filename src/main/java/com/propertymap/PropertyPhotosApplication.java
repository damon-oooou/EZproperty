package com.propertymap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// v0.7:@EnableScheduling 供 RefreshTokenService 每日清理过期 token 使用
@SpringBootApplication
@EnableScheduling
public class PropertyPhotosApplication {
    public static void main(String[] args) {
        SpringApplication.run(PropertyPhotosApplication.class, args);
    }
}