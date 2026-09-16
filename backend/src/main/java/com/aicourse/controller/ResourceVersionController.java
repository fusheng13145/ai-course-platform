package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Resource;
import com.aicourse.entity.ResourceVersion;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.CourseService;
import com.aicourse.service.ResourceService;
import com.aicourse.service.ResourceVersionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.aicourse.common.ApiCode;
import com.aicourse.dto.ResourceVersionCreateDTO;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 资源版本管理控制器
 * 提供版本历史查询、新版本创建、版本回滚及当前版本获取等API
 */
@RestController
@RequestMapping("/resource-versions")
@Tag(name = "资源版本")
public class ResourceVersionController {

    private static final Logger log = LoggerFactory.getLogger(ResourceVersionController.class);

    private final ResourceVersionService resourceVersionService;
    private final ResourceService resourceService;
    private final CourseService courseService;

    public ResourceVersionController(ResourceVersionService resourceVersionService,
                                     ResourceService resourceService,
                                     CourseService courseService) {
        this.resourceVersionService = resourceVersionService;
        this.resourceService = resourceService;
        this.courseService = courseService;
    }

    /**
     * 获取资源的版本历史列表
     * @param resourceId 资源ID
     * @return 版本列表（按版本号降序）
     */
    @GetMapping("/resource/{resourceId}")
    public ApiResponse<List<ResourceVersion>> getVersionHistory(@PathVariable Long resourceId) {
        List<ResourceVersion> versions = resourceVersionService.getVersionHistory(resourceId);
        return ApiResponse.success(versions);
    }

    /**
     * 创建新版本
     * 权限：教师、管理员
     * @param dto 版本创建请求DTO
     * @return 新创建的版本
     */
    @PostMapping("")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<ResourceVersion> createNewVersion(@Valid @RequestBody ResourceVersionCreateDTO dto,
                                                         Authentication auth) {
        Long resourceId = dto.getResourceId();
        // 契约修复（2026-09-14）：DTO 的必填字段是 fileUrl（@NotBlank），但控制器原先只读 url，
        // 于是「按 DTO 契约只传 fileUrl」会被判「资源地址不能为空」，而「只传 url」又过不了 @NotBlank
        // —— 两端各自为政。现统一：优先 url，缺失时回退 fileUrl。
        String url = (dto.getUrl() != null && !dto.getUrl().isBlank()) ? dto.getUrl() : dto.getFileUrl();
        String fileName = dto.getFileName();
        String fileType = dto.getFileType();
        String description = dto.getDescription();
        Long fileSize = dto.getFileSize();

        if (resourceId == null) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "资源ID不能为空");
        }
        if (fileName == null || fileName.isEmpty()) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "文件名不能为空");
        }
        if (url == null || url.isEmpty()) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "资源地址不能为空");
        }
        // 归属校验（2026-09-14）：仅资源所属课程的授课教师或管理员可为该资源建版本/回滚
        assertResourceManageable(resourceId, auth);
        // 上传者以**当前登录人**为准，忽略客户端可伪造的 uploaderId
        Long uploaderId = (Long) auth.getPrincipal();

        ResourceVersion version = resourceVersionService.createNewVersion(
                resourceId, fileName, fileType, url, description, uploaderId, fileSize);
        return ApiResponse.success("版本创建成功", version);
    }

    /**
     * 回滚到指定版本
     * 权限：教师、管理员
     * @param id 资源ID
     * @param dto 版本创建请求DTO（仅使用versionNumber字段）
     * @return 操作结果
     */
    @PostMapping("/{id}/rollback")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> rollbackToVersion(@PathVariable Long id, @Valid @RequestBody ResourceVersionCreateDTO dto,
                                               Authentication auth) {
        Integer versionNumber = dto.getVersionNumber();
        if (versionNumber == null) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "目标版本号不能为空");
        }
        // 归属校验（2026-09-14）：回滚会改变对外可见的资源内容，须限资源所属课程教师/管理员
        assertResourceManageable(id, auth);
        resourceVersionService.rollbackToVersion(id, versionNumber);
        return ApiResponse.success("回滚成功");
    }

    /**
     * 获取资源当前启用的版本
     * @param resourceId 资源ID
     * @return 当前激活的版本
     */
    @GetMapping("/active/{resourceId}")
    public ApiResponse<ResourceVersion> getActiveVersion(@PathVariable Long resourceId) {
        ResourceVersion activeVersion = resourceVersionService.getActiveVersion(resourceId);
        return ApiResponse.success(activeVersion);
    }

    // ===================== 归属校验（内部工具） =====================

    /**
     * 资源归属校验：管理员放行；教师须为资源所属课程的授课教师。
     * 与 ExamPaperAccessControl / SubmissionAccessControl 同一模式（防 IDOR/水平越权）。
     */
    private void assertResourceManageable(Long resourceId, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(g -> "ROLE_ADMIN".equals(g.getAuthority()));
        if (isAdmin) {
            return;
        }
        Resource resource = resourceService.getById(resourceId);
        if (resource == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "资源不存在");
        }
        if (resource.getCourseId() != null) {
            var course = courseService.getById(resource.getCourseId());
            if (course == null) {
                throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
            }
            Long userId = (Long) auth.getPrincipal();
            if (!userId.equals(course.getTeacherId())) {
                log.warn("[ResourceVersion] 越权尝试：resourceId={}, userId={}, courseId={}, teacherId={}",
                        resourceId, userId, course.getId(), course.getTeacherId());
                throw new BusinessException(ApiCode.FORBIDDEN, "无权操作其他教师课程的资源版本");
            }
        }
    }
}
