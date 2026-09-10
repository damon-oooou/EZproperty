-- v0.8:照片备注、照片更新链、join 行的沿用标记。
--
-- 核心不变量:照片一旦进入系统就不会被修改或删除。
-- "更新一个位置的照片" = 新增一张照片并记录它替换了哪一张(replaces_photo_id),
-- 旧照片继续留在它出现过的所有历史 inspection 里。

ALTER TABLE photos
  ADD COLUMN note VARCHAR(500),
  ADD COLUMN replaces_photo_id BIGINT;

-- 被替换的旧照片不可被物理删除(链的前驱必须一直存在)
ALTER TABLE photos
  ADD CONSTRAINT fk_photos_replaces
    FOREIGN KEY (replaces_photo_id) REFERENCES photos(id)
    ON DELETE RESTRICT;

-- UNIQUE 而非普通索引:一张旧照片只能被一张新照片替换。
-- 没有这个约束链会分叉,History 会渲染出互相矛盾的路径。
-- (PostgreSQL 的 UNIQUE 约束自带索引,反向查 "谁替换了我" 直接走它。)
ALTER TABLE photos
  ADD CONSTRAINT uq_photos_replaces UNIQUE (replaces_photo_id);

-- carried_forward:该引用是从上一次 inspection 沿用的(TRUE)还是本次新增的(FALSE)。
-- confirmed_at / confirmed_by:PM 在现场确认过沿用照片仍然准确。v0.8 只建字段不写入——
-- web 流程里没有这个用户动作,写入创建时间会是假数据(会出现在报告上作为证据)。
ALTER TABLE inspection_photos
  ADD COLUMN carried_forward BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN confirmed_at TIMESTAMP,
  ADD COLUMN confirmed_by BIGINT;

ALTER TABLE inspection_photos
  ADD CONSTRAINT fk_inspection_photos_confirmed_by
    FOREIGN KEY (confirmed_by) REFERENCES users(id)
    ON DELETE SET NULL;

-- 回填既有数据。v0.8 之前照片出现在多个 inspection 的唯一途径就是继承(复制 join 行),
-- 所以每张照片 id 最小的 join 行是原始上传,其余全部是沿用。
-- 不回填的话,现有 inspection 里所有继承来的照片会被 History 归为"本次新拍",PDF 也永远不标沿用。
UPDATE inspection_photos
SET carried_forward = TRUE
WHERE id NOT IN (
    SELECT MIN(id) FROM inspection_photos GROUP BY photo_id
);
