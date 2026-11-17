package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.StorageLayout;
import com.aicourse.common.UploadMagic;
import com.aicourse.dto.ResourceVisibilityDTO;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.FileUploadSession;
import com.aicourse.entity.Resource;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.FileStorageService;
import com.aicourse.service.FileUploadSessionService;
import com.aicourse.service.ResourceService;
import com.aicourse.service.ResourceVersionService;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.vo.ChapterResourceTreeVO;
import com.aicourse.vo.ResourceStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.Valid;
import com.aicourse.common.ApiCode;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 课程资源控制器
 * 提供课程文件资源的查询、上传、下载、删除、资源树、搜索、统计及可见性管理等能力
 */
@RestController
@RequestMapping("/resources")
@Tag(name = "教学资源")
public class ResourceController {

    private static final Logger log = LoggerFactory.getLogger(ResourceController.class);

    // 文件大小限制(50MB)、允许的文件类型
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    private static final String[] ALLOWED_MIME_TYPES = {
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "image/jpeg", "image/png", "image/gif",
            "video/mp4", "audio/mpeg"
    };

    // 修复 #13：已通过 ALLOWED_MIME_TYPES + 魔数校验的 contentType 到落盘后缀的唯一映射。
    // 落盘后缀只允许来自此白名单，禁止直接采用客户端原始文件名后缀（防同源 .html 托管页 + 路径穿越 N-04）。
    private static final java.util.Map<String, String> SUFFIX_BY_MIME = java.util.Map.of(
            "application/pdf", ".pdf",
            "application/msword", ".doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx",
            "application/vnd.ms-excel", ".xls",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx",
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "video/mp4", ".mp4",
            "audio/mpeg", ".mp3");

    /** 分片合并后登记资源时，扩展名 → MIME 的可信映射；与 {@link #ALLOWED_MIME_TYPES} 同集合。 */
    private static final java.util.Map<String, String> MIME_BY_EXT = java.util.Map.ofEntries(
            java.util.Map.entry("pdf", "application/pdf"),
            java.util.Map.entry("doc", "application/msword"),
            java.util.Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            java.util.Map.entry("xls", "application/vnd.ms-excel"),
            java.util.Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            java.util.Map.entry("jpg", "image/jpeg"),
            java.util.Map.entry("jpeg", "image/jpeg"),
            java.util.Map.entry("png", "image/png"),
            java.util.Map.entry("gif", "image/gif"),
            java.util.Map.entry("mp4", "video/mp4"),
            java.util.Map.entry("mp3", "audio/mpeg"));

    private final ResourceService resourceService;
    private final CourseService courseService;
    private final CourseEnrollmentService courseEnrollmentService;
    private final ResourceVersionService resourceVersionService;
    private final FileStorageService fileStorageService;
    private final AdminScopeGuard adminScopeGuard;
    private final CourseAccessControl courseAccessControl;
    private final FileUploadSessionService fileUploadSessionService;

    public ResourceController(ResourceService resourceService,
                              CourseService courseService,
                              CourseEnrollmentService courseEnrollmentService,
                              ResourceVersionService resourceVersionService,
                              FileStorageService fileStorageService,
                              AdminScopeGuard adminScopeGuard,
                              CourseAccessControl courseAccessControl,
                              FileUploadSessionService fileUploadSessionService) {
        this.resourceService = resourceService;
        this.courseService = courseService;
        this.courseEnrollmentService = courseEnrollmentService;
        this.resourceVersionService = resourceVersionService;
        this.fileStorageService = fileStorageService;
        this.adminScopeGuard = adminScopeGuard;
        this.courseAccessControl = courseAccessControl;
        this.fileUploadSessionService = fileUploadSessionService;
    }

