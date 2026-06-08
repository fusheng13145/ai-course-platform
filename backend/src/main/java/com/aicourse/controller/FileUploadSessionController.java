package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.FileUploadSession;
import com.aicourse.service.FileUploadSessionService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 文件分片上传会话控制器
 * 提供上传会话的创建、分片上传记录、合并、进度查询及取消等API
 */
@RestController
@RequestMapping("/upload-sessions")
public class FileUploadSessionController {

    private final FileUploadSessionService fileUploadSessionService;

    public FileUploadSessionController(FileUploadSessionService fileUploadSessionService) {
        this.fileUploadSessionService = fileUploadSessionService;
    }

    /**
     * 创建上传会话
     * @param params 请求参数（包含 filename, fileSize, totalChunks）
     * @return 创建的上传会话
     */
    @PostMapping("")
    public ApiResponse<FileUploadSession> createUploadSession(
            @RequestBody Map<String, Object> params,
            org.springframework.security.core.Authentication auth) {
        String filename = (String) params.get("filename");
        Long fileSize = params.get("fileSize") != null ? Long.valueOf(params.get("fileSize").toString()) : null;
        Long totalChunks = params.get("totalChunks") != null ? Long.valueOf(params.get("totalChunks").toString()) : null;
        Long uploaderId = (Long) auth.getPrincipal();

        if (filename == null || filename.isEmpty()) {
            return ApiResponse.error(400, "文件名不能为空");
        }
        if (fileSize == null || fileSize <= 0) {
            return ApiResponse.error(400, "文件大小必须大于0");
        }
        if (totalChunks == null || totalChunks <= 0) {
            return ApiResponse.error(400, "总分片数必须大于0");
        }

        FileUploadSession session = fileUploadSessionService.createUploadSession(filename, fileSize, totalChunks, uploaderId);
        return ApiResponse.success("会话创建成功", session);
    }

    /**
     * 上传单个分片
     * @param id 会话ID
     * @param index 分片索引
     * @return 更新后的会话
     */
    @PostMapping("/{id}/chunks/{index}")
    public ApiResponse<FileUploadSession> uploadChunk(@PathVariable Long id, @PathVariable Integer index) {
        FileUploadSession session = fileUploadSessionService.uploadChunk(id, index);
        return ApiResponse.success("分片上传成功", session);
    }

    /**
     * 合并所有分片
     * @param id 会话ID
     * @return 更新后的会话
     */
    @PostMapping("/{id}/merge")
    public ApiResponse<FileUploadSession> mergeChunks(@PathVariable Long id) {
        FileUploadSession session = fileUploadSessionService.mergeChunks(id);
        return ApiResponse.success("合并成功", session);
    }

    /**
     * 获取上传进度
     * @param id 会话ID
     * @return 进度信息（uploaded/total/percentage）
     */
    @GetMapping("/{id}/progress")
    public ApiResponse<Map<String, Object>> getUploadProgress(@PathVariable Long id) {
        Map<String, Object> progress = fileUploadSessionService.getUploadProgress(id);
        return ApiResponse.success(progress);
    }

    /**
     * 取消上传会话
     * @param id 会话ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}/cancel")
    public ApiResponse<Void> cancelUpload(@PathVariable Long id) {
        fileUploadSessionService.cancelUpload(id);
        return ApiResponse.success("取消成功");
    }
}
