package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.TeacherAccessGuard;
import com.aicourse.service.CourseApprovalService;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseService;
import com.aicourse.service.TeacherCourseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 教师端课程建设与学情驾驶舱接口。
 *
 * <p>2026-11-18 S-01 拆分：原 887 行统一控制器按域拆为课程、作业、题库、导入、学情五个控制器；
 * 本类只保留课程 CRUD、章节 CRUD、课程驾驶舱与课程聚合指标。所有路径仍以 {@code /teacher} 为前缀，
 * 方法级权限继承类级 {@code @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")}。</p>
 */
@RestController
@RequestMapping("/teacher")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
@Tag(name = "教师端课程与驾驶舱接口", description = "课程建设/章节/驾驶舱/课程聚合指标")
public class TeacherController {

    private final CourseService courseService;
    private final CourseApprovalService courseApprovalService;
    private final CourseChapterService chapterService;
    private final TeacherCourseService teachingService;
    private final TeacherAccessGuard teacherAccessGuard;

    public TeacherController(CourseService courseService,
                             CourseApprovalService courseApprovalService,
                             CourseChapterService chapterService,
                             TeacherCourseService teachingService,
                             TeacherAccessGuard teacherAccessGuard) {
        this.courseService = courseService;
        this.courseApprovalService = courseApprovalService;
        this.chapterService = chapterService;
        this.teachingService = teachingService;
        this.teacherAccessGuard = teacherAccessGuard;
    }

    // ==================== 课程管理（已加归属校验） ====================

    /** 创建课程并绑定当前教师 */
    @PostMapping("/courses")
    @Operation(summary = "创建课程")
    public ApiResponse<Course> createCourse(@Valid @RequestBody Course course, Authentication auth) {
        Long teacherId = (Long) auth.getPrincipal();
        courseApprovalService.applyOwnerCollege(course, teacherId);
        Course saved = courseService.createCourse(course, teacherId);
        courseApprovalService.recordSubmission(saved.getId(), teacherId);
        return ApiResponse.success("创建成功", saved);
    }

    /** 分页获取当前教师拥有的课程列表 */
    @GetMapping("/courses")
    @Operation(summary = "获取我的课程列表")
    public ApiResponse<PagedResponse<Course>> getCourses(
            Authentication auth,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Long userId = (Long) auth.getPrincipal();
        Page<Course> coursePage = courseService.page(Page.of(page, size),
                new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, userId));
        return ApiResponse.success(PagedResponse.of(coursePage));
    }

    /** 获取课程详情（需课程归属校验） */
    @GetMapping("/courses/{courseId}")
    @Operation(summary = "获取课程详情")
    public ApiResponse<Course> getCourseDetail(@PathVariable Long courseId, Authentication auth) {
        teacherAccessGuard.assertCourseOwner(courseId, auth);
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        return ApiResponse.success(course);
    }

    /** 更新课程信息（需课程归属校验） */
    @PutMapping("/courses/{courseId}")
    @Operation(summary = "更新课程")
    public ApiResponse<Course> updateCourse(@PathVariable Long courseId,
                                            @Valid @RequestBody Course course,
                                            Authentication auth) {
        teacherAccessGuard.assertCourseOwner(courseId, auth);
        Course updated = courseService.updateCourseById(courseId, course);
        return ApiResponse.success("更新成功", updated);
    }

    /** 删除课程（需课程归属校验） */
    @DeleteMapping("/courses/{courseId}")
    @Operation(summary = "删除课程")
    public ApiResponse<Void> deleteCourse(@PathVariable Long courseId, Authentication auth) {
        teacherAccessGuard.assertCourseOwner(courseId, auth);
        courseService.deleteCourseById(courseId);
        return ApiResponse.success("删除成功", null);
    }

    /** 为指定课程新增章节（需课程归属校验） */
    @PostMapping("/courses/{courseId}/chapters")
    @Operation(summary = "添加课程章节")
    public ApiResponse<CourseChapter> addChapter(@PathVariable Long courseId,
                                                  @Valid @RequestBody CourseChapter chapter,
                                                  Authentication auth) {
        teacherAccessGuard.assertCourseOwner(courseId, auth);
        chapter.setCourseId(courseId);
        chapterService.save(chapter);
        return ApiResponse.success("章节添加成功", chapter);
    }

    /** 更新课程章节（需课程归属校验） */
    @PutMapping("/courses/{courseId}/chapters/{chapterId}")
    @Operation(summary = "更新课程章节")
    public ApiResponse<CourseChapter> updateChapter(@PathVariable Long courseId,
                                                     @PathVariable Long chapterId,
                                                     @Valid @RequestBody CourseChapter chapter,
                                                     Authentication auth) {
        teacherAccessGuard.assertCourseOwner(courseId, auth);
        teacherAccessGuard.assertChapterBelongsToCourse(courseId, chapterId);
        chapter.setId(chapterId);
        chapter.setCourseId(courseId);
        chapterService.updateById(chapter);
        return ApiResponse.success("章节更新成功", chapterService.getById(chapterId));
    }

    /** 删除课程章节（需课程归属校验） */
    @DeleteMapping("/courses/{courseId}/chapters/{chapterId}")
    @Operation(summary = "删除课程章节")
    public ApiResponse<Void> deleteChapter(@PathVariable Long courseId,
                                            @PathVariable Long chapterId,
                                            Authentication auth) {
        teacherAccessGuard.assertCourseOwner(courseId, auth);
        teacherAccessGuard.assertChapterBelongsToCourse(courseId, chapterId);
        chapterService.removeById(chapterId);
        return ApiResponse.success("章节删除成功", null);
    }

    // ==================== 课程建设 / 学情驾驶舱 ====================

    /** 课程驾驶舱：聚合课程各项教学指标（需课程归属校验） */
    @GetMapping("/courses/{courseId}/dashboard")
    @Operation(summary = "课程驾驶舱", description = "聚合课程概览/内容量/学习指标/待批改/趋势/预警学生/章节树/AI解读")
    public ApiResponse<Map<String, Object>> courseDashboard(
            @Parameter(description = "课程ID") @PathVariable Long courseId,
            Authentication auth) {
        teacherAccessGuard.assertCourseOwner(courseId, auth);
        Long operatorId = (Long) auth.getPrincipal();
        return ApiResponse.success(teachingService.getCourseTeachingDashboard(courseId, operatorId));
    }

    /** AI 生成建课大纲（章节与任务点建议） */
    @PostMapping("/courses/ai/generate-outline")
    @Operation(summary = "AI 建课大纲", description = "输入主题/分类/难度，由 AI 生成章节与任务点建议大纲")
    public ApiResponse<Map<String, Object>> generateOutline(
            @Parameter(description = "课程主题") @RequestParam String topic,
            @Parameter(description = "课程分类（可选）") @RequestParam(required = false) String category,
            @Parameter(description = "难度等级（可选）") @RequestParam(required = false) String level,
            @Parameter(description = "建议章节数（默认6）") @RequestParam(defaultValue = "6") int chapterCount,
            Authentication auth) {
        Long operatorId = (Long) auth.getPrincipal();
        return ApiResponse.success(teachingService.generateCourseOutlineByAI(topic, category, level, chapterCount, operatorId));
    }

    /** 克隆课程为新的草稿课程（需源课程归属校验） */
    @PostMapping("/courses/{courseId}/clone")
    @Operation(summary = "克隆课程", description = "复制课程元信息与章节结构为新课程（状态置为草稿）")
    public ApiResponse<Map<String, Object>> cloneCourse(
            @Parameter(description = "源课程ID") @PathVariable Long courseId,
            @Parameter(description = "新课程名称") @RequestParam(required = false) String newName,
            Authentication auth) {
        teacherAccessGuard.assertCourseOwner(courseId, auth);
        Long operatorId = (Long) auth.getPrincipal();
        return ApiResponse.success("克隆成功", teachingService.cloneCourse(courseId, newName, operatorId));
    }

    /** 获取教师每门课程的聚合指标摘要 */
    @GetMapping("/courses/summaries")
    @Operation(summary = "我的课程聚合指标", description = "返回教师每门课程的摘要：学生数/平均进度/完成率/待批改")
    public ApiResponse<List<Map<String, Object>>> myCourseSummaries(Authentication auth) {
        Long teacherId = (Long) auth.getPrincipal();
        return ApiResponse.success(teachingService.getMyCourseSummaries(teacherId));
    }

    /** 增强型教师仪表盘（多维下钻与预警）。AI 总览已摘出，见 /dashboard/ai-overview */
    @GetMapping("/dashboard/overview")
    @Operation(summary = "增强教师仪表盘", description = "课程下钻卡片/跨课预警学生/任务点完成概览/平台均值对比；aiOverview 只回 deferred 标记")
    public ApiResponse<Map<String, Object>> teacherDashboard(Authentication auth) {
        Long teacherId = (Long) auth.getPrincipal();
        return ApiResponse.success(teachingService.getTeacherDashboard(teacherId));
    }

    /** 教师 AI 教学总览（独立入口：首页聚合不再同步调用模型，避免被 LLM 阻塞） */
    @GetMapping("/dashboard/ai-overview")
    @Operation(summary = "教师 AI 教学总览", description = "按需获取 AI 学情解读；与 /dashboard/overview 同统计口径，只是额外做一次模型调用")
    public ApiResponse<Map<String, Object>> teacherAiOverview(Authentication auth) {
        Long teacherId = (Long) auth.getPrincipal();
        return ApiResponse.success(teachingService.getTeacherAIOverview(teacherId));
    }

    /** 获取课程完整章节树（需课程归属校验） */
    @GetMapping("/courses/{courseId}/chapters/tree")
    @Operation(summary = "完整章节树", description = "返回带父子嵌套、内容、发布状态的课程章节树")
    public ApiResponse<List<Map<String, Object>>> chapterTree(
            @Parameter(description = "课程ID") @PathVariable Long courseId,
            Authentication auth) {
        teacherAccessGuard.assertCourseOwner(courseId, auth);
        return ApiResponse.success(teachingService.getFullChapterTree(courseId));
    }

    /** 获取课程关联班级聚合信息（需课程归属校验） */
    @GetMapping("/courses/{courseId}/classes")
    @Operation(summary = "课程关联班级", description = "按选课学生所属班级聚合，返回班级学生数/待批改数/课表说明")
    public ApiResponse<List<Map<String, Object>>> courseClasses(
            @Parameter(description = "课程ID") @PathVariable Long courseId,
            Authentication auth) {
        teacherAccessGuard.assertCourseOwner(courseId, auth);
        return ApiResponse.success(teachingService.getCourseClasses(courseId));
    }
}