    /**
     * 根据课程ID查询关联资源列表
     */
    @GetMapping("/course/{courseId}")
    public ResponseEntity<ApiResponse<List<Resource>>> listByCourse(@PathVariable Long courseId, Authentication auth) {
        if (!canAccessCourse(courseId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(ApiCode.FORBIDDEN, "无权访问该课程资源"));
        }
        List<Resource> resourceList = resourceService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Resource>()
                        .eq(Resource::getCourseId, courseId)
        );
        return ResponseEntity.ok(ApiResponse.success(resourceList));
    }

    /**
     * 根据资源ID查询详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Resource> getResourceById(@PathVariable Long id, Authentication auth) {
        Resource resource = resourceService.getById(id);
        if (resource == null) {
            return ApiResponse.error(ApiCode.NOT_FOUND, "资源不存在");
        }
        if (!canAccessResource(resource, auth)) {
            return ApiResponse.error(ApiCode.FORBIDDEN, "无权访问该资源");
        }
        return ApiResponse.success(resource);
    }

    /**
     * 获取课程的资源树（按章节组织）
     * @param courseId 课程ID
     * @return 资源树列表
     */
    @GetMapping("/tree/{courseId}")
    public ResponseEntity<ApiResponse<List<ChapterResourceTreeVO>>> getResourceTree(@PathVariable Long courseId,
                                                                                   Authentication auth) {
        if (!canAccessCourse(courseId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(ApiCode.FORBIDDEN, "无权访问该课程资源"));
        }
        List<ChapterResourceTreeVO> tree = resourceService.getResourceTree(courseId);
        return ResponseEntity.ok(ApiResponse.success(tree));
    }

    /**
     * 资源搜索（按标题/描述模糊搜索）
     * @param keyword 搜索关键词
     * @param courseId 限定课程ID（可选）
     * @param page 页码
     * @param size 每页大小
     * @return 分页搜索结果
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<Resource>>> searchResources(
            @RequestParam String keyword,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        if (courseId == null) {
            // 跨课程搜索会枚举全站资源（含 /uploads 路径），属越权风险：仅管理员允许，
            // 院系管理员进一步收口到本学院可读课程（校级管理员 readableCourseIds=null 才全量）。
            if (auth == null || !(auth.getPrincipal() instanceof Long) || !isAdmin(auth)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ApiCode.FORBIDDEN, "请指定课程ID进行资源搜索"));
            }
            List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
            Page<Resource> result = resourceService.searchResourcesInCourses(
                    keyword, readableCourseIds, page, size);
            return ResponseEntity.ok(ApiResponse.success(result));
        }
        if (!canAccessCourse(courseId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(ApiCode.FORBIDDEN, "无权访问该课程资源"));
        }
        Page<Resource> result = resourceService.searchResources(keyword, courseId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取课程资源统计信息（类型分布、大小总计等）
     * @param courseId 课程ID
     * @return 统计数据
     */
    @GetMapping("/stats/course/{courseId}")
    public ResponseEntity<ApiResponse<ResourceStatsVO>> getResourceStats(@PathVariable Long courseId, Authentication auth) {
        if (!canAccessCourse(courseId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(ApiCode.FORBIDDEN, "无权访问该课程资源"));
        }
        ResourceStatsVO stats = resourceService.getResourceStats(courseId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 资源可见性切换
     * @param id 资源ID
     * @param params 请求参数（包含 visibleToAll）
     * @return 操作结果
     */
    @PatchMapping("/{id}/visibility")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    public ApiResponse<Void> toggleVisibility(@PathVariable Long id,
                                              @Valid @RequestBody ResourceVisibilityDTO params,
                                              Authentication auth) {
        Resource resource = resourceService.getById(id);
        if (resource == null) {
            return ApiResponse.error(ApiCode.NOT_FOUND, "资源不存在");
        }
        if (!canAccessResource(resource, auth)) {
            return ApiResponse.error(ApiCode.FORBIDDEN, "无权操作该课程资源");
        }
        resourceService.toggleVisibility(id, params.getVisibleToAll());
        return ApiResponse.success("可见性切换成功");
    }

    /**
     * 获取课程下某章节的资源列表
     * @param courseId 课程ID
     * @param chapterId 章节ID
     * @return 章节下的资源列表
     */
    @GetMapping("/chapter/{chapterId}")
    public ResponseEntity<ApiResponse<List<Resource>>> getResourcesByChapter(
            @RequestParam Long courseId, @PathVariable Long chapterId, Authentication auth) {
        if (!canAccessCourse(courseId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(ApiCode.FORBIDDEN, "无权访问该课程资源"));
        }
        List<Resource> resources = resourceService.getResourcesByChapter(courseId, chapterId);
        return ResponseEntity.ok(ApiResponse.success(resources));
    }

    /**
     * 上传课程资源文件
     * 权限：教师、管理员；且教师仅可上传至本人任教课程（修复 #5：补对象级 ACL）。
     */
    @PostMapping("/upload")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    public ApiResponse<Resource> uploadResource(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long courseId,
            @RequestParam(required = false) String description,
            Authentication auth) {
        // 修复 #5：先校验课程确实存在，避免造出 courseId 悬空的孤儿资源 + 磁盘残留文件
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }
        // 修复 #5：补齐对象级 ACL（此前仅有角色校验，任意教师可向他人课程上传资源）。
        // 抛 BusinessException(403) 经全局异常处理器映射为真实 HTTP 403，与同文件其余 ACL 端点一致。
        if (!canAccessCourse(courseId, auth)) {
            throw new BusinessException(403, "无权向该课程上传资源");
        }
        if (file.isEmpty()) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "文件大小不能超过50MB");
        }
        String contentType = file.getContentType();
        boolean typeValid = false;
        for (String type : ALLOWED_MIME_TYPES) {
            if (type.equals(contentType)) { typeValid = true; break; }
        }
        if (!typeValid) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "不支持该文件格式上传");
        }

        byte[] header;
        try {
            // 只读文件头做魔数校验，不再把整文件读进堆；随后重新打开输入流交给存储层。
            header = UploadMagic.readHeader(file);
        } catch (IOException e) {
            log.error("读取上传文件头失败: {}", e.getMessage(), e);
            return ApiResponse.error(ApiCode.INTERNAL_SERVER_ERROR, "读取上传文件失败：" + e.getMessage());
        }
        // 修复 P1-6：Content-Type 由客户端声明可伪造，必须以文件魔数校验真实内容，
        // 阻断"伪装成图片的可执行/HTML/脚本文件"入库（防存储型 XSS / 恶意文件分发）。
        String magic = UploadMagic.detect(header);
        if (!UploadMagic.matchesMime(contentType, magic)) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "文件内容与声明类型不符，已拒绝上传");
        }

        String originalFilename = file.getOriginalFilename();
        String safeFileName = StringUtils.hasText(originalFilename) ? originalFilename : "unknown";

        // 修复 #13：落盘后缀由已校验可信的 contentType 经白名单映射决定，禁止采用客户端原始文件名后缀。
        // 原始文件名后缀可能含 "/" 造成路径穿越（N-04），且其值可控会导致 .html 经 /uploads/** 回显形成同源托管页。
        String suffix = SUFFIX_BY_MIME.get(contentType);
        if (suffix == null) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "不支持该文件格式上传");
        }
        // 新文件按课程分组写入仓库外存储根；对外仍用 /uploads/<key> 兼容旧前缀。
        String objectKey = "resources/" + courseId + "/" + UUID.randomUUID() + suffix;

        try {
            String stored = fileStorageService.upload(objectKey, file.getInputStream(),
                    contentType, file.getSize());
            if (stored == null || stored.isBlank()) {
                return ApiResponse.error(ApiCode.INTERNAL_SERVER_ERROR, "文件存储失败");
            }

            Resource resource = new Resource();
            resource.setFileName(safeFileName);
            resource.setFileType(contentType);
            resource.setUrl(StorageLayout.publicUrl(objectKey));
            resource.setCourseId(courseId);
            resource.setUploaderId((Long) auth.getPrincipal());
            resource.setUploadTime(LocalDateTime.now());
            resource.setDescription(description);
            // V-2（2026-11-15）：教学资源仅对已加入该课程的用户可见。
            // 旧实现硬编码 true，等于任何登录用户拿到 UUID 即可直链下载；与裁定口径冲突。
            // 上传方如需公开，后续以显式参数开放，不再作为默认值。
            resource.setVisibleToAll(Boolean.FALSE);
            resourceService.save(resource);
            // B-13㉑：上传即落 resource_versions 首版，file_size 与上传人可供版本历史/回滚溯源。
            resourceVersionService.createNewVersion(resource.getId(), safeFileName, contentType,
                    resource.getUrl(), "初始上传", (Long) auth.getPrincipal(), file.getSize());

            return ApiResponse.success("文件上传成功", resource);
        } catch (IOException e) {
            log.error("读取上传文件流失败: {}", e.getMessage(), e);
            return ApiResponse.error(ApiCode.INTERNAL_SERVER_ERROR, "读取上传文件失败");
        } catch (RuntimeException e) {
            log.error("存储服务写入失败: {}", e.getMessage(), e);
            return ApiResponse.error(ApiCode.INTERNAL_SERVER_ERROR, "文件上传异常：" + e.getMessage());
        }
    }

    /**
     * 把已合并的分片上传会话登记为课程资源（B-11 教师侧大文件入口）。
     *
     * <p>分片上传只负责把字节落到 {@code uploadPath}；本端点负责把对象归属到课程、
     * 做扩展名/MIME/魔数校验并写 {@code resources} 与 {@code resource_versions} 首版。
     * 仅教师可用，且必须与上传会话的 uploaderId 一致，避免拿别人的会话 id 登记资源。</p>
     */
    @PostMapping("/from-upload-session/{sessionId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<Resource> createFromUploadSession(
            @PathVariable Long sessionId,
            @RequestParam Long courseId,
            @RequestParam(required = false) String description,
            Authentication auth) {
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }
        if (!canAccessCourse(courseId, auth)) {
            throw new BusinessException(403, "无权向该课程上传资源");
        }
        FileUploadSession session = fileUploadSessionService.getById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "上传会话不存在");
        }
        Long operatorId = (Long) auth.getPrincipal();
        if (session.getUploaderId() == null || !session.getUploaderId().equals(operatorId)) {
            throw new BusinessException(403, "无权登记该上传会话");
        }
        if (!"COMPLETED".equals(session.getStatus())) {
            throw new BusinessException(400, "上传会话尚未合并完成");
        }
        if (!StringUtils.hasText(session.getUploadPath())) {
            throw new BusinessException(400, "上传会话缺少存储路径");
        }

        String originalFilename = StringUtils.hasText(session.getFilename()) ? session.getFilename() : "unknown";
        int dot = originalFilename.lastIndexOf('.');
        String ext = dot >= 0 ? originalFilename.substring(dot + 1).toLowerCase() : "";
        String contentType = MIME_BY_EXT.get(ext);
        if (contentType == null) {
            throw new BusinessException(400, "不支持该文件格式上传");
        }
        String magic;
        try (InputStream in = fileStorageService.download(session.getUploadPath())) {
            if (in == null) {
                throw new BusinessException(404, "合并文件不存在或已回收");
            }
            magic = UploadMagic.detect(in.readNBytes(16));
        } catch (IOException e) {
            log.error("读取合并文件头失败: session={}, {}", sessionId, e.getMessage());
            throw new BusinessException(500, "读取上传文件失败", e);
        }
        if (!UploadMagic.matchesMime(contentType, magic)) {
            throw new BusinessException(400, "文件内容与声明类型不符，已拒绝登记");
        }

        Resource resource = new Resource();
        resource.setFileName(originalFilename);
        resource.setFileType(contentType);
        resource.setUrl(StorageLayout.publicUrl(session.getUploadPath()));
        resource.setCourseId(courseId);
        resource.setUploaderId(operatorId);
        resource.setUploadTime(LocalDateTime.now());
        resource.setDescription(description);
        resource.setVisibleToAll(Boolean.FALSE);
        resourceService.save(resource);
        resourceVersionService.createNewVersion(resource.getId(), originalFilename, contentType,
                resource.getUrl(), "分片上传", operatorId,
                session.getFileSize() == null ? Long.valueOf(0L) : session.getFileSize());
        return ApiResponse.success("文件上传成功", resource);
    }

    /**
     * 下载资源文件（修复 P1-6：下载必须校验资源访问权限——
     * 管理员按学院作用域；教师仅限本课程；学生必须已选该课程，否则 403）
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadResource(@PathVariable Long id,
                                                                                 Authentication auth) {
        Resource resource = resourceService.getById(id);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessResource(resource, auth)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        try {
            InputStream inputStream = fileStorageService.downloadByUrl(resource.getUrl());
            if (inputStream != null) {
                // 修复 P1-6：文件名中的 CRLF/引号会污染 Content-Disposition 头（头注入），统一清洗
                String safeFileName = sanitizeHeaderValue(resource.getFileName());
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("Content-Disposition", "attachment; filename=\"" + safeFileName + "\"")
                        .body(new InputStreamResource(inputStream));
            }
        } catch (Exception e) {
            // 捕获路径、IO 异常，统一返回404
            log.warn("下载资源文件异常，返回404: id={}", id, e);
        }
        return ResponseEntity.notFound().build();
    }

    // ===================== 权限辅助方法（重构自 P1-6 的 canAccessResource） =====================

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private boolean isTeacher(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TEACHER".equals(a.getAuthority()));
    }

    /**
     * 单个资源访问权限校验（修复 P1-6）：下载/详情/删除/可见性切换复用。
     */
    private boolean canAccessResource(Resource resource, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            return false;
        }
        Long userId = (Long) auth.getPrincipal();
        if (isAdmin(auth)) {
            if (resource.getCourseId() == null) {
                // 全局资源保留既有「登录可读」口径；管理员可读，非管理员仍按下方课程成员规则拒绝。
                return true;
            }
            return canAdminAccessCourse(resource.getCourseId());
        }
        if (isTeacher(auth)) {
            Course course = resource.getCourseId() != null ? courseService.getById(resource.getCourseId()) : null;
            return course != null && userId.equals(course.getTeacherId());
        }
        // 学生：必须是该课程**有效且已审批通过**的成员
        // （修复 2026-09-14：原先只查 is_active，待审批/已驳回的报名记录也能下载资源）
        if (resource.getCourseId() == null) {
            return false;
        }
        return courseEnrollmentService.hasApprovedEnrollment(userId, resource.getCourseId());
    }

    /**
     * 课程级访问权限校验（DR-16 资源列表越权修复）。
     * <p>此前 listByCourse / tree / stats / chapter / search 等列表端点**无任何 ACL**，
     * 任意已登录用户可枚举任意课程的资源（含 {@code /uploads/<uuid>} 路径），再经
     * {@code /uploads/**}（已受登录保护）直链下载，构成跨课程越权读取。</p>
     * 规则与 {@link #canAccessResource} 对齐：管理员按学院作用域（校级全平台）；
     * 教师须是该课程 teacherId；学生须 {@code hasApprovedEnrollment}。返回 false 的调用方应返回 403。
     */
    private boolean canAccessCourse(Long courseId, Authentication auth) {
        if (courseId == null) {
            return false;
        }
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            return false;
        }
        Long userId = (Long) auth.getPrincipal();
        if (isAdmin(auth)) {
            return canAdminAccessCourse(courseId);
        }
        if (isTeacher(auth)) {
            Course course = courseService.getById(courseId);
            return course != null && userId.equals(course.getTeacherId());
        }
        return courseEnrollmentService.hasApprovedEnrollment(userId, courseId);
    }

    /** 管理员课程作用域：院系管理员只能读本学院（含教师学院回退），校级全平台。 */
    private boolean canAdminAccessCourse(Long courseId) {
        if (courseId == null) {
            return false;
        }
        try {
            adminScopeGuard.assertCourseAccess(courseService.getById(courseId));
            return true;
        } catch (AccessDeniedException e) {
            return false;
        }
    }

    /**
     * 清洗响应头中的文件名：移除 CR/LF 与引号（防 HTTP 头注入）
     */
    private String sanitizeHeaderValue(String value) {
        if (value == null) {
            return "file";
        }
        return value.replaceAll("[\\r\\n\"]", "_");
    }

    /**
     * 删除资源
     * 权限：教师、管理员（修复 P1-6：教师仅能删除本课程资源）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    public ApiResponse<Void> deleteResource(@PathVariable Long id, Authentication auth) {
        Resource resource = resourceService.getById(id);
        if (resource == null) {
            return ApiResponse.error(ApiCode.NOT_FOUND, "资源不存在");
        }
        if (!canAccessResource(resource, auth)) {
            return ApiResponse.error(ApiCode.FORBIDDEN, "无权删除该课程资源");
        }

        // 先删库行再删物理文件：库行删除失败时文件仍在，可人工恢复；反过来会留下不可达的孤儿库行。
        resourceService.removeById(id);
        boolean fileDeleted = fileStorageService.deleteByUrl(resource.getUrl());
        if (!fileDeleted) {
            // 文件已不存在或路径非法不阻断主流程，但留日志供清理任务核对。
            log.warn("资源文件未删除（可能已不存在）: id={}, url={}", id, resource.getUrl());
        }
        return ApiResponse.success("资源删除成功", null);
    }
}
