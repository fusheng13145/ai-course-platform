package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.StorageLayout;
import com.aicourse.common.UploadMagic;
import com.aicourse.entity.Course;
import com.aicourse.entity.FileUploadSession;
import com.aicourse.vo.ResourceVOs;
import com.aicourse.entity.Resource;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.ResourceAccessSupport;
import com.aicourse.service.CourseService;
import com.aicourse.service.FileStorageService;
import com.aicourse.service.FileUploadSessionService;
import com.aicourse.service.FileVirusScanService;
import com.aicourse.service.ResourceService;
import com.aicourse.service.ResourceVersionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 课程资源上传控制器
 * 提供整文件上传与分片合并后登记两条教师入口
 */
@RestController
@RequestMapping("/resources")
@Tag(name = "教学资源")
public class ResourceUploadController {

    private static final Logger log = LoggerFactory.getLogger(ResourceUploadController.class);

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
    private final ResourceVersionService resourceVersionService;
    private final FileStorageService fileStorageService;
    private final FileUploadSessionService fileUploadSessionService;
    private final FileVirusScanService fileVirusScanService;
    private final ResourceAccessSupport resourceAccessSupport;

    public ResourceUploadController(ResourceService resourceService,
                                    CourseService courseService,
                                    ResourceVersionService resourceVersionService,
                                    FileStorageService fileStorageService,
                                    FileUploadSessionService fileUploadSessionService,
                                    FileVirusScanService fileVirusScanService,
                                    ResourceAccessSupport resourceAccessSupport) {
        this.resourceService = resourceService;
        this.courseService = courseService;
        this.resourceVersionService = resourceVersionService;
        this.fileStorageService = fileStorageService;
        this.fileUploadSessionService = fileUploadSessionService;
        this.fileVirusScanService = fileVirusScanService;
        this.resourceAccessSupport = resourceAccessSupport;
    }

    /**
     * 上传课程资源文件
     * 权限：教师、管理员；且教师仅可上传至本人任教课程（修复 #5：补对象级 ACL）。
     */
    @PostMapping("/upload")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    public ApiResponse<ResourceVOs.ResourceVO> uploadResource(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long courseId,
            @RequestParam(required = false) String description,
            Authentication auth) {
        // 修复 #5：先校验课程确实存在，避免造出 courseId 悬空的孤儿资源 + 磁盘残留文件
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        // 修复 #5：补齐对象级 ACL（此前仅有角色校验，任意教师可向他人课程上传资源）。
        // 抛 BusinessException(403) 经全局异常处理器映射为真实 HTTP 403，与同文件其余 ACL 端点一致。
        if (!resourceAccessSupport.canAccessCourse(courseId, auth)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权向该课程上传资源");
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

        // 与 /common/upload、分片合并同口径：魔数之后、落盘之前做病毒扫描（fail-closed）。
        // 教师资源是学生端可下载分发的内容，漏扫等于把恶意样本挂进课程资源。
        try {
            fileVirusScanService.scanOrThrow(file, safeFileName);
        } catch (BusinessException e) {
            log.warn("课程资源上传被病毒扫描拒绝: file={}, course={}, code={}, reason={}",
                    safeFileName, courseId, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        }

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

            return ApiResponse.success("文件上传成功", ResourceVOs.ResourceVO.from(resource));
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
    public ApiResponse<ResourceVOs.ResourceVO> createFromUploadSession(
            @PathVariable Long sessionId,
            @RequestParam Long courseId,
            @RequestParam(required = false) String description,
            Authentication auth) {
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        if (!resourceAccessSupport.canAccessCourse(courseId, auth)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权向该课程上传资源");
        }
        FileUploadSession session = fileUploadSessionService.getById(sessionId);
        if (session == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "上传会话不存在");
        }
        Long operatorId = (Long) auth.getPrincipal();
        if (session.getUploaderId() == null || !session.getUploaderId().equals(operatorId)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权登记该上传会话");
        }
        if (!"COMPLETED".equals(session.getStatus())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "上传会话尚未合并完成");
        }
        if (!StringUtils.hasText(session.getUploadPath())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "上传会话缺少存储路径");
        }

        String originalFilename = StringUtils.hasText(session.getFilename()) ? session.getFilename() : "unknown";
        int dot = originalFilename.lastIndexOf('.');
        String ext = dot >= 0 ? originalFilename.substring(dot + 1).toLowerCase() : "";
        String contentType = MIME_BY_EXT.get(ext);
        if (contentType == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "不支持该文件格式上传");
        }
        String magic;
        try (InputStream in = fileStorageService.download(session.getUploadPath())) {
            if (in == null) {
                throw new BusinessException(ApiCode.NOT_FOUND, "合并文件不存在或已回收");
            }
            magic = UploadMagic.detect(in.readNBytes(16));
        } catch (IOException e) {
            log.error("读取合并文件头失败: session={}, {}", sessionId, e.getMessage());
            throw new BusinessException(ApiCode.INTERNAL_SERVER_ERROR, "读取上传文件失败", e);
        }
        if (!UploadMagic.matchesMime(contentType, magic)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "文件内容与声明类型不符，已拒绝登记");
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
        return ApiResponse.success("文件上传成功", ResourceVOs.ResourceVO.from(resource));
    }
}

