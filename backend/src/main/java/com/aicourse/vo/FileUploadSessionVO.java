package com.aicourse.vo;

import com.aicourse.entity.FileUploadSession;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分片上传会话出参。
 *
 * <p>{@link #forUploader} 会把 `uploadPath` 置空：该字段是服务端磁盘落盘路径，
 * 暴露的是部署目录结构，与上传者本人无关。键仍保留（值为 null），前端契约不变。
 * 管理员监控端点 {@code GET /file-upload-sessions} 用 {@link #from} 保留原值以便排障。
 */
@Data
public class FileUploadSessionVO {

    private Long id;
    private String filename;
    private Long fileSize;
    private Integer totalChunks;
    private Integer uploadedChunks;
    private String uploadPath;
    private String status;
    private Long uploaderId;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 管理侧视图：全字段。 */
    public static FileUploadSessionVO from(FileUploadSession session) {
        FileUploadSessionVO vo = build(session);
        if (vo != null) {
            vo.setUploadPath(session.getUploadPath());
        }
        return vo;
    }

    /** 上传者视图：隐去服务端落盘路径。 */
    public static FileUploadSessionVO forUploader(FileUploadSession session) {
        FileUploadSessionVO vo = build(session);
        if (vo != null) {
            vo.setUploadPath(null);
        }
        return vo;
    }

    public static List<FileUploadSessionVO> from(List<FileUploadSession> sessions) {
        return sessions == null ? List.of() : sessions.stream().map(FileUploadSessionVO::from).toList();
    }

    private static FileUploadSessionVO build(FileUploadSession session) {
        if (session == null) {
            return null;
        }
        FileUploadSessionVO vo = new FileUploadSessionVO();
        vo.setId(session.getId());
        vo.setFilename(session.getFilename());
        vo.setFileSize(session.getFileSize());
        vo.setTotalChunks(session.getTotalChunks());
        vo.setUploadedChunks(session.getUploadedChunks());
        vo.setStatus(session.getStatus());
        vo.setUploaderId(session.getUploaderId());
        vo.setExpiresAt(session.getExpiresAt());
        vo.setCreatedAt(session.getCreatedAt());
        vo.setUpdatedAt(session.getUpdatedAt());
        return vo;
    }
}
