-- v0.5:房源归属 agency。
-- 决策:上线认证前的存量数据均为测试数据,直接清空重来,
-- 这样 agency_id 可以一步到位设为 NOT NULL,不需要默认账号迁移。
-- TRUNCATE ... CASCADE 会级联清空 rooms/photos/inspections/
-- inspection_photos/room_conditions/report_details。

TRUNCATE TABLE properties CASCADE;

ALTER TABLE properties
    ADD COLUMN agency_id BIGINT NOT NULL REFERENCES agencies(id);

CREATE INDEX idx_properties_agency ON properties(agency_id);
