package com.propertymap.security;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v0.6 阶段 D:auth 端点限流(bucket4j 内存实现,按 IP)。
 *   POST /api/auth/login    每 IP 10 次/分钟
 *   POST /api/auth/register 每 IP  5 次/小时
 * 超限返回 429 + JSON message。
 *
 * 真实客户端 IP:Railway 位于反向代理后,取 X-Forwarded-For 的第一跳;
 * 无该头(本地 dev 直连)时回退 remoteAddr。
 *
 * 已知取舍:桶按 IP 存内存 Map,不做过期清理 —— 单实例 MVP 体量下内存可忽略,
 * 引入 Redis/Caffeine 记 backlog。
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String REGISTER_PATH = "/api/auth/register";

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        Bucket bucket = switch (path) {
            case LOGIN_PATH -> loginBuckets.computeIfAbsent(clientIp(request), k -> newLoginBucket());
            case REGISTER_PATH -> registerBuckets.computeIfAbsent(clientIp(request), k -> newRegisterBucket());
            default -> null;
        };

        if (bucket == null || bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"Too many attempts. Please try again later.\"}");
    }

    /** 10 次/分钟(greedy 匀速补充) */
    private Bucket newLoginBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(10).refillGreedy(10, Duration.ofMinutes(1)))
                .build();
    }

    /** 5 次/小时 */
    private Bucket newRegisterBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(5).refillGreedy(5, Duration.ofHours(1)))
                .build();
    }

    /**
     * X-Forwarded-For: "client, proxy1, proxy2" —— 取第一跳。
     * Railway 的边缘代理按此约定注入;头缺失时(本地 dev)用 remoteAddr。
     */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma >= 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}
