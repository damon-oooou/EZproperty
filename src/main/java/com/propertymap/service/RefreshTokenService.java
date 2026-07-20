package com.propertymap.service;

import com.propertymap.model.RefreshToken;
import com.propertymap.model.User;
import com.propertymap.repository.RefreshTokenRepository;
import com.propertymap.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * v0.7 Phase A:refresh token 的签发/轮换/撤销。
 * 设计要点:
 * - 明文只在响应里出现一次,数据库只存 SHA-256,泄库不等于泄 token
 * - 轮换:每次刷新旧 token 作废、发新 token,滑动续期 30 天
 * - 复用检测:已消耗的 token 再次出现 = 明文可能被窃,整个 family 立即撤销
 * - 所有校验失败统一 401 "Invalid refresh token",不区分原因(防枚举,与登录错误口径一致)
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final JwtService jwtService;
    private final long refreshExpirationDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository,
                               JwtService jwtService,
                               @Value("${jwt.refresh-expiration-days}") long refreshExpirationDays) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }

    /** 登录/注册成功后调用:开启一条新的轮换链(新 family)。 */
    @Transactional
    public TokenPair issue(User user) {
        String raw = newRawToken();
        saveToken(user, sha256Hex(raw), UUID.randomUUID());
        return new TokenPair(jwtService.generate(user), raw);
    }

    /**
     * 刷新:消耗旧 token,同 family 发新 token(滑动续期)。
     * 悲观锁保证同一 token 的并发刷新只有一个成功,落败方看到 rotatedAt 非空走复用分支。
     *
     * 校验失败一律返回 null(由调用方转 401),【不在本方法抛异常】——
     * 复用分支要先 revokeFamily 再让事务提交,若在此抛 RuntimeException,
     * 默认回滚会把刚做的整链撤销一起回滚掉(撤销就失效了)。正常返回才能保证撤销落库。
     */
    @Transactional
    public TokenPair rotate(String presented) {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken row = repository.findByTokenHashForUpdate(sha256Hex(presented)).orElse(null);

        // 找不到 / 已撤销 / 已过期:一律无效。无写操作,直接返回 null。
        if (row == null || row.getRevokedAt() != null || row.getExpiresAt().isBefore(now)) {
            return null;
        }
        if (row.getRotatedAt() != null) {
            // 复用攻击:token 被使用第二次,整条链不可信,全部撤销后正常返回(提交撤销)
            repository.revokeFamily(row.getFamilyId(), now);
            return null;
        }

        row.setRotatedAt(now);   // 消耗旧 token
        String raw = newRawToken();
        saveToken(row.getUser(), sha256Hex(raw), row.getFamilyId());
        return new TokenPair(jwtService.generate(row.getUser()), raw);
    }

    /** 登出:撤销整个 family。找不到时静默返回——登出必须幂等,不给前端报错的机会。 */
    @Transactional
    public void revoke(String presented) {
        repository.findByTokenHash(sha256Hex(presented))
                .ifPresent(row -> repository.revokeFamily(row.getFamilyId(), LocalDateTime.now()));
    }

    /** 每日 04:00 删过期 7 天以上的行(留缓冲便于排查)。 */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void purgeExpired() {
        repository.deleteExpiredBefore(LocalDateTime.now().minusDays(7));
    }

    private void saveToken(User user, String tokenHash, UUID familyId) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setFamilyId(familyId);
        token.setExpiresAt(LocalDateTime.now().plusDays(refreshExpirationDays));
        repository.save(token);
    }

    /** 256-bit 随机值,URL-safe Base64 无填充(约 43 字符),客户端持有的就是它。 */
    private String newRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

}
