-- v0.5 多用户与认证:Agency 租户模型
-- 用户属于 agency,房源属于 agency;注册时自动创建单人 agency,
-- 以后加团队协作(邀请同事/角色权限)不需要再改数据模型。

CREATE TABLE agencies (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    agency_id     BIGINT NOT NULL REFERENCES agencies(id),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_agency ON users(agency_id);
