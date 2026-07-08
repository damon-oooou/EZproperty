CREATE TABLE report_details (
    inspection_id              BIGINT PRIMARY KEY REFERENCES inspections(id) ON DELETE CASCADE,
    landlord_name              VARCHAR(255),
    tenant_name                VARCHAR(255),
    lease_expiry               DATE,
    smoke_alarms_present       BOOLEAN,
    smoke_alarms_location      TEXT,
    tenant_repairs_carried_out BOOLEAN,
    urgent_action              TEXT,
    general_comments           TEXT,
    tenant_action_required     TEXT,
    agent_name                 VARCHAR(255),
    agent_trading_as           VARCHAR(255),
    disclaimer                 TEXT,
    updated_at                 TIMESTAMP NOT NULL DEFAULT now()
);