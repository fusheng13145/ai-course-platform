package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiResponse;
import com.aicourse.dto.FileUploadDTO;
import com.aicourse.entity.FileUploadSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.FileUploadSessionService;
import com.aicourse.vo.UploadProgressVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件分片上传会话控制器
 * 提供上传会话的创建、分片上传记录、合并、进度查询及取消等API
 */
@Tag(name = "文件上传", description = "分片上传会话管理、进度查询")
@RestController
@RequestMapping("/upload-sessions")
public class FileUploadSessionController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadSessionController.class);

    private final FileUploadSessionService fileUploadSessionService;

    public FileUploadSessionController(FileUploadSessionService fileUploadSessionService) {
        this.fileUploadSessionService = fileUploadSessionService;
    }

    /**
     * 上传会话列表（管理员监控用）
     * 按创建时间倒序返回最近 100 条会话，供管理端「分片上传管理」页展示
     * @return 上传会话列表
     */
    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "上传会话列表（管理员）", description = "按创建时间倒序返回最近 100 条分片上传会话")
    public ApiResponse<List<FileUploadSession>> listSessions() {
        List<FileUploadSession> sessions = fileUploadSessionService.list(
                new LambdaQueryWrapper<FileUploadSession>()
                        .orderByDesc(FileUploadSession::getCreatedAt)
                        .last("LIMIT 100"));
        return ApiResponse.success(sessions);
    }

    /**
     * 创建上传会话
     * @param params 请求参数（包含 filename, fileSize, totalChunks）
     * @return 创建的上传会话
     */
    @PostMapping("")
    public ApiResponse<FileUploadSession> createUploadSession(
            @Valid @RequestBody FileUploadDTO params,
            org.springframework.security.core.Authentication auth) {
        Long uploaderId = (Long) auth.getPrincipal();
        FileUploadSession session = fileUploadSessionService.createUploadSession(
                params.getFilename(), params.getFileSize(), params.getTotalChunks(), uploaderId);
        return ApiResponse.success("会话创建成功", session);
    }

    /**
     * 上传单个分片
     * <p>该端点为历史空壳实现：后端未落地分片字节落盘与合并逻辑，
     * 仅修改会话状态为 COMPLETED，导致管理员「合并」得到虚假成功、磁盘无文件（静默数据丢失）。
     * 已按上线前全检结论（N-03）下线，保留会话元数据管理（create/list/progress/cancel）。</p>
     * @param id 会话ID
     * @param index 分片索引
     * @return 永不成功，返回 501
     */
    @PostMapping("/{id}/chunks/{index}")
    public ApiResponse<FileUploadSession> uploadChunk(@PathVariable Long id, @PathVariable Integer index,
                                                      org.springframework.security.core.Authentication auth) {
        throw new BusinessException(501, "分片上传功能暂未启用");
    }

    /**
     * 合并所有分片
     * <p>与 uploadChunk 同理，合并字节逻辑从未实现，按 N-03 下线。</p>
     * @param id 会话ID
     * @return 永不成功，返回 501
     */
    @PostMapping("/{id}/merge")
    public ApiResponse<FileUploadSession> mergeChunks(@PathVariable Long id,
                                                      org.springframework.security.core.Authentication auth) {
        throw new BusinessException(501, "分片合并功能暂未启用");
    }

    /**
     * 获取上传进度
     * @param id 会话ID
     * @return 进度信息（uploaded/total/percentage）
     */
    @GetMapping("/{id}/progress")
    public ApiResponse<UploadProgressVO> getUploadProgress(@PathVariable Long id,
                                                           org.springframework.security.core.Authentication auth) {
        UploadProgressVO progress = fileUploadSessionService.getUploadProgress(id, resolveOperator(id, auth));
        return ApiResponse.success(progress);
    }

    /**
     * 取消上传会话
     * @param id 会话ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}/cancel")
    public ApiResponse<Void> cancelUpload(@PathVariable Long id,
                                          org.springframework.security.core.Authentication auth) {
        fileUploadSessionService.cancelUpload(id, resolveOperator(id, auth));
        return ApiResponse.success("取消成功");
    }

    /**
     * 解析操作人：普通用户仅能操作自己的会话（Service 层归属校验）；
     * 管理员（ADMIN）在「分片上传管理」页可对任意会话执行合并/取消/查看进度，
     * 通过以会话归属人身份代操作实现，不放宽 Service 层 IDOR 防护。
     */
    private Long resolveOperator(Long sessionId, org.springframework.security.core.Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            return userId;
        }
        FileUploadSession session = fileUploadSessionService.getById(sessionId);
        return session != null && session.getUploaderId() != null ? session.getUploaderId() : userId;
    }
}
