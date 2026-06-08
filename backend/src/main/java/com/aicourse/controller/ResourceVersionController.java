package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.ResourceVersion;
import com.aicourse.service.ResourceVersionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 资源版本管理控制器
 * 提供版本历史查询、新版本创建、版本回滚及当前版本获取等API
 */
@RestController
@RequestMapping("/resource-versions")
public class ResourceVersionController {

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
     * @param params 请求参数（包含 resourceId, fileName, fileType, url, description, fileSize）
     * @return 新创建的版本
     */
    @PostMapping("")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<ResourceVersion> createNewVersion(@RequestBody Map<String, Object> params) {
        Long resourceId = params.get("resourceId") != null ? Long.valueOf(params.get("resourceId").toString()) : null;
        String fileName = (String) params.get("fileName");
        String fileType = (String) params.get("fileType");
        String url = (String) params.get("url");
        String description = (String) params.get("description");
        Long uploaderId = params.get("uploaderId") != null ? Long.valueOf(params.get("uploaderId").toString()) : null;
        Long fileSize = params.get("fileSize") != null ? Long.valueOf(params.get("fileSize").toString()) : null;

        if (resourceId == null) {
            return ApiResponse.error(400, "资源ID不能为空");
        }
        if (fileName == null || fileName.isEmpty()) {
            return ApiResponse.error(400, "文件名不能为空");
        }
        if (url == null || url.isEmpty()) {
            return ApiResponse.error(400, "资源地址不能为空");
        }

        ResourceVersion version = resourceVersionService.createNewVersion(
                resourceId, fileName, fileType, url, description, uploaderId, fileSize);
        return ApiResponse.success("版本创建成功", version);
    }

    /**
     * 回滚到指定版本
     * 权限：教师、管理员
     * @param id 资源ID
     * @param params 请求参数（包含 versionNumber）
     * @return 操作结果
     */
    @PostMapping("/{id}/rollback")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> rollbackToVersion(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        Integer versionNumber = params.get("versionNumber") != null ?
                Integer.valueOf(params.get("versionNumber").toString()) : null;
        if (versionNumber == null) {
            return ApiResponse.error(400, "目标版本号不能为空");
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
