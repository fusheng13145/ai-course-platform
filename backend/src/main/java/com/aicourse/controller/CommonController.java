package com.aicourse.controller;

import jakarta.validation.Valid;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.service.AuthService;
import com.aicourse.service.FileStorageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 公共通用接口控制器。
 * <p>文件上传通过 {@link FileStorageService} 抽象，支持本地磁盘 / MinIO 透明切换。</p>
 */
@Slf4j
@RestController
@RequestMapping("/common")
@Tag(name = "通用服务")
public class CommonController {

    /** 允许上传的文件扩展名白名单（小写），杜绝 .html/.svg/.js 等存储型 XSS / Webshell 风险。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".txt", ".csv", ".md", ".zip", ".rar", ".7z",
            ".mp3", ".mp4", ".avi", ".mov"
    );

    private static boolean isAllowedExtension(String ext) {
        return ext != null && ALLOWED_EXTENSIONS.contains(ext.toLowerCase());
    }

    /** 从原始文件名中提取扩展名，不合法返回空字符串。 */
    private static String extractSafeExt(String originalName) {
        if (originalName == null || !originalName.contains(".")) return "";
        String ext = originalName.substring(originalName.lastIndexOf("."));
        return isAllowedExtension(ext) ? ext : "";
    }

    private final AuthService authService;
    private final FileStorageService fileStorageService;

    public CommonController(AuthService authService, FileStorageService fileStorageService) {
        this.authService = authService;
        this.fileStorageService = fileStorageService;
    }

    /**
     * 发送验证码
     */
    @PostMapping("/verify-code")
    public ApiResponse<Void> sendVerifyCode(@Valid @RequestBody Map<String, String> body,
                                            HttpServletRequest request) {
        String email = body.get("email");
        String type = body.getOrDefault("type", "FORGOT_PASSWORD");
        if (email == null || email.isEmpty()) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "邮箱不能为空");
        }
        return authService.sendVerificationCode(email, type, request);
    }

    /**
     * 单文件上传（委托 FileStorageService，支持本地/MinIO 透明切换）。
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/upload")
    public ApiResponse<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "上传文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        String safeName = originalName != null ? originalName : "未命名文件";
        String ext = extractSafeExt(originalName);
        if (ext.isEmpty()) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "不支持的文件类型: " +
                    (originalName != null && originalName.contains(".")
                            ? originalName.substring(originalName.lastIndexOf(".")) : "无后缀"));
        }

        String objectName = UUID.randomUUID() + ext;
        try {
            String stored = fileStorageService.upload(objectName,
                    file.getInputStream(), file.getContentType(), file.getSize());
            log.info("文件上传成功: {} (original={}, size={})", stored, safeName, file.getSize());
            return ApiResponse.success(Map.of(
                    "url", "/uploads/" + stored,
                    "fileName", safeName,
                    "size", String.valueOf(file.getSize())
            ));
        } catch (Exception e) {
            log.error("文件上传失败: {}", objectName, e);
            return ApiResponse.error(ApiCode.INTERNAL_SERVER_ERROR, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 批量文件上传。
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/upload/batch")
    public ApiResponse<List<Map<String, String>>> uploadBatch(@RequestParam("files") List<MultipartFile> files) {
        List<Map<String, String>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            String originalName = file.getOriginalFilename();
            String safeName = originalName != null ? originalName : "未命名文件";
            String ext = extractSafeExt(originalName);
            if (ext.isEmpty()) {
                results.add(Map.of("url", "", "fileName", safeName, "size", "0",
                        "error", "不支持的文件类型"));
                continue;
            }

            String objectName = UUID.randomUUID() + ext;
            try {
                String stored = fileStorageService.upload(objectName,
                        file.getInputStream(), file.getContentType(), file.getSize());
                results.add(Map.of("url", "/uploads/" + stored,
                        "fileName", safeName, "size", String.valueOf(file.getSize())));
            } catch (Exception e) {
                log.error("批量上传失败: {}", objectName, e);
                results.add(Map.of("url", "", "fileName", safeName, "size", "0",
                        "error", "上传失败: " + e.getMessage()));
            }
        }
        return ApiResponse.success(results);
    }

    /**
     * 获取功能路线图。
     */
    @GetMapping("/feature-roadmap")
    public ApiResponse<Map<String, Object>> featureRoadmap() {
        List<Map<String, String>> features = List.of(
                Map.of("name", "课程管理", "status", "done"),
                Map.of("name", "班级管理", "status", "done"),
                Map.of("name", "用户管理", "status", "done"),
                Map.of("name", "AI课程", "status", "partial"),
                Map.of("name", "AI助手", "status", "partial")
        );
        return ApiResponse.success(Map.of(
                "version", "1.0.0",
                "features", features
        ));
    }
}
