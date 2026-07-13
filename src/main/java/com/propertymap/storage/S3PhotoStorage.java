package com.propertymap.storage;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * v0.6 阶段 B:Cloudflare R2 实现(prod 用)。
 * AWS SDK for Java v2,endpoint override 指向 R2 的 S3 兼容 API,region=auto。
 * 桶全私有,访问一律走 presigned GET URL(TTL 由调用方给,拍板值 24h)。
 *
 * 配置(全部来自环境变量,见 application.yml 的 storage.r2.*):
 *   R2_ENDPOINT = https://<account_id>.r2.cloudflarestorage.com
 *   R2_ACCESS_KEY / R2_SECRET_KEY = R2 API Token 的 S3 凭证
 *   R2_BUCKET = ezproperty-prod
 */
@Service
@Profile("prod")
@Slf4j
public class S3PhotoStorage implements PhotoStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public S3PhotoStorage(@Value("${storage.r2.endpoint}") String endpoint,
                          @Value("${storage.r2.access-key}") String accessKey,
                          @Value("${storage.r2.secret-key}") String secretKey,
                          @Value("${storage.r2.bucket}") String bucket) {
        this.bucket = bucket;
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));
        // R2 对 path-style 访问兼容性最好,统一开启
        S3Configuration pathStyle = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
    }

    @Override
    public void save(String key, byte[] bytes, String contentType) throws IOException {
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(bytes));
        } catch (SdkException e) {
            throw new IOException("R2 putObject failed for key " + key, e);
        }
    }

    @Override
    public byte[] load(String key) throws IOException {
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket).key(key).build()).asByteArray();
        } catch (SdkException e) {
            throw new IOException("R2 getObject failed for key " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        List<ObjectIdentifier> ids = List.of(
                ObjectIdentifier.builder().key(key).build(),
                ObjectIdentifier.builder().key(PhotoKeys.medium(key)).build(),
                ObjectIdentifier.builder().key(PhotoKeys.thumbnail(key)).build());
        try {
            s3.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(ids).build())
                    .build());
        } catch (SdkException e) {
            log.warn("R2 deleteObjects failed for key {}: {}", key, e.getMessage());
        }
    }

    @Override
    public String presignedGetUrl(String key, Duration ttl) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        return presigner.presignGetObject(request).url().toString();
    }

    @PreDestroy
    void close() {
        s3.close();
        presigner.close();
    }
}
