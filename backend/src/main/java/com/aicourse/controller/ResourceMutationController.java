package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.dto.ResourceVisibilityDTO;
import com.aicourse.entity.Resource;
import com.aicourse.security.ResourceAccessSupport;
import com.aicourse.service.FileStorageService;
import com.aicourse.service.ResourceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程资源变更控制器
 * 提供资源可见性切换与删除两条教师写入口
 */
@RestController
@RequestMapping("/resources")
@Tag(name = "教学资源")
public class ResourceMutationController {

    private static final Logger log = LoggerFactory.getLogger(ResourceMutationController.class);

    private final ResourceService resourceService;
    private final ResourceAccessSupport resourceAccessSupport;
    private final FileStorageService fileStorageService;

    public ResourceMutationController(ResourceService resourceService,
                                      ResourceAccessSupport resourceAccessSupport,
                                      FileStorageService fileStorageService) {
        this.resourceService = resourceService;
        this.resourceAccessSupport = resourceAccessSupport;
        this.fileStorageService = fileStorageService;
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
        if (!resourceAccessSupport.canAccessResource(resource, auth)) {
            return ApiResponse.error(ApiCode.FORBIDDEN, "无权操作该课程资源");
        }
        resourceService.toggleVisibility(id, params.getVisibleToAll());
        return ApiResponse.success("可见性切换成功");
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
        if (!resourceAccessSupport.canAccessResource(resource, auth)) {
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
