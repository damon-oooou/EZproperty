CREATE TABLE rooms (
    id           BIGSERIAL PRIMARY KEY,
    property_id  BIGINT       NOT NULL REFERENCES properties(id),
    name         VARCHAR(100) NOT NULL,
    position     INT          NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);