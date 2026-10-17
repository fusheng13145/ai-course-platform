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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
     * <p>分片字节按 0 基索引落盘到 {@code file.chunk-dir} 下的会话目录，
     * 同名索引重传覆盖且不重复推进进度。</p>
     * @param id 会话ID
     * @param index 分片索引（0 基，须小于会话声明的 totalChunks）
     * @param file 分片内容（multipart 字段名 file）
     * @param md5 可选的分片 MD5 摘要，声明后由 Service 层实算比对
     * @return 更新后的会话
     */
    @PostMapping("/{id}/chunks/{index}")
    public ApiResponse<FileUploadSession> uploadChunk(@PathVariable Long id, @PathVariable Integer index,
                                                      @RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "md5", required = false) String md5,
                                                      org.springframework.security.core.Authentication auth) {
        try {
            FileUploadSession session = fileUploadSessionService.uploadChunk(
                    id, index, file.getInputStream(), file.getSize(), md5, resolveOperator(id, auth));
            return ApiResponse.success("分片上传成功", session);
        } catch (IOException e) {
            log.error("分片读取失败: session={}, chunk={}, {}", id, index, e.getMessage());
            throw new BusinessException(500, "分片读取失败", e);
        }
    }

    /**
     * 合并所有分片
     * <p>以磁盘分片为真值：缺片返回 400 并列出缺失索引，绝不产出半成品。</p>
     * @param id 会话ID
     * @return 合并后的会话（status=COMPLETED，uploadPath 为相对上传根的对象名）
     */
    @PostMapping("/{id}/merge")
    public ApiResponse<FileUploadSession> mergeChunks(@PathVariable Long id,
                                                      org.springframework.security.core.Authentication auth) {
        FileUploadSession session = fileUploadSessionService.mergeChunks(id, resolveOperator(id, auth));
        return ApiResponse.success("分片合并成功", session);
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
