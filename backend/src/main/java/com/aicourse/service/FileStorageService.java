package com.aicourse.service;

import com.aicourse.common.StorageLayout;

import java.io.InputStream;

/**
 * 文件存储服务统一接口
 *
 * 提供 upload / download / delete 三个核心操作，允许在不同存储后端之间透明切换：
 * - LocalFileStorageServiceImpl：本地磁盘（当前默认实现）
 * - MinioFileStorageServiceImpl：MinIO 对象存储（需 minio.enabled=true 激活）
 */
public interface FileStorageService {

    /**
     * 上传文件
     *
     * @param objectName 对象名/文件路径（MinIO 为 object key，本地为相对 upload-dir 的路径）
     * @param inputStream 文件输入流
     * @param contentType 文件 MIME 类型（如 application/pdf）
     * @param size 文件大小（字节），用于 MinIO 分片上传优化
     * @return 成功则返回存储路径或 object name；失败抛异常
     */
    String upload(String objectName, InputStream inputStream, String contentType, long size);

    /**
     * 下载文件
     *
     * @param objectName 对象名/文件路径
     * @return 文件输入流（调用方负责关闭）
     */
    InputStream download(String objectName);

    /**
     * 删除文件
     *
     * @param objectName 对象名/文件路径
     * @return true 删除成功；false 文件不存在或删除失败
     */
    boolean delete(String objectName);

    /**
     * 按落库 URL 下载（默认实现把 {@code /uploads/<key>} 反解成 key 后委托 download）。
     *
     * @param storedUrl 落库 URL，如 {@code /uploads/resources/1/a.pdf}
     * @return 文件输入流；URL 非法或文件不存在返回 null
     */
    default InputStream downloadByUrl(String storedUrl) {
        String key = StorageLayout.objectKey(storedUrl);
        if (key == null) {
            return null;
        }
        return download(key);
    }

    /**
     * 按落库 URL 删除（默认实现把 {@code /uploads/<key>} 反解成 key 后委托 delete）。
     *
     * @param storedUrl 落库 URL
     * @return true 真实删除成功
     */
    default boolean deleteByUrl(String storedUrl) {
        String key = StorageLayout.objectKey(storedUrl);
        if (key == null) {
            return false;
        }
        return delete(key);
    }
}
