package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Resource;
import com.aicourse.entity.ResourceVersion;
import com.aicourse.service.ResourceService;
import com.aicourse.service.ResourceVersionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import java.util.UUID;

/**
 * 课程资源控制器
 * 提供课程文件资源的查询、上传、下载、删除及版本管理能力
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
    private final ResourceVersionService versionService;

    public ResourceController(ResourceService resourceService, ResourceVersionService versionService) {
        this.resourceService = resourceService;
        this.versionService = versionService;
    }

    /**
     * 根据课程ID查询关联资源列表
     * @param courseId 课程ID
     * @return 资源列表
     */
    @GetMapping("/course/{courseId}")
    public ApiResponse<List<Resource>> listByCourse(@PathVariable Long courseId) {
        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<Resource>()
                .eq(Resource::getCourseId, courseId);
        List<Resource> resourceList = resourceService.list(queryWrapper);
        return ApiResponse.success(resourceList);
    }

    /**
     * 根据资源ID查询详情
     * @param id 资源ID
     * @return 资源详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Resource> getResourceById(@PathVariable Long id) {
        Resource resource = resourceService.getById(id);
        return ApiResponse.success(resource);
    }

    /**
     * 上传课程资源文件
     * 权限：教师、管理员
     * @param file 待上传文件
     * @param courseId 所属课程ID
     * @param description 资源描述（选填）
     * @return 上传后的资源信息
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Resource> uploadResource(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long courseId,
            @RequestParam(required = false) String description) {
        // 1. 基础校验
        if (file.isEmpty()) {
            return ApiResponse.error(400, "上传文件不能为空");
        }
        // 2. 文件大小校验
        if (file.getSize() > MAX_FILE_SIZE) {
            return ApiResponse.error(400, "文件大小不能超过50MB");
        }
        // 3. 文件类型校验
        String contentType = file.getContentType();
        boolean typeValid = false;
        for (String type : ALLOWED_MIME_TYPES) {
            if (type.equals(contentType)) {
                typeValid = true;
                break;
            }
        }
        if (!typeValid) {
            return ApiResponse.error(400, "不支持该文件格式上传");
        }

        // 4. 初始化上传目录
        File dir = new File(UPLOAD_BASE_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            return ApiResponse.error(500, "上传目录创建失败");
        }

        // 5. 解析文件名与后缀
        String originalFilename = file.getOriginalFilename();
        String safeFileName = StringUtils.hasText(originalFilename) ? originalFilename : "unknown";
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFileName = UUID.randomUUID() + suffix;
        Path targetPath = Paths.get(UPLOAD_BASE_DIR, uniqueFileName);

        try {
            // 6. 写入文件到本地
            Files.write(targetPath, file.getBytes());

            // 7. 保存资源主数据
            Resource resource = new Resource();
            resource.setFileName(safeFileName);
            resource.setFileType(contentType);
            resource.setUrl("/uploads/" + uniqueFileName);
            resource.setCourseId(courseId);
            resource.setUploadTime(LocalDateTime.now());
            resource.setDescription(description);
            resourceService.save(resource);

            // 8. 保存版本记录（初始版本=1）
            ResourceVersion version = new ResourceVersion();
            version.setResourceId(resource.getId());
            version.setVersionNumber(1);
            version.setFileName(safeFileName);
            version.setFileType(contentType);
            version.setUrl("/uploads/" + uniqueFileName);
            version.setFileSize(file.getSize());
            version.setIsActive(true);
            versionService.save(version);

            return ApiResponse.success("文件上传成功", resource);
        } catch (IOException e) {
            return ApiResponse.error(500, "文件上传异常：" + e.getMessage());
        }
    }

    /**
     * 下载资源文件
     * @param id 资源ID
     * @return 文件流响应
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadResource(@PathVariable Long id) {
        Resource resource = resourceService.getById(id);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            // 拼接物理路径
            String fileRelativePath = resource.getUrl().substring(1);
            Path filePath = Paths.get(fileRelativePath);
            UrlResource fileResource = new UrlResource(filePath.toUri());

            // 校验文件存在且为普通文件
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
     * 删除资源（逻辑删除 + 物理删除文件 + 联动删除版本记录）
     * 权限：教师、管理员
     * @param id 资源ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> deleteResource(@PathVariable Long id) {
        // 1. 查询资源信息
        Resource resource = resourceService.getById(id);
        if (resource == null) {
            return ApiResponse.error(404, "资源不存在");
        }

        // 2. 物理删除服务器文件
        String fileRelativePath = resource.getUrl().substring(1);
        Path filePath = Paths.get(fileRelativePath);
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
        } catch (IOException e) {
            return ApiResponse.error(500, "文件删除失败：" + e.getMessage());
        }

        // 3. 删除资源主表数据
        resourceService.removeById(id);

        // 4. 联动删除该资源下所有版本记录
        LambdaUpdateWrapper<ResourceVersion> versionWrapper = new LambdaUpdateWrapper<ResourceVersion>()
                .eq(ResourceVersion::getResourceId, id);
        versionService.remove(versionWrapper);

        return ApiResponse.success("资源删除成功", null);
    }
}