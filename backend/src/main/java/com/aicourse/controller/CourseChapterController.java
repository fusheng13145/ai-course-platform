package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.dto.ChapterReorderDTO;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseService;
import com.aicourse.service.TeacherCourseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 课程章节接口控制器
 */
@RestController
@Tag(name = "课程章节")
public class CourseChapterController {

    private final CourseChapterService chapterService;
    private final CourseService courseService;
    private final TeacherCourseService teachingService;

    public CourseChapterController(CourseChapterService chapterService, CourseService courseService,
                                    TeacherCourseService teachingService) {
        this.chapterService = chapterService;
        this.courseService = courseService;
        this.teachingService = teachingService;
    }

    /**
     * 根据课程ID查询章节（完整嵌套树，对齐超星章节任务点）
     * 返回带父子层级、内容、发布状态的章节树，前端可直接渲染目录。
     * @param courseId 课程ID
     * @return 章节树
     */
    @GetMapping("/chapters/course/{courseId}")
    public ApiResponse<List<Map<String, Object>>> getByCourse(@PathVariable Long courseId) {
        return ApiResponse.success(teachingService.getFullChapterTree(courseId));
    }

    /**
     * 课程章节查询备用接口
     * @param courseId 课程ID
     * @return 章节树
     */
    @GetMapping("/courses/{courseId}/chapters")
    public ApiResponse<List<Map<String, Object>>> getByCourseAlt(@PathVariable Long courseId) {
        return ApiResponse.success(teachingService.getFullChapterTree(courseId));
    }

    /**
     * 根据ID查询章节详情
     * @param id 章节ID
     * @return 章节信息
     */
    @GetMapping("/chapters/{id}")
    public ApiResponse<CourseChapter> getById(@PathVariable Long id) {
        CourseChapter chapter = chapterService.getById(id);
        return ApiResponse.success(chapter);
    }

    /**
     * 新增章节（教师/管理员权限）
     * @param chapter 章节信息
     * @param auth 认证信息
     * @return 新增章节数据
     */
    @PostMapping("/chapters")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<CourseChapter> create(@Valid @RequestBody CourseChapter chapter, Authentication auth) {
        // 课程管理权限校验与落库下沉至服务层（事务化）
        CourseChapter saved = chapterService.createChapter(chapter, (Long) auth.getPrincipal(), isAdmin(auth));
        return ApiResponse.success("创建成功", saved);
    }

    /**
     * 修改章节（教师/管理员权限）
     * @param id 章节ID
     * @param chapter 待更新章节数据
     * @param auth 认证信息
     * @return 更新后章节数据
     */
    @PutMapping("/chapters/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<CourseChapter> update(@PathVariable Long id,
                                             @Valid @RequestBody CourseChapter chapter,
                                             Authentication auth) {
        // 章节存在性/权限校验与更新落库下沉至服务层（事务化）
        CourseChapter updatedChapter = chapterService.updateChapter(id, chapter, (Long) auth.getPrincipal(), isAdmin(auth));
        return ApiResponse.success("更新成功", updatedChapter);
    }

    /**
     * 删除章节（教师/管理员权限）
     * @param id 章节ID
     * @param auth 认证信息
     * @return 操作结果
     */
    @DeleteMapping("/chapters/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        // 章节存在性/权限校验与删除落库下沉至服务层（事务化）
        chapterService.deleteChapter(id, (Long) auth.getPrincipal(), isAdmin(auth));
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 批量更新章节排序
     * @param body orderList: [{id: 1, orderIndex: 0}, {id: 2, orderIndex: 1}, ...]
     * @param auth 认证信息
     * @return 操作结果
     */
    @PutMapping("/batch-reorder")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> batchReorder(@Valid @RequestBody ChapterReorderDTO dto, Authentication auth) {
        for (ChapterReorderDTO.OrderItem item : dto.getOrderList()) {
            CourseChapter chapter = chapterService.getById(item.getId());
            if (chapter == null) continue;
            if (canManageCourse(chapter.getCourseId(), auth)) continue;
            chapter.setOrderIndex(item.getOrderIndex());
            chapterService.updateById(chapter);
        }
        return ApiResponse.success("排序更新成功", null);
    }

    /**
     * 判断当前用户是否为管理员
     */
    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * 校验当前用户是否有权限管理对应课程
     * @param courseId 课程ID
     * @param auth 认证信息
     * @return true-有权限，false-无权限
     */
    private boolean canManageCourse(Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return false;
        }
        Course course = courseService.getById(courseId);
        return course == null || !userId.equals(course.getTeacherId());
    }
}