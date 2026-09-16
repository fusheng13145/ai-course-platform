package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiResponse;
import com.aicourse.dto.ResourceVisibilityDTO;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Resource;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ResourceService;
import com.aicourse.vo.ChapterResourceTreeVO;
import com.aicourse.vo.ResourceStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    // 全局常量：上传目录、文件大小限制(50MB)、允许的文件类型
    private static final String UPLOAD_BASE_DIR = "uploads/";
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    private static final String[] ALLOWED_MIME_TYPES = {
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "image/jpeg", "image/png", "image/gif",
            "video/mp4", "audio/mpeg"
    };

    // 常见文件魔数（修复 P1-6：Content-Type 可伪造，必须以文件内容校验真实类型）
    private static final byte[] MAGIC_PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] MAGIC_PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] MAGIC_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_GIF = {'G', 'I', 'F', '8'};
    private static final byte[] MAGIC_ZIP = {'P', 'K', 0x03, 0x04};
    private static final byte[] MAGIC_OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    private final ResourceService resourceService;
    private final CourseService courseService;
    private final CourseEnrollmentService courseEnrollmentService;

    public ResourceController(ResourceService resourceService,
                              CourseService courseService,
                              CourseEnrollmentService courseEnrollmentService) {
        this.resourceService = resourceService;
        this.courseService = courseService;
        this.courseEnrollmentService = courseEnrollmentService;
    }

    /**
     * 根据课程ID查询关联资源列表
     */
    @GetMapping("/course/{courseId}")
    public ApiResponse<List<Resource>> listByCourse(@PathVariable Long courseId) {
        List<Resource> resourceList = resourceService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Resource>()
                        .eq(Resource::getCourseId, courseId)
        );
        return ApiResponse.success(resourceList);
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
    public ApiResponse<List<ChapterResourceTreeVO>> getResourceTree(@PathVariable Long courseId) {
        List<ChapterResourceTreeVO> tree = resourceService.getResourceTree(courseId);
        return ApiResponse.success(tree);
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
    public ApiResponse<Page<Resource>> searchResources(
            @RequestParam String keyword,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Resource> result = resourceService.searchResources(keyword, courseId, page, size);
        return ApiResponse.success(result);
    }

    /**
     * 获取课程资源统计信息（类型分布、大小总计等）
     * @param courseId 课程ID
     * @return 统计数据
     */
    @GetMapping("/stats/course/{courseId}")
    public ApiResponse<ResourceStatsVO> getResourceStats(@PathVariable Long courseId) {
        ResourceStatsVO stats = resourceService.getResourceStats(courseId);
        return ApiResponse.success(stats);
    }

    /**
     * 资源可见性切换
     * @param id 资源ID
     * @param params 请求参数（包含 visibleToAll）
     * @return 操作结果
     */
    @PatchMapping("/{id}/visibility")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
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
    public ApiResponse<List<Resource>> getResourcesByChapter(
            @RequestParam Long courseId, @PathVariable Long chapterId) {
        List<Resource> resources = resourceService.getResourcesByChapter(courseId, chapterId);
        return ApiResponse.success(resources);
    }

    /**
     * 上传课程资源文件
     * 权限：教师、管理员
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Resource> uploadResource(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long courseId,
            @RequestParam(required = false) String description) {
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

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("读取上传文件失败: {}", e.getMessage(), e);
            return ApiResponse.error(ApiCode.INTERNAL_SERVER_ERROR, "读取上传文件失败：" + e.getMessage());
        }
        // 修复 P1-6：Content-Type 由客户端声明可伪造，必须以文件魔数校验真实内容，
        // 阻断"伪装成图片的可执行/HTML/脚本文件"入库（防存储型 XSS / 恶意文件分发）。
        String magic = detectMagicType(bytes);
        if (!magicMatchesContentType(contentType, magic)) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "文件内容与声明类型不符，已拒绝上传");
        }

        File dir = new File(UPLOAD_BASE_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            return ApiResponse.error(ApiCode.INTERNAL_SERVER_ERROR, "上传目录创建失败");
        }

        String originalFilename = file.getOriginalFilename();
        String safeFileName = StringUtils.hasText(originalFilename) ? originalFilename : "unknown";
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFileName = UUID.randomUUID() + suffix;
        Path targetPath = Paths.get(UPLOAD_BASE_DIR, uniqueFileName);

        try {
            Files.write(targetPath, bytes);

            Resource resource = new Resource();
            resource.setFileName(safeFileName);
            resource.setFileType(contentType);
            resource.setUrl("/uploads/" + uniqueFileName);
            resource.setCourseId(courseId);
            resource.setUploadTime(LocalDateTime.now());
            resource.setDescription(description);
            resource.setVisibleToAll(true);
            resourceService.save(resource);

            return ApiResponse.success("文件上传成功", resource);
        } catch (IOException e) {
            log.error("写入上传文件失败: {}", e.getMessage(), e);
            return ApiResponse.error(ApiCode.INTERNAL_SERVER_ERROR, "文件上传异常：" + e.getMessage());
        }
    }

    /**
     * 下载资源文件（修复 P1-6：下载必须校验资源访问权限——
     * ADMIN 放行；教师仅限本课程；学生必须已选该课程，否则 403）
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
            String fileRelativePath = resource.getUrl().substring(1);
            Path filePath = Paths.get(fileRelativePath);
            UrlResource fileResource = new UrlResource(filePath.toUri());

            if (fileResource.exists() && fileResource.isFile()) {
                // 修复 P1-6：文件名中的 CRLF/引号会污染 Content-Disposition 头（头注入），统一清洗
                String safeFileName = sanitizeHeaderValue(resource.getFileName());
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("Content-Disposition", "attachment; filename=\"" + safeFileName + "\"")
                        .body(fileResource);
            }
        } catch (Exception e) {
            // 捕获路径、IO 异常，统一返回404
            log.warn("下载资源文件异常，返回404: id={}", id, e);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 资源访问权限校验（修复 P1-6）
     */
    private boolean canAccessResource(Resource resource, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            return false;
        }
        Long userId = (Long) auth.getPrincipal();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isTeacher = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TEACHER".equals(a.getAuthority()));
        if (isAdmin) {
            return true;
        }
        if (isTeacher) {
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
     * 清洗响应头中的文件名：移除 CR/LF 与引号（防 HTTP 头注入）
     */
    private String sanitizeHeaderValue(String value) {
        if (value == null) {
            return "file";
        }
        return value.replaceAll("[\\r\\n\"]", "_");
    }

    /**
     * 识别文件魔数（读取文件头 8 字节）
     */
    private String detectMagicType(byte[] b) {
        if (b == null || b.length < 8) {
            return null;
        }
        if (startsWith(b, MAGIC_PDF)) return "pdf";
        if (startsWith(b, MAGIC_PNG)) return "png";
        if (startsWith(b, MAGIC_JPEG)) return "jpeg";
        if (startsWith(b, MAGIC_GIF)) return "gif";
        if (startsWith(b, MAGIC_ZIP)) return "zip";
        if (startsWith(b, MAGIC_OLE2)) return "ole2";
        if (b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') return "mp4";
        return null;
    }

    private boolean startsWith(byte[] data, byte[] magic) {
        if (data.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) return false;
        }
        return true;
    }

    /**
     * 声明 Content-Type 与魔数一致性校验（修复 P1-6）
     */
    private boolean magicMatchesContentType(String contentType, String magic) {
        if (magic == null) {
            return false;
        }
        if ("application/pdf".equals(contentType)) return "pdf".equals(magic);
        if ("image/png".equals(contentType)) return "png".equals(magic);
        if ("image/jpeg".equals(contentType)) return "jpeg".equals(magic);
        if ("image/gif".equals(contentType)) return "gif".equals(magic);
        if ("video/mp4".equals(contentType)) return "mp4".equals(magic);
        // MP3 帧头（FFFx）不统一，仅对声明类型放行
        if ("audio/mpeg".equals(contentType)) return true;
        // Word/Excel 新格式（docx/xlsx/pptx）为 ZIP 容器；旧格式（doc/xls）为 OLE2 复合文档
        if (contentType != null
                && (contentType.contains("word") || contentType.contains("excel"))) {
            return "zip".equals(magic) || "ole2".equals(magic);
        }
        return false;
    }

    /**
     * 删除资源
     * 权限：教师、管理员（修复 P1-6：教师仅能删除本课程资源）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> deleteResource(@PathVariable Long id, Authentication auth) {
        Resource resource = resourceService.getById(id);
        if (resource == null) {
            return ApiResponse.error(ApiCode.NOT_FOUND, "资源不存在");
        }
        if (!canAccessResource(resource, auth)) {
            return ApiResponse.error(ApiCode.FORBIDDEN, "无权删除该课程资源");
        }

        String fileRelativePath = resource.getUrl().substring(1);
        Path filePath = Paths.get(fileRelativePath);
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
        } catch (IOException e) {
            log.error("删除资源文件失败: {}", e.getMessage(), e);
            return ApiResponse.error(ApiCode.INTERNAL_SERVER_ERROR, "文件删除失败：" + e.getMessage());
        }

        resourceService.removeById(id);
        return ApiResponse.success("资源删除成功", null);
    }
}
