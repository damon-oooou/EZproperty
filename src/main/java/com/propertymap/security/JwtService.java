package com.propertymap.security;

import com.propertymap.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 的签发与解析。web 和将来的 iOS 端共用同一套 token,
 * 所以 claims 里带上 agencyId,移动端不用额外请求就知道租户上下文。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-hours}") long expirationHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofHours(expirationHours);
    }

    public String generate(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("agencyId", user.getAgency().getId())
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration.toMillis()))
                .signWith(key)
                .compact();
    }

    /** 解析并校验签名/过期。token 非法时抛 JwtException,由过滤器捕获后按未登录处理。 */
    public AuthUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        // 数字 claim 经 JSON 反序列化可能是 Integer,统一走 Number 转 long
        return new AuthUser(
                Long.valueOf(claims.getSubject()),
                ((Number) claims.get("agencyId")).longValue(),
                claims.get("email", String.class));
    }
}
