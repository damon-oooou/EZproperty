CREATE TABLE inspection_photos (
    id            BIGSERIAL PRIMARY KEY,
    inspection_id BIGINT NOT NULL REFERENCES inspections(id) ON DELETE CASCADE,
    photo_id      BIGINT NOT NULL REFERENCES photos(id),
    added_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_inspection_photo UNIQUE (inspection_id, photo_id)
);

CREATE INDEX idx_inspection_photos_inspection_id ON inspection_photos(inspection_id);