package com.propertymap.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * v0.7 Phase A:refresh token 的服务端记录。
 * 明文永不落库,tokenHash 是客户端所持随机值的 SHA-256(hex,64 字符)。
 * 同一次登录的轮换链共享一个 familyId——复用检测命中时整条链一起撤销。
 */
@Entity
@Table(name = "refresh_tokens")
@Getter @Setter @NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 非空 = 已被正常轮换消耗;再次出现即视为复用攻击。 */
    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;

    /** 非空 = 已撤销(登出 / 复用检测)。 */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
