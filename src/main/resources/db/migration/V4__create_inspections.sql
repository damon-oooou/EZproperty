CREATE TABLE inspections (
    id              BIGSERIAL PRIMARY KEY,
    property_id     BIGINT NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    type            VARCHAR(20) NOT NULL,
    inspection_date DATE NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_inspections_property_id ON inspections(property_id);