CREATE TABLE room_conditions (
    id            BIGSERIAL PRIMARY KEY,
    inspection_id BIGINT  NOT NULL REFERENCES inspections(id) ON DELETE CASCADE,
    room_id       BIGINT  NOT NULL REFERENCES rooms(id)       ON DELETE RESTRICT,
    satisfactory  BOOLEAN,
    comments      TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_room_conditions_inspection_room UNIQUE (inspection_id, room_id)
);