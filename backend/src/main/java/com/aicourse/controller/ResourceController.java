package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Resource;
import com.aicourse.entity.ResourceVersion;
import com.aicourse.service.ResourceService;
import com.aicourse.service.ResourceVersionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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

@RestController
@RequestMapping("/resources")
public class ResourceController {
    private final ResourceService resourceService;
    private final ResourceVersionService versionService;

    public ResourceController(ResourceService resourceService, ResourceVersionService versionService) {
        this.resourceService = resourceService;
        this.versionService = versionService;
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<List<Resource>> list(@PathVariable Long courseId) {
        return ApiResponse.success(resourceService.list(
            new LambdaQueryWrapper<Resource>().eq(Resource::getCourseId, courseId)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Resource> getById(@PathVariable Long id) {
        return ApiResponse.success(resourceService.getById(id));
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Resource> upload(@RequestParam("file") MultipartFile file,
                                        @RequestParam Long courseId,
                                        @RequestParam(required = false) String description) {
        try {
            String uploadDir = "uploads/";
            new File(uploadDir).mkdirs();
            String ext = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
            String filename = UUID.randomUUID().toString() + ext;
            Path path = Paths.get(uploadDir + filename);
            Files.write(path, file.getBytes());

            Resource resource = new Resource();
            resource.setFileName(file.getOriginalFilename());
            resource.setFileType(file.getContentType());
            resource.setUrl("/uploads/" + filename);
            resource.setCourseId(courseId);
            resource.setUploadTime(LocalDateTime.now());
            resource.setDescription(description);
            resourceService.save(resource);

            // Create version
            ResourceVersion version = new ResourceVersion();
            version.setResourceId(resource.getId());
            version.setVersionNumber(1);
            version.setFileName(file.getOriginalFilename());
            version.setFileType(file.getContentType());
            version.setUrl("/uploads/" + filename);
            version.setFileSize(file.getSize());
            version.setIsActive(true);
            versionService.save(version);

            return ApiResponse.success("上传成功", resource);
        } catch (IOException e) {
            return ApiResponse.error(500, "上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/download/{id}")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long id) {
        Resource resource = resourceService.getById(id);
        if (resource == null) return org.springframework.http.ResponseEntity.notFound().build();
        try {
            Path filePath = Paths.get(resource.getUrl().substring(1));
            org.springframework.core.io.Resource file = new org.springframework.core.io.UrlResource(filePath.toUri());
            if (file.exists()) {
                return org.springframework.http.ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "attachment; filename=\"" + resource.getFileName() + "\"")
                    .body(file);
            }
        } catch (Exception e) {}
        return org.springframework.http.ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        resourceService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }
}
