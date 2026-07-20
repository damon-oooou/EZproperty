package com.propertymap.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // JWT 无状态认证:不需要 session,也就不存在 CSRF 攻击面
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // v0.7:refresh/logout 也放行——两者靠 refresh token 自证,
                // access 已过期时也必须能刷新和登出
                .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/google",
                        "/api/auth/refresh", "/api/auth/logout").permitAll()
                // v0.6:Spring Boot 的 /error 转发必须放行。否则任何未处理异常(500)
                // 都会在 error dispatch 时被判为未认证而变成 401,前端误跳登录页。
                .requestMatchers("/error").permitAll()
                // dev-only:生产走 R2 私有桶 + presigned URL(v0.6),此映射仅本地开发使用
                .requestMatchers("/uploads/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated())
            // 默认未认证会返回 403,显式改成 401,前端据此跳登录页
            .exceptionHandling(e -> e.authenticationEntryPoint(
                (request, response, ex) -> response.sendError(HttpStatus.UNAUTHORIZED.value())))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
