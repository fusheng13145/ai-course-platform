package com.aicourse.service.impl;


import com.aicourse.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地磁盘文件存储实现
 *
 * 将文件写入 file.upload-dir 对应的本地目录（默认为 uploads/）。
 * 这是当前默认的文件存储后端，不依赖任何外部服务。
 */
@Slf4j
@Service
public class LocalFileStorageServiceImpl implements FileStorageService {


    @Value("${file.upload-dir:uploads/}")
    private String uploadDir;


    /** 上传本地文件存储 */
    @Override
    public String upload(String objectName, InputStream inputStream, String contentType, long size) {
        Path targetPath = Paths.get(uploadDir, objectName);
        try {
            // 确保父目录存在（支持多级子目录如 uploads/2025/01/xxx.pdf）
            Files.createDirectories(targetPath.getParent());
            try (FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            log.info("本地文件上传成功: {}", targetPath);
            return objectName;
        } catch (IOException e) {
            log.error("本地文件上传失败: {} - {}", objectName, e.getMessage());
            throw new RuntimeException("文件上传失败", e);
        }
    }


    /** 下载本地文件存储 */
    @Override
    public InputStream download(String objectName) {
        File file = new File(uploadDir, objectName);
        if (!file.exists() || !file.isFile()) {
            log.warn("本地文件不存在: {}", file.getAbsolutePath());
            return null;
        }
        try {
            return new FileInputStream(file);
        } catch (IOException e) {
            log.error("本地文件读取失败: {} - {}", objectName, e.getMessage());
            throw new RuntimeException("文件读取失败", e);
        }
    }


    /** 删除本地文件存储 */
    @Override
    public boolean delete(String objectName) {
        File file = new File(uploadDir, objectName);
        if (!file.exists()) {
            log.warn("删除失败，文件不存在: {}", file.getAbsolutePath());
            return false;
        }
        boolean deleted = file.delete();
        if (deleted) {
            log.info("本地文件删除成功: {}", objectName);
        } else {
            log.error("本地文件删除失败: {}", objectName);
        }
        return deleted;
    }
}
