package com.propertymap.storage;

import java.io.IOException;
import java.time.Duration;

/**
 * v0.6 阶段 B:照片存储抽象。
 * dev profile -> LocalPhotoStorage(本地文件系统,不签名);
 * prod profile -> S3PhotoStorage(Cloudflare R2 私有桶 + presigned GET)。
 *
 * key 语义见 {@link PhotoKeys}:调用方只持有主 key,变体 key 按约定派生。
 */
public interface PhotoStorage {

    /** 写入一个对象(管线产物,contentType 恒为 image/jpeg)。 */
    void save(String key, byte[] bytes, String contentType) throws IOException;

    /** 读取一个对象的完整字节(PDF 生成用中间档 key)。 */
    byte[] load(String key) throws IOException;

    /**
     * 删除主 key 及其全部变体(_m/_t)。变体派生逻辑收敛在实现层,调用方只传主 key。
     * 尽力而为:单个对象删除失败记日志,不抛出(用于上传回滚清理与将来的 library 删除)。
     */
    void delete(String key);

    /**
     * 该 key 的访问 URL。
     * Local 实现返回 /uploads/{key}(dev 不签名,保持开发简单);
     * S3 实现返回 R2 presigned GET URL(本地 HMAC 运算,无网络调用,批量签无性能问题)。
     */
    String presignedGetUrl(String key, Duration ttl);
}
