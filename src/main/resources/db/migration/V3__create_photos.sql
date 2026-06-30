CREATE TABLE photos (
    id          BIGSERIAL PRIMARY KEY,
    room_id     BIGINT        NOT NULL REFERENCES rooms(id),
    file_name   VARCHAR(500)  NOT NULL,
    file_path   VARCHAR(1000) NOT NULL,
    file_size   BIGINT,
    uploaded_at TIMESTAMP     NOT NULL DEFAULT NOW()
);