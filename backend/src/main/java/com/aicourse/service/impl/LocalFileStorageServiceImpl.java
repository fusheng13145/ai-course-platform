package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.exception.BusinessException;


import com.aicourse.common.StorageLayout;
import com.aicourse.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘文件存储实现
 *
 * <p>写入 {@code file.upload-dir}；该值默认留空，由 {@link StorageLayout} 解析到
 * 仓库外的 {@code ~/.aicourse-platform/storage}。仓库内不保留 {@code uploads/} 目录，
 * 读与删同样只走该根，不再有历史目录回退。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "minio.enabled", havingValue = "false", matchIfMissing = true)
public class LocalFileStorageServiceImpl implements FileStorageService {


    @Value("${file.upload-dir:}")
    private String uploadDir;


    /** 上传本地文件存储 */
    @Override
    public String upload(String objectName, InputStream inputStream, String contentType, long size) {
        Path root = StorageLayout.uploadRoot(uploadDir);
        Path targetPath = StorageLayout.resolveKey(root, objectName);
        if (targetPath == null) {
            throw new BusinessException(ApiCode.INTERNAL_SERVER_ERROR, "非法文件路径: " + objectName);
        }
        try {
            // 支持多级子目录，如 resources/1/xxx.pdf
            Files.createDirectories(targetPath.getParent());
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("本地文件上传成功: {}", targetPath);
            return objectName;
        } catch (IOException e) {
            log.error("本地文件上传失败: {} - {}", objectName, e.getMessage());
            throw new BusinessException(ApiCode.INTERNAL_SERVER_ERROR, "文件上传失败", e);
        }
    }


    /** 下载本地文件存储；只查外部存储根。 */
    @Override
    public InputStream download(String objectName) {
        Path root = StorageLayout.uploadRoot(uploadDir);
        Path path = StorageLayout.resolveKey(root, objectName);
        if (path == null) {
            log.warn("拒绝非法文件路径: {}", objectName);
            return null;
        }
        if (!Files.isRegularFile(path)) {
            log.warn("本地文件不存在: {}", path);
            return null;
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            log.error("本地文件读取失败: {} - {}", objectName, e.getMessage());
            throw new BusinessException(ApiCode.INTERNAL_SERVER_ERROR, "文件读取失败", e);
        }
    }


    /** 删除本地文件存储；只查外部存储根。 */
    @Override
    public boolean delete(String objectName) {
        Path root = StorageLayout.uploadRoot(uploadDir);
        Path path = StorageLayout.resolveKey(root, objectName);
        if (path == null) {
            log.warn("拒绝删除非法文件路径: {}", objectName);
            return false;
        }
        if (!Files.exists(path)) {
            log.warn("删除失败，文件不存在: {}", path);
            return false;
        }
        try {
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                log.info("本地文件删除成功: {}", objectName);
            } else {
                log.error("本地文件删除失败: {}", objectName);
            }
            return deleted;
        } catch (IOException e) {
            log.error("本地文件删除失败: {} - {}", objectName, e.getMessage());
            return false;
        }
    }

    /** 按落库 URL 下载；委托 StorageLayout 解析到外部存储根。 */
    @Override
    public InputStream downloadByUrl(String storedUrl) {
        Path path = StorageLayout.resolveUrl(uploadDir, storedUrl);
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            log.error("本地文件读取失败: {} - {}", storedUrl, e.getMessage());
            throw new BusinessException(ApiCode.INTERNAL_SERVER_ERROR, "文件读取失败", e);
        }
    }

    /** 按落库 URL 删除；委托 StorageLayout 解析到外部存储根。 */
    @Override
    public boolean deleteByUrl(String storedUrl) {
        return StorageLayout.deleteByUrl(uploadDir, storedUrl);
    }
}
