-- v0.6 阶段 A:照片 ingest 规格化管线。
--
-- 已拍板决策(见 v0.6 规格书):上线前所有照片均为测试数据,清库重置,
-- 不写任何回填/兼容逻辑。此迁移直接清空照片引用与照片行,
-- 保证库内不存在任何未规格化照片(本地 uploads 目录由所有者手动删除)。
DELETE FROM inspection_photos;
DELETE FROM photos;

-- EXIF 拍摄时间(DateTimeOriginal),缺失(PNG/截图等)为 NULL。
-- 前端展示规则:非空显示"Taken {日期}",为空回退 uploaded_at 显示"Uploaded {日期}"。
ALTER TABLE photos ADD COLUMN taken_at TIMESTAMP NULL;

-- 存储语义从"完整磁盘路径"改为"storage key"(为阶段 B 的存储抽象铺路,
-- 本地文件系统与 R2 使用同一套 key):
--   主文件(规格化原图) = {uuid}.jpg
--   变体由约定派生:中间档 {uuid}_m.jpg,缩略图 {uuid}_t.jpg
-- 表里只存主 key,不存三个路径列。
ALTER TABLE photos RENAME COLUMN file_path TO storage_key;
ALTER TABLE photos ALTER COLUMN storage_key TYPE VARCHAR(255);
