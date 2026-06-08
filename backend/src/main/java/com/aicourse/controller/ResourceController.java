package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Resource;
import com.aicourse.service.ResourceService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

/**
 * 课程资源控制器
 * 提供课程文件资源的查询、上传、下载、删除、资源树、搜索、统计及可见性管理等能力
 */
@RestController
@RequestMapping("/resources")
public class ResourceController {

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

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
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
    public ApiResponse<Resource> getResourceById(@PathVariable Long id) {
        Resource resource = resourceService.getById(id);
        return ApiResponse.success(resource);
    }

    /**
     * 获取课程的资源树（按章节组织）
     * @param courseId 课程ID
     * @return 资源树列表
     */
    @GetMapping("/tree/{courseId}")
    public ApiResponse<List<Map<String, Object>>> getResourceTree(@PathVariable Long courseId) {
        List<Map<String, Object>> tree = resourceService.getResourceTree(courseId);
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
    public ApiResponse<Map<String, Object>> getResourceStats(@PathVariable Long courseId) {
        Map<String, Object> stats = resourceService.getResourceStats(courseId);
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
    public ApiResponse<Void> toggleVisibility(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        Boolean visibleToAll = params.get("visibleToAll") != null ?
                Boolean.valueOf(params.get("visibleToAll").toString()) : null;
        if (visibleToAll == null) {
            return ApiResponse.error(400, "可见性参数不能为空");
        }
        resourceService.toggleVisibility(id, visibleToAll);
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
            return ApiResponse.error(400, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ApiResponse.error(400, "文件大小不能超过50MB");
        }
        String contentType = file.getContentType();
        boolean typeValid = false;
        for (String type : ALLOWED_MIME_TYPES) {
            if (type.equals(contentType)) { typeValid = true; break; }
        }
        if (!typeValid) {
            return ApiResponse.error(400, "不支持该文件格式上传");
        }

        File dir = new File(UPLOAD_BASE_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            return ApiResponse.error(500, "上传目录创建失败");
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
            Files.write(targetPath, file.getBytes());

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
            return ApiResponse.error(500, "文件上传异常：" + e.getMessage());
        }
    }

    /**
     * 下载资源文件
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadResource(@PathVariable Long id) {
        Resource resource = resourceService.getById(id);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            String fileRelativePath = resource.getUrl().substring(1);
            Path filePath = Paths.get(fileRelativePath);
            UrlResource fileResource = new UrlResource(filePath.toUri());

            if (fileResource.exists() && fileResource.isFile()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("Content-Disposition", "attachment; filename=\"" + resource.getFileName() + "\"")
                        .body(fileResource);
            }
        } catch (Exception e) {
            // 捕获路径、IO 异常，统一返回404
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 删除资源
     * 权限：教师、管理员
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> deleteResource(@PathVariable Long id) {
        Resource resource = resourceService.getById(id);
        if (resource == null) {
            return ApiResponse.error(404, "资源不存在");
        }

        String fileRelativePath = resource.getUrl().substring(1);
        Path filePath = Paths.get(fileRelativePath);
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
        } catch (IOException e) {
            return ApiResponse.error(500, "文件删除失败：" + e.getMessage());
        }

        resourceService.removeById(id);
        return ApiResponse.success("资源删除成功", null);
    }
}
