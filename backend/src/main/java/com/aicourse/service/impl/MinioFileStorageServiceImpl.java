package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.exception.BusinessException;


import com.aicourse.common.StorageLayout;
import com.aicourse.service.FileStorageService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MinIO 对象存储文件存储实现
 *
 * <p>默认启用（minio.enabled=true，未配置时也按启用处理）；启用后取代本地实现，
 * 文件上传/删除均通过 MinIO SDK 进行。历史本地文件只读兼容：MinIO 未命中时按
 * {@code /uploads/<key>} 回退到旧本地存储根，避免切换后旧 URL 立即失效。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class MinioFileStorageServiceImpl implements FileStorageService {


    private final MinioClient minioClient;

    @Value("${minio.bucket:aicourse-uploads}")
    private String bucket;

    /** 历史本地根：仅用于 MinIO 未命中时的只读兼容，不参与新上传。 */
    @Value("${file.upload-dir:}")
    private String uploadDir;

    public MinioFileStorageServiceImpl(MinioClient minioClient) {
        this.minioClient = minioClient;
    }


    /** 上传对象存储文件存储 */
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
            throw new BusinessException(ApiCode.INTERNAL_SERVER_ERROR, "MinIO 文件上传失败", e);
        }
    }


    /** 下载对象存储文件存储 */
    @Override
    public InputStream download(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 文件下载失败: {} - {}", objectName, e.getMessage());
            throw new BusinessException(ApiCode.INTERNAL_SERVER_ERROR, "MinIO 文件下载失败", e);
        }
    }


    /** 删除对象存储文件存储 */
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

    /**
     * 按落库 URL 读取：优先 MinIO；对象不存在时回退历史本地文件（只读）。
     * 新上传不会写本地，因此回退只解决切换前的存量 URL。
     */
    @Override
    public InputStream downloadByUrl(String storedUrl) {
        String key = StorageLayout.objectKey(storedUrl);
        if (key == null) {
            return null;
        }
        try {
            return download(key);
        } catch (RuntimeException minioFailure) {
            Path fallback = StorageLayout.resolveUrl(uploadDir, storedUrl);
            if (fallback != null && Files.isRegularFile(fallback)) {
                try {
                    log.warn("MinIO 未命中，回退历史本地文件: {}", key);
                    return Files.newInputStream(fallback);
                } catch (IOException e) {
                    throw new BusinessException(ApiCode.INTERNAL_SERVER_ERROR, "历史本地文件读取失败: " + key, e);
                }
            }
            log.warn("MinIO 对象不存在，且无历史本地文件: {}", key);
            return null;
        }
    }

    /** 删除时同时清理 MinIO 对象与历史本地文件，任一成功即视为已清理。 */
    @Override
    public boolean deleteByUrl(String storedUrl) {
        String key = StorageLayout.objectKey(storedUrl);
        if (key == null) {
            return false;
        }
        boolean minioDeleted = delete(key);
        boolean localDeleted = StorageLayout.deleteByUrl(uploadDir, storedUrl);
        return minioDeleted || localDeleted;
    }
}
