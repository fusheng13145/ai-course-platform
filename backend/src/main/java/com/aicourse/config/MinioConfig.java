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
 * 默认禁用（minio.enabled=false），启用时需在 application.yaml 中设置 minio.enabled=true
 * 并提供正确的 endpoint / access-key / secret-key。
 * Bean 创建后自动检查 bucket 是否存在，不存在则自动创建。
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "minio")
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
@Data
public class MinioConfig {

    private String endpoint = "http://localhost:9000";
    // 环境变量优先（MINIO_ACCESS_KEY / MINIO_SECRET_KEY），未设置时回退到本地开发默认值 minioadmin
    private String accessKey = System.getenv().getOrDefault("MINIO_ACCESS_KEY", "minioadmin");
    private String secretKey = System.getenv().getOrDefault("MINIO_SECRET_KEY", "minioadmin");
    private String bucket = "aicourse-uploads";

    @Bean
    public MinioClient minioClient() {
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
}
