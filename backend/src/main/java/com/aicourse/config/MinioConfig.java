package com.aicourse.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储配置
 *
 * 默认启用（minio.enabled=true）；测试 profile 显式关闭后回退本地实现。
 * 启用时必须提供 endpoint / access-key / secret-key，凭据为空则在启动期 fail-fast。
 * Bean 创建后自动检查 bucket 是否存在，不存在则自动创建。
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "minio")
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
@Data
public class MinioConfig {

    private String endpoint = "http://localhost:9000";
    // 凭据由 application.yaml 注入：MINIO_ACCESS_KEY / MINIO_SECRET_KEY 优先，
    // 未设置时回退 MINIO_ROOT_USER / MINIO_ROOT_PASSWORD（与 Compose 容器同源，避免两套凭据漂移）。
    // 两者都为空时在下面的 Bean 创建处 fail-fast，不再静默落到本地实现或使用 minioadmin 弱口令。
    private String accessKey;
    private String secretKey;
    private String bucket = "aicourse-uploads";

    @Bean
    public MinioClient minioClient() {
        if (isBlank(accessKey) || isBlank(secretKey)) {
            throw new IllegalStateException(
                    "MinIO 已启用但凭据为空：请设置 MINIO_ROOT_USER / MINIO_ROOT_PASSWORD"
                            + "（或 MINIO_ACCESS_KEY / MINIO_SECRET_KEY）");
        }
        log.info("初始化 MinIO 客户端: endpoint={}, bucket={}", endpoint, bucket);
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        // 自动检查并创建 bucket
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket 自动创建成功: {}", bucket);
            } else {
                log.info("MinIO bucket 已存在: {}", bucket);
            }
        } catch (Exception e) {
            log.error("MinIO bucket 检查/创建失败: {} - {}", bucket, e.getMessage());
            throw new RuntimeException("MinIO bucket 初始化失败", e);
        }

        return client;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
