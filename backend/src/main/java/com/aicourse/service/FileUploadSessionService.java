package com.aicourse.service;

import com.aicourse.entity.FileUploadSession;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

/**
 * 文件上传会话服务接口
 * 提供分片上传会话的创建、分片上传记录、合并、进度查询、取消及过期清理等能力
 */
public interface FileUploadSessionService extends IService<FileUploadSession> {

    /**
     * 创建上传会话
     * 生成上传路径，设置24小时过期时间，初始化已上传分片数为0
     * @param filename 原始文件名
     * @param fileSize 文件总大小（字节）
     * @param totalChunks 总分片数
     * @param uploaderId 上传人ID
     * @return 创建的上传会话
     */
    FileUploadSession createUploadSession(String filename, Long fileSize, Long totalChunks, Long uploaderId);

    /**
     * 记录单个分片上传完成
     * 将分片索引追加到已上传分片中，更新已上传数量
     * @param sessionId 会话ID
     * @param chunkIndex 分片索引
     * @param uploaderId 当前登录用户ID（归属校验，修复 IDOR 越权）
     * @return 更新后的会话
     */
    FileUploadSession uploadChunk(Long sessionId, Integer chunkIndex, Long uploaderId);

    /**
     * 合并所有分片
     * 检查已上传分片数是否等于总分片数，满足则标记为已完成
     * @param sessionId 会话ID
     * @param uploaderId 当前登录用户ID（归属校验，修复 IDOR 越权）
     * @return 更新后的会话
     */
    FileUploadSession mergeChunks(Long sessionId, Long uploaderId);

    /**
     * 获取上传进度信息
     * @param sessionId 会话ID
     * @param uploaderId 当前登录用户ID（归属校验，修复 IDOR 越权）
     * @return 进度信息（包含 uploaded/total/percentage）
     */
    Map<String, Object> getUploadProgress(Long sessionId, Long uploaderId);

    /**
     * 取消上传会话
     * @param sessionId 会话ID
     * @param uploaderId 当前登录用户ID（归属校验，修复 IDOR 越权）
     */
    void cancelUpload(Long sessionId, Long uploaderId);

    /**
     * 清理过期会话
     * 删除 expiresAt < now 的记录
     * @return 清理的数量
     */
    int cleanExpiredSessions();
}
