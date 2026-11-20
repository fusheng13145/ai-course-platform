package com.aicourse.controller;

import jakarta.validation.Valid;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.UploadMagic;
import com.aicourse.service.AuthService;
import com.aicourse.vo.FeatureItemVO;
import com.aicourse.vo.FeatureRoadmapVO;
import com.aicourse.vo.UploadResultVO;
import com.aicourse.service.FileStorageService;
import com.aicourse.util.ClientInfoResolver;
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

    /** 通用上传单文件上限，与 ResourceController 的 50MB 口径一致。 */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    /**
     * 扩展名白名单 + 文件头魔数校验；返回错误文案，合法返回 null。
     */
    private String validateFile(MultipartFile file, String ext) {
        if (file.getSize() > MAX_FILE_SIZE) {
            return "文件大小不能超过50MB";
        }
        try {
            String magic = UploadMagic.detect(UploadMagic.readHeader(file));
            if (!UploadMagic.matchesExtension(ext, magic)) {
                return "文件内容与扩展名不符，已拒绝上传";
            }
        } catch (java.io.IOException e) {
            log.error("读取上传文件头失败: {}", e.getMessage(), e);
            return "读取上传文件失败";
        }
        return null;
    }

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
    private final ClientInfoResolver clientInfoResolver;
    private final com.aicourse.service.SemesterService semesterService;
    private final com.aicourse.service.UserService userService;

    public CommonController(AuthService authService, FileStorageService fileStorageService,
                            ClientInfoResolver clientInfoResolver,
                            com.aicourse.service.SemesterService semesterService,
                            com.aicourse.service.UserService userService) {
        this.authService = authService;
        this.fileStorageService = fileStorageService;
        this.clientInfoResolver = clientInfoResolver;
        this.semesterService = semesterService;
        this.userService = userService;
    }

    /**
     * 当前 ACTIVE 学期（10A 展示别名，2026-11-07 全量实现批）。
     * <p>页面页眉（kicker）等展示位用；别名为现场推导、不落库。无在册学期时 data 为 null。</p>
     */
    @org.springframework.web.bind.annotation.GetMapping("/semester/active")
    @io.swagger.v3.oas.annotations.Operation(summary = "当前学期", description = "返回 ACTIVE 学期（含 10A 展示别名）；无则 null")
    public ApiResponse<com.aicourse.vo.SemesterVO> activeSemester() {
        return ApiResponse.success(semesterService.listByStatus("ACTIVE").stream()
                .findFirst()
                .map(com.aicourse.vo.SemesterVO::from)
                .orElse(null));
    }

    /**
     * 学期列表（教师发起开课任务时选择；管理员也可见）。
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @org.springframework.web.bind.annotation.GetMapping("/semesters")
    @io.swagger.v3.oas.annotations.Operation(summary = "学期列表", description = "返回全部学期，按开始日期倒序")
    public ApiResponse<List<com.aicourse.vo.SemesterVO>> semesters() {
        return ApiResponse.success(semesterService.list().stream()
                .sorted(java.util.Comparator.comparing(
                        com.aicourse.entity.Semester::getStartDate,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(com.aicourse.vo.SemesterVO::from)
                .toList());
    }

    /**
     * 教师目录（开课任务指派主讲、教学班指定主讲时使用）。
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @org.springframework.web.bind.annotation.GetMapping("/teachers")
    @io.swagger.v3.oas.annotations.Operation(summary = "教师目录", description = "可按学院筛选；只返回在职教师")
    public ApiResponse<List<Map<String, Object>>> teachers(
            @RequestParam(required = false) Long collegeId) {
        List<com.aicourse.entity.User> teachers = userService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.aicourse.entity.User>()
                        .eq(com.aicourse.entity.User::getRole, com.aicourse.common.RoleConstants.TEACHER)
                        .eq(com.aicourse.entity.User::getIsActive, true)
                        .eq(collegeId != null, com.aicourse.entity.User::getCollegeId, collegeId)
                        .orderByAsc(com.aicourse.entity.User::getFullName));
        return ApiResponse.success(teachers.stream().map(user -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("name", user.getFullName() == null ? user.getUsername() : user.getFullName());
            row.put("department", user.getDepartment());
            row.put("collegeId", user.getCollegeId());
            return row;
        }).toList());
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
        return authService.sendVerificationCode(email, type, clientInfoResolver.resolve(request));
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
        String validationError = validateFile(file, ext);
        if (validationError != null) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, validationError);
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
            return ApiResponse.error(ApiCode.INTERNAL_SERVER_ERROR, "上传失败，请稍后重试");
        }
    }

    /**
     * 批量文件上传。
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/upload/batch")
    public ApiResponse<List<UploadResultVO>> uploadBatch(@RequestParam("files") List<MultipartFile> files) {
        List<UploadResultVO> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            String originalName = file.getOriginalFilename();
            String safeName = originalName != null ? originalName : "未命名文件";
            String ext = extractSafeExt(originalName);
            if (ext.isEmpty()) {
                results.add(new UploadResultVO("", safeName, "0", "不支持的文件类型"));
                continue;
            }
            String validationError = validateFile(file, ext);
            if (validationError != null) {
                results.add(new UploadResultVO("", safeName, "0", validationError));
                continue;
            }

            String objectName = UUID.randomUUID() + ext;
            try {
                String stored = fileStorageService.upload(objectName,
                        file.getInputStream(), file.getContentType(), file.getSize());
                results.add(new UploadResultVO("/uploads/" + stored,
                        safeName, String.valueOf(file.getSize())));
            } catch (Exception e) {
                log.error("批量上传失败: {}", objectName, e);
                // 不把存储层/IO 异常细节回给客户端（可能含路径、桶名、内部实现信息）。
                results.add(new UploadResultVO("", safeName, "0", "上传失败，请稍后重试"));
            }
        }
        return ApiResponse.success(results);
    }

    /**
     * 获取功能路线图。
     */
    @GetMapping("/feature-roadmap")
    public ApiResponse<FeatureRoadmapVO> featureRoadmap() {
        List<FeatureItemVO> features = List.of(
                new FeatureItemVO("课程管理", "done"),
                new FeatureItemVO("班级管理", "done"),
                new FeatureItemVO("用户管理", "done"),
                new FeatureItemVO("AI课程", "partial"),
                new FeatureItemVO("AI助手", "partial")
        );
        return ApiResponse.success(new FeatureRoadmapVO("1.0.0", features));
    }
}
