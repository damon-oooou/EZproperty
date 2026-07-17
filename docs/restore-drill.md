# 数据库恢复演练（v0.6 阶段 D）

> 未验证过的备份视为不存在。本文档是从 R2 备份桶恢复生产库到本地 Docker PG 的完整步骤。
> 首次演练必做；之后建议每季度或 schema 大改后重做一次，并在文末登记。

## 前提

- 本机装有 Docker Desktop 与 AWS CLI（`winget install Amazon.AWSCLI`）
- R2 备份桶的只读凭证（可复用备份 token）

## 步骤（PowerShell）

### 1. 从 R2 拉最新备份

```powershell
$env:AWS_ACCESS_KEY_ID     = "<R2_BACKUP_ACCESS_KEY>"
$env:AWS_SECRET_ACCESS_KEY = "<R2_BACKUP_SECRET_KEY>"
$env:AWS_DEFAULT_REGION    = "auto"
$EP = "https://<account_id>.r2.cloudflarestorage.com"

# 列出可用备份
aws s3 ls s3://ezproperty-backups/daily/ --endpoint-url $EP

# 下载(替换日期)
aws s3 cp s3://ezproperty-backups/daily/ezproperty-<YYYY-MM-DD>.dump .\restore-test.dump --endpoint-url $EP
```

### 2. 起一个干净的临时 PG（不碰 dev 库）

```powershell
docker run -d --name pg-restore-drill -e POSTGRES_PASSWORD=drill -p 5544:5432 postgres:18
```

### 3. 恢复

```powershell
docker cp .\restore-test.dump pg-restore-drill:/tmp/
docker exec pg-restore-drill createdb -U postgres restored
docker exec pg-restore-drill pg_restore -U postgres -d restored --no-owner /tmp/restore-test.dump
```

### 4. 验证完整性

```powershell
# 表结构:应看到 agencies/users/properties/rooms/photos/inspections/
#         inspection_photos/room_conditions/report_details/flyway_schema_history
docker exec pg-restore-drill psql -U postgres -d restored -c "\dt"

# Flyway 版本应到 V11(或当前最新)
docker exec pg-restore-drill psql -U postgres -d restored -c `
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;"

# 行数抽查(与生产侧对比数量级)
docker exec pg-restore-drill psql -U postgres -d restored -c `
  "SELECT (SELECT count(*) FROM users) users, (SELECT count(*) FROM properties) properties, (SELECT count(*) FROM photos) photos, (SELECT count(*) FROM inspections) inspections;"

# 抽一行看数据可读
docker exec pg-restore-drill psql -U postgres -d restored -c "SELECT id, email FROM users LIMIT 3;"
```

### 5. 清理

```powershell
docker rm -f pg-restore-drill
Remove-Item .\restore-test.dump
```

## 演练记录

| 日期 | 备份文件 | 结果 | 执行人 | 备注 |
|---|---|---|---|---|
| 2026-07-14 | daily/ezproperty-2026-07-14.dump | ✅ 通过 | Damon | 首次演练。10 表齐全,Flyway v11,users=2/properties=1/photos=4,数据可读。本地 Docker postgres:18 恢复 |
