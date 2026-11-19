package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Resource;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.ResourceAccessSupport;
import com.aicourse.service.FileStorageService;
import com.aicourse.service.ResourceService;
import com.aicourse.vo.ChapterResourceTreeVO;
import com.aicourse.vo.ResourceStatsVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.List;

/**
 * 课程资源查询控制器
 * 提供课程文件资源的列表、详情、资源树、搜索、统计、章节列表与下载能力
 */
@RestController
@RequestMapping("/resources")
@Tag(name = "教学资源")
public class ResourceController {

    private static final Logger log = LoggerFactory.getLogger(ResourceController.class);

    private final ResourceService resourceService;
    private final CourseAccessControl courseAccessControl;
    private final ResourceAccessSupport resourceAccessSupport;
    private final FileStorageService fileStorageService;

    public ResourceController(ResourceService resourceService,
                              CourseAccessControl courseAccessControl,
                              ResourceAccessSupport resourceAccessSupport,
                              FileStorageService fileStorageService) {
        this.resourceService = resourceService;
        this.courseAccessControl = courseAccessControl;
        this.resourceAccessSupport = resourceAccessSupport;
        this.fileStorageService = fileStorageService;
    }

    /**
     * 根据课程ID查询关联资源列表
     */
    @GetMapping("/course/{courseId}")
    public ResponseEntity<ApiResponse<List<Resource>>> listByCourse(@PathVariable Long courseId, Authentication auth) {
        if (!resourceAccessSupport.canAccessCourse(courseId, auth)) {
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
        if (!resourceAccessSupport.canAccessResource(resource, auth)) {
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
        if (!resourceAccessSupport.canAccessCourse(courseId, auth)) {
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
            if (auth == null || !(auth.getPrincipal() instanceof Long) || !resourceAccessSupport.isAdmin(auth)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ApiCode.FORBIDDEN, "请指定课程ID进行资源搜索"));
            }
            List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
            Page<Resource> result = resourceService.searchResourcesInCourses(
                    keyword, readableCourseIds, page, size);
            return ResponseEntity.ok(ApiResponse.success(result));
        }
        if (!resourceAccessSupport.canAccessCourse(courseId, auth)) {
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
        if (!resourceAccessSupport.canAccessCourse(courseId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(ApiCode.FORBIDDEN, "无权访问该课程资源"));
        }
        ResourceStatsVO stats = resourceService.getResourceStats(courseId);
        return ResponseEntity.ok(ApiResponse.success(stats));
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
        if (!resourceAccessSupport.canAccessCourse(courseId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(ApiCode.FORBIDDEN, "无权访问该课程资源"));
        }
        List<Resource> resources = resourceService.getResourcesByChapter(courseId, chapterId);
        return ResponseEntity.ok(ApiResponse.success(resources));
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
        if (!resourceAccessSupport.canAccessResource(resource, auth)) {
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

    /**
     * 清洗响应头中的文件名：移除 CR/LF 与引号（防 HTTP 头注入）
     */
    private String sanitizeHeaderValue(String value) {
        if (value == null) {
            return "file";
        }
        return value.replaceAll("[\\r\\n\"]", "_");
    }
}
