package com.aicourse.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * 上传文件病毒扫描的唯一入口。
 *
 * <p>实现必须 fail-closed：扫描器不可用、超时或返回未知结果时抛 {@code BusinessException(503)}，
 * 感染时抛 {@code BusinessException(400)}；只有确认干净才正常返回。调用方不得把“跳过扫描”
 * 当作干净结果。</p>
 */
@FunctionalInterface
public interface FileVirusScanService {

    /**
     * 扫描已落盘的临时文件。
     *
     * @param file           待扫描文件
     * @param originalFilename 原始文件名（仅用于日志/提示，不参与命令拼接）
     */
    void scanOrThrow(Path file, String originalFilename);

    /**
     * 扫描 multipart 文件：先复制到系统临时文件，扫描后无论结果都删除临时文件。
     * 默认实现保留原 multipart 输入流可再次读取，供后续存储层使用。
     */
    default void scanOrThrow(MultipartFile file, String originalFilename) {
        throw new UnsupportedOperationException("当前扫描实现不支持 MultipartFile 直扫");
    }
}
