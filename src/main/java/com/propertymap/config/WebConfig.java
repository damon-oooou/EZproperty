package com.propertymap.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * /uploads/** 本地静态照片映射 —— 仅 dev。
 * v0.6 阶段 B:prod 走 R2 私有桶 + presigned URL,此映射在 prod 下不注册,
 * 终结 "/uploads 公开靠 UUID 防猜" 的遗留问题。
 */
@Configuration
@Profile("!prod")
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations("file:uploads/");
    }
}
