-- v0.5.1 Google 登录:
-- Google 账号没有本地密码,password_hash 改为可空;
-- auth_provider 区分账号来源(LOCAL = 邮箱密码, GOOGLE = Google 登录)。

ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';
