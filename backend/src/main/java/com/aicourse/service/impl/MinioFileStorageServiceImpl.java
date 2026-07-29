package com.aicourse.service.impl;

import com.aicourse.service.FileStorageService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * MinIO 对象存储文件存储实现
 *
 * 仅在 minio.enabled=true 时激活（通过 @ConditionalOnProperty）。
 * 启用后会取代默认的 LocalFileStorageServiceImpl，文件上传/下载/删除均通过 MinIO SDK 进行。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class MinioFileStorageServiceImpl implements FileStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket:aicourse-uploads}")
    private String bucket;

    public MinioFileStorageServiceImpl(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public String upload(String objectName, InputStream inputStream, String contentType, long size) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());
            log.info("MinIO 文件上传成功: {}", objectName);
            return objectName;
        } catch (Exception e) {
            log.error("MinIO 文件上传失败: {} - {}", objectName, e.getMessage());
            throw new RuntimeException("MinIO 文件上传失败", e);
        }
    }

    @Override
    public InputStream download(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 文件下载失败: {} - {}", objectName, e.getMessage());
            throw new RuntimeException("MinIO 文件下载失败", e);
        }
    }

    @Override
    public boolean delete(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            log.info("MinIO 文件删除成功: {}", objectName);
            return true;
        } catch (Exception e) {
            log.error("MinIO 文件删除失败: {} - {}", objectName, e.getMessage());
            return false;
        }
    }
}
