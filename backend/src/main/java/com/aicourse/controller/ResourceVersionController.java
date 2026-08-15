package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.ResourceVersion;
import com.aicourse.service.ResourceVersionService;
import org.springframework.security.access.prepost.PreAuthorize;
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

    public ResourceVersionController(ResourceVersionService resourceVersionService) {
        this.resourceVersionService = resourceVersionService;
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
    public ApiResponse<ResourceVersion> createNewVersion(@Valid @RequestBody ResourceVersionCreateDTO dto) {
        Long resourceId = dto.getResourceId();
        String fileName = dto.getFileName();
        String fileType = dto.getFileType();
        String url = dto.getUrl();
        String description = dto.getDescription();
        Long uploaderId = dto.getUploaderId();
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
    public ApiResponse<Void> rollbackToVersion(@PathVariable Long id, @Valid @RequestBody ResourceVersionCreateDTO dto) {
        Integer versionNumber = dto.getVersionNumber();
        if (versionNumber == null) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "目标版本号不能为空");
        }
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
}
