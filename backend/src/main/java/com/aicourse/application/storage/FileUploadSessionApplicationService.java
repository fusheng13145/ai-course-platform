package com.aicourse.application.storage;

import com.aicourse.entity.FileUploadSession;
import com.aicourse.service.FileUploadSessionService;
import com.aicourse.vo.FileUploadSessionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分片上传会话读口径应用服务（3E-05E）。
 *
 * <p>从 {@code FileUploadSessionController} 下移 1 处 {@code LambdaQueryWrapper}：管理员列表
 * 「按创建时间倒序 + LIMIT 100」。写路径（创建会话/分片/合并/取消）仍由控制器转发到
 * {@code FileUploadSessionService}。</p>
 */
@Service
public class FileUploadSessionApplicationService {

    /** 管理员列表最多回显的条数（原实现硬编码 100）。 */
    private static final int LIST_LIMIT = 100;

    private final FileUploadSessionService fileUploadSessionService;

    public FileUploadSessionApplicationService(FileUploadSessionService fileUploadSessionService) {
        this.fileUploadSessionService = fileUploadSessionService;
    }

    /** 最近的上传会话（创建时间倒序，最多 100 条）。 */
    public List<FileUploadSessionVO> listRecent() {
        List<FileUploadSession> sessions = fileUploadSessionService.list(
                new LambdaQueryWrapper<FileUploadSession>()
                        .orderByDesc(FileUploadSession::getCreatedAt)
                        .last("LIMIT " + LIST_LIMIT));
        return FileUploadSessionVO.from(sessions);
    }
}
