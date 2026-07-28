package com.aicourse.service.impl;

import com.aicourse.entity.FileUploadSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.FileUploadSessionMapper;
import com.aicourse.service.FileUploadSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传会话服务实现类
 * 实现分片上传会话的创建、分片记录、合并、进度查询、取消及过期清理等核心逻辑
 */
@Service
public class FileUploadSessionServiceImpl extends ServiceImpl<FileUploadSessionMapper, FileUploadSession> implements FileUploadSessionService {

    /** 上传会话默认过期时间：24小时 */
    private static final int SESSION_EXPIRE_HOURS = 24;

    /** 允许上传的文件扩展名白名单（小写） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "csv", "md", "json", "xml",
            "png", "jpg", "jpeg", "gif", "svg", "bmp",
            "mp4", "webm", "mov",
            "zip", "rar", "7z"
    );

    /**
     * 上传会话安全清洗文件名，同时校验扩展名白名单。
     * - 移除一切路径分量（防目录穿越）：仅保留最后一个路径分隔符后的纯文件名
     * - 校验扩展名是否在允许白名单内，非法则抛出异常
     */
    private String sanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.isBlank()) {
            throw new BusinessException("文件名为空");
        }
        // 1) 仅取纯文件名部分（阻止 ../../etc/passwd、C:\windows\... 等路径穿越）
        String name = Paths.get(rawFilename).getFileName().toString();
        if (name == null || name.isBlank()) {
            throw new BusinessException("文件名无效");
        }
        // 2) 扩展名白名单校验
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx == -1 || dotIdx == name.length() - 1) {
            throw new BusinessException("文件缺少有效扩展名，允许的类型：" + String.join(", ", ALLOWED_EXTENSIONS));
        }
        String ext = name.substring(dotIdx + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("不允许的文件类型（." + ext + "），允许的类型：" + String.join(", ", ALLOWED_EXTENSIONS));
        }
        return name;
    }

    /**
     * 创建上传会话
     * 生成上传路径（uploads/{year}/{month}/{uuid}_{filename}），设置24小时过期时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadSession createUploadSession(String filename, Long fileSize, Long totalChunks, Long uploaderId) {
        // 安全清洗：防路径穿越 + 扩展名白名单
        String safeFilename = sanitizeFilename(filename);
        // 生成唯一上传路径：uploads/{年}/{月}/{uuid}_{安全文件名}
        LocalDate now = LocalDate.now();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String uploadPath = "uploads/" + now.getYear() + "/" +
                String.format("%02d", now.getMonthValue()) + "/" + uuid + "_" + safeFilename;

        FileUploadSession session = new FileUploadSession();
        session.setFilename(safeFilename);
        session.setFileSize(fileSize);
        session.setTotalChunks(totalChunks.intValue());
        session.setUploadedChunks(0);
        session.setUploadPath(uploadPath);
        session.setStatus("UPLOADING");
        session.setUploaderId(uploaderId);
        session.setExpiresAt(LocalDateTime.now().plusHours(SESSION_EXPIRE_HOURS));
        this.save(session);
        return session;
    }

    /**
     * 记录单个分片上传完成
     * 已上传分片数+1
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadSession uploadChunk(Long sessionId, Integer chunkIndex) {
        FileUploadSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("上传会话不存在");
        }
        if (!"UPLOADING".equals(session.getStatus())) {
            throw new BusinessException("当前会话状态不允许上传分片");
        }
        // 已上传分片数递增
        session.setUploadedChunks(session.getUploadedChunks() + 1);
        this.updateById(session);
        return session;
    }

    /**
     * 合并所有分片
     * 校验已上传分片数是否等于总分片数，满足则标记为已完成
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadSession mergeChunks(Long sessionId) {
        FileUploadSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("上传会话不存在");
        }
        if (!"UPLOADING".equals(session.getStatus())) {
            throw new BusinessException("当前会话状态不允许合并");
        }
        // 检查是否所有分片都已上传完毕
        if (session.getUploadedChunks().equals(session.getTotalChunks())) {
            session.setStatus("COMPLETED");
            this.updateById(session);
        } else {
            throw new BusinessException("分片未全部上传完成，当前已上传：" +
                    session.getUploadedChunks() + "/" + session.getTotalChunks());
        }
        return session;
    }

    /**
     * 获取上传进度信息
     * 返回 uploaded/total/percentage
     */
    @Override
    public Map<String, Object> getUploadProgress(Long sessionId) {
        FileUploadSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("上传会话不存在");
        }
        int uploaded = session.getUploadedChunks() != null ? session.getUploadedChunks() : 0;
        int total = session.getTotalChunks() != null ? session.getTotalChunks() : 0;
        double percentage = total > 0 ? Math.round((double) uploaded / total * 10000) / 100.0 : 0.0;

        Map<String, Object> progress = new HashMap<>();
        progress.put("uploaded", uploaded);
        progress.put("total", total);
        progress.put("percentage", percentage + "%");
        progress.put("status", session.getStatus());
        return progress;
    }

    /**
     * 取消上传会话
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelUpload(Long sessionId) {
        FileUploadSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("上传会话不存在");
        }
        if ("COMPLETED".equals(session.getStatus()) || "CANCELLED".equals(session.getStatus())) {
            throw new BusinessException("该会话已完成或已取消，无法再次操作");
        }
        session.setStatus("CANCELLED");
        this.updateById(session);
    }

    /**
     * 清理过期会话
     * 删除 expiresAt < now 的记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanExpiredSessions() {
        List<FileUploadSession> expiredSessions = this.list(
                new LambdaQueryWrapper<FileUploadSession>()
                        .lt(FileUploadSession::getExpiresAt, LocalDateTime.now())
        );
        if (expiredSessions.isEmpty()) {
            return 0;
        }
        for (FileUploadSession session : expiredSessions) {
            this.removeById(session.getId());
        }
        return expiredSessions.size();
    }
}
