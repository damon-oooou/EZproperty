package com.propertymap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * v0.5 改为 CorsConfigurationSource bean:之前的 WebMvcConfigurer 方式
 * 只作用于 MVC 层,Spring Security 的过滤器链在它之前就会拦掉预检请求。
 * SecurityConfig 里的 .cors(withDefaults()) 会自动找到这个 bean。
 *
 * v0.6 阶段 C:origins 改为配置驱动。dev 默认 localhost:5173/5174;
 * prod 由环境变量 CORS_ALLOWED_ORIGINS 提供(逗号分隔,
 * 先填 Vercel 生成域名,正式域名生效后换 https://app.<domain>)——加域名不再改代码。
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // 前端要从下载响应里读文件名
        config.setExposedHeaders(List.of("Content-Disposition"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
