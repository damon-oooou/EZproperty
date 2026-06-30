CREATE TABLE properties (
    id          BIGSERIAL PRIMARY KEY,
    address     VARCHAR(500) NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);