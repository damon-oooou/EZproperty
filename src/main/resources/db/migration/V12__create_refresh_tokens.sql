-- v0.7 Phase A:refresh token 持久化。
-- 明文永不落库:token_hash 是客户端所持随机值的 SHA-256(hex)。
-- family_id:同一次登录产生的轮换链共享一个 family,是复用检测和整链撤销的单位。
CREATE TABLE refresh_tokens (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash    VARCHAR(64) NOT NULL UNIQUE,
    family_id     UUID        NOT NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMP   NOT NULL,
    rotated_at    TIMESTAMP,   -- 非空 = 已被正常轮换消耗
    revoked_at    TIMESTAMP    -- 非空 = 已撤销(登出 / 复用检测)
);

CREATE INDEX idx_refresh_tokens_user   ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);
