package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.UserService;
import com.aicourse.vo.CourseStatsVO;
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
 * 课程管理接口控制器
 * 提供课程的增删改查、审批、选课管理等RESTful API
 */
@RestController
@RequestMapping("/courses")
@Tag(name = "课程管理", description = "提供课程的新增、查询、修改、删除、审批、选课统计等功能")
public class CourseController {

    private static final Logger log = LoggerFactory.getLogger(CourseController.class);

    private final CourseService courseService;
    private final CourseEnrollmentService enrollmentService;
    private final UserService userService;
    private final AdminScopeGuard adminScopeGuard;

    public CourseController(CourseService courseService, CourseEnrollmentService enrollmentService,
                            UserService userService, AdminScopeGuard adminScopeGuard) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.userService = userService;
        this.adminScopeGuard = adminScopeGuard;
    }

    /**
     * 分页查询课程列表（支持状态、关键词筛选）
     */
    @GetMapping
    @Operation(summary = "分页查询课程列表", description = "支持按关键词搜索和状态筛选的分页查询")
    public ApiResponse<PagedResponse<Course>> list(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "搜索关键词（匹配课程名称）") @RequestParam(required = false) String keyword,
            @Parameter(description = "课程状态筛选") @RequestParam(required = false) String status) {
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<Course>()
                .eq(status != null, Course::getStatus, status)
                .like(keyword != null, Course::getName, keyword)
                .orderByDesc(Course::getCreatedAt);
        applyCollegeFilter(queryWrapper);
        Page<Course> coursePage = courseService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(coursePage));
    }

    /**
     * 根据ID查询课程详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取课程详情", description = "根据课程ID返回完整课程信息")
    public ApiResponse<Course> getById(@Parameter(description = "课程ID") @PathVariable Long id) {
        Course course = courseService.getById(id);
        adminScopeGuard.assertCourseAccess(course);
        return ApiResponse.success(course);
    }

    /**
     * 新增课程（教师/管理员权限）
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "创建新课程", description = "教师或管理员创建新课程，自动关联当前登录用户为授课教师")
    public ApiResponse<Course> create(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "课程信息"
    ) @Valid @RequestBody Course course, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        // 绑定授课教师与落库下沉至服务层（事务化）
        Course saved = courseService.createCourse(course, userId);
        return ApiResponse.success("创建成功", saved);
    }

    /**
     * 修改课程信息（课程创建者/管理员权限）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "更新课程信息", description = "课程创建者或管理员可修改课程信息")
    public ApiResponse<Course> update(@Parameter(description = "课程ID") @PathVariable Long id,
                                      @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                              description = "待更新的课程数据"
                                      ) @Valid @RequestBody Course course,
                                      Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals("ROLE_ADMIN"));
        if (adminScopeGuard.isCollegeAdmin()) {
            Course existing = courseService.getById(id);
            if (existing == null) throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
            adminScopeGuard.assertCourseAccess(existing);
        }
        // 存在性校验/权限校验/更新落库下沉至服务层（事务化）
        Course updatedCourse = courseService.updateCourse(id, course, userId, isAdmin);
        return ApiResponse.success("更新成功", updatedCourse);
    }

    /**
     * 删除课程（教师/管理员权限，教师仅能删除自己创建的课程）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "删除课程", description = "教师或管理员可删除课程（软删除），教师仅能删除自己创建的课程")
    public ApiResponse<Void> delete(@Parameter(description = "课程ID") @PathVariable Long id, Authentication auth) {
        assertCourseOwner(id, auth);
        courseService.deleteCourseById(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 课程审批通过（仅管理员）
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "审批通过课程", description = "管理员审批通过课程申请，将状态设为ACTIVE")
    public ApiResponse<Course> approve(@Parameter(description = "课程ID") @PathVariable Long id) {
        assertCollegeScope(id);
        Course course = courseService.approveCourse(id);
        return ApiResponse.success("审批成功", course);
    }

    /**
     * 切换课程启用/禁用状态（教师/管理员权限，教师仅能操作自己创建的课程）
     */
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "切换课程状态", description = "在ACTIVE和INACTIVE之间切换课程状态，教师仅能操作自己创建的课程")
    public ApiResponse<Course> toggle(@Parameter(description = "课程ID") @PathVariable Long id, Authentication auth) {
        assertCourseOwner(id, auth);
        Course course = courseService.toggleCourseStatus(id);
        return ApiResponse.success("状态切换成功", course);
    }

    /**
     * 课程审批驳回（仅管理员）
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "驳回课程审批", description = "管理员驳回课程申请，可填写驳回原因")
    public ApiResponse<Course> reject(@Parameter(description = "课程ID") @PathVariable Long id,
                                      @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                              description = "驳回原因（可选）"
                                      ) @Valid @RequestBody(required = false) Map<String, String> body) {
        assertCollegeScope(id);
        String reason = body != null ? body.get("reason") : null;
        Course course = courseService.rejectCourse(id, reason);
        return ApiResponse.success("已拒绝", course);
    }

    /**
     * 查询当前用户已报名的课程
     */
    @GetMapping("/enrolled")
    @Operation(summary = "获取我的已报名课程", description = "返回当前登录用户的课程选课记录列表")
    public ApiResponse<PagedResponse<CourseEnrollment>> enrolledCourses(
            Authentication auth,
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size) {
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<CourseEnrollment> queryWrapper = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, userId);
        Page<CourseEnrollment> enrollmentPage = enrollmentService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(enrollmentPage));
    }

    /**
     * 查询当前教师创建的课程
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "获取我创建的课程", description = "返回当前教师用户创建的所有课程列表")
    public ApiResponse<PagedResponse<Course>> myCourses(
            Authentication auth,
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size) {
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, userId);
        Page<Course> coursePage = courseService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(coursePage));
    }

    /**
     * 查询课程下所有报名学生（教师/管理员权限，教师仅能查看自己课程的学生名单）
     */
    @GetMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取课程学生列表", description = "返回指定课程的所有选课学生记录，教师仅能查看自己课程的学生名单")
    public ApiResponse<PagedResponse<CourseEnrollment>> students(
            @Parameter(description = "课程ID") @PathVariable Long id,
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size,
            Authentication auth) {
        assertCourseOwner(id, auth);
        LambdaQueryWrapper<CourseEnrollment> queryWrapper = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getCourseId, id);
        Page<CourseEnrollment> enrollmentPage = enrollmentService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(enrollmentPage));
    }

    /**
     * 获取课程统计数据（仅课程教师或管理员可见）
     */
    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取课程统计数据", description = "返回课程的学生数、待审核数、状态等统计信息")
    public ApiResponse<CourseStatsVO> getStats(@Parameter(description = "课程ID") @PathVariable Long id, Authentication auth) {
        assertCourseOwner(id, auth);
        Course course = courseService.getById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }

        // 已通过正式学员数量
        LambdaQueryWrapper<CourseEnrollment> activeWrapper = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getCourseId, id)
                .eq(CourseEnrollment::getIsActive, true)
                .eq(CourseEnrollment::getApprovalStatus, "APPROVED");
        long totalStudents = enrollmentService.count(activeWrapper);

        // 待审核报名数量
        LambdaQueryWrapper<CourseEnrollment> pendingWrapper = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getCourseId, id)
                .eq(CourseEnrollment::getApprovalStatus, "PENDING");
        long pendingStudents = enrollmentService.count(pendingWrapper);

        return ApiResponse.success(new CourseStatsVO(
                totalStudents, pendingStudents, id,
                course.getName(), course.getStatus(), course.getApprovalStatus()
        ));
    }

    /**
     * 课程归属校验：管理员放行；教师仅能操作自己创建的课程
     */
    private void assertCourseOwner(Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            assertCollegeScope(courseId);
            return;
        }
        Course course = courseService.getById(courseId);
        if (course == null) throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        if (!userId.equals(course.getTeacherId())) throw new BusinessException(ApiCode.FORBIDDEN, "无权操作该课程");
    }

    /**
     * 院系管理员按授课教师学院限制课程访问；校级管理员不做额外限制。
     */
    private void assertCollegeScope(Long courseId) {
        if (!adminScopeGuard.isCollegeAdmin()) {
            return;
        }
        Course course = courseService.getById(courseId);
        if (course == null) throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        adminScopeGuard.assertCourseAccess(course);
    }

    /**
     * 院系管理员只能看到本院教师创建的课程；校级管理员不过滤。
     */
    private void applyCollegeFilter(LambdaQueryWrapper<Course> queryWrapper) {
        Long collegeId = adminScopeGuard.currentCollegeFilter();
        if (collegeId == null) {
            return;
        }
        List<Long> teacherIds = userService.list(new LambdaQueryWrapper<User>()
                        .select(User::getId)
                        .eq(User::getCollegeId, collegeId))
                .stream().map(User::getId).toList();
        if (teacherIds.isEmpty()) {
            queryWrapper.eq(Course::getTeacherId, -1L);
        } else {
            queryWrapper.in(Course::getTeacherId, teacherIds);
        }
    }
}