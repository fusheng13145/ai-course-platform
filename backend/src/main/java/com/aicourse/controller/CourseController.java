package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 课程管理接口控制器
 */
@RestController
@RequestMapping("/courses")
public class CourseController {

    private final CourseService courseService;
    private final CourseEnrollmentService enrollmentService;

    public CourseController(CourseService courseService, CourseEnrollmentService enrollmentService) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
    }

    /**
     * 分页查询课程列表（支持状态、关键词筛选）
     * @param page 当前页码
     * @param size 每页条数
     * @param keyword 搜索关键词
     * @param status 课程状态
     * @return 分页课程数据
     */
    @GetMapping
    public ApiResponse<PagedResponse<Course>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<Course>()
                .eq(status != null, Course::getStatus, status)
                .like(keyword != null, Course::getName, keyword)
                .orderByDesc(Course::getCreatedAt);
        Page<Course> coursePage = courseService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(coursePage));
    }

    /**
     * 根据ID查询课程详情
     * @param id 课程ID
     * @return 课程信息
     */
    @GetMapping("/{id}")
    public ApiResponse<Course> getById(@PathVariable Long id) {
        Course course = courseService.getById(id);
        return ApiResponse.success(course);
    }

    /**
     * 新增课程（教师/管理员权限）
     * @param course 课程信息
     * @param auth 认证信息
     * @return 新增课程数据
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Course> create(@RequestBody Course course, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        course.setTeacherId(userId);
        courseService.save(course);
        return ApiResponse.success("创建成功", course);
    }

    /**
     * 修改课程信息（课程创建者/管理员权限）
     * @param id 课程ID
     * @param course 待更新课程数据
     * @param auth 认证信息
     * @return 更新后课程数据
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Course> update(@PathVariable Long id,
                                      @RequestBody Course course,
                                      Authentication auth) {
        Course existCourse = courseService.getById(id);
        if (existCourse == null) {
            throw new BusinessException("课程不存在");
        }

        Long userId = (Long) auth.getPrincipal();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals("ROLE_ADMIN"));
        if (!userId.equals(existCourse.getTeacherId()) && !isAdmin) {
            throw new BusinessException("无权修改该课程");
        }

        course.setId(id);
        courseService.updateById(course);
        Course updatedCourse = courseService.getById(id);
        return ApiResponse.success("更新成功", updatedCourse);
    }

    /**
     * 删除课程（教师/管理员权限）
     * @param id 课程ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        courseService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 课程审批通过（仅管理员）
     * @param id 课程ID
     * @return 审批后课程信息
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Course> approve(@PathVariable Long id) {
        Course course = courseService.getById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        course.setApprovalStatus("APPROVED");
        course.setStatus("ACTIVE");
        courseService.updateById(course);
        return ApiResponse.success("审批成功", course);
    }

    /**
     * 切换课程启用/禁用状态（教师/管理员权限）
     * @param id 课程ID
     * @return 状态切换后课程信息
     */
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Course> toggle(@PathVariable Long id) {
        Course course = courseService.getById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if ("ACTIVE".equals(course.getStatus())) {
            course.setStatus("INACTIVE");
        } else {
            course.setStatus("ACTIVE");
        }
        courseService.updateById(course);
        return ApiResponse.success("状态切换成功", course);
    }

    /**
     * 课程审批驳回（仅管理员）
     * @param id 课程ID
     * @param body 驳回原因
     * @return 驳回后课程信息
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Course> reject(@PathVariable Long id,
                                      @RequestBody(required = false) Map<String, String> body) {
        Course course = courseService.getById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        course.setApprovalStatus("REJECTED");
        if (body != null && body.get("reason") != null) {
            course.setApprovalComment(body.get("reason"));
        }
        courseService.updateById(course);
        return ApiResponse.success("已拒绝", course);
    }

    /**
     * 查询当前用户已报名的课程
     * @param auth 认证信息
     * @param page 当前页码
     * @param size 每页条数
     * @return 分页报名记录
     */
    @GetMapping("/enrolled")
    public ApiResponse<PagedResponse<CourseEnrollment>> enrolledCourses(
            Authentication auth,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<CourseEnrollment> queryWrapper = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, userId);
        Page<CourseEnrollment> enrollmentPage = enrollmentService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(enrollmentPage));
    }

    /**
     * 查询当前教师创建的课程
     * @param auth 认证信息
     * @param page 当前页码
     * @param size 每页条数
     * @return 分页课程数据
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<PagedResponse<Course>> myCourses(
            Authentication auth,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, userId);
        Page<Course> coursePage = courseService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(coursePage));
    }

    /**
     * 查询课程下所有报名学生
     * @param id 课程ID
     * @param page 当前页码
     * @param size 每页条数
     * @return 分页报名记录
     */
    @GetMapping("/{id}/students")
    public ApiResponse<PagedResponse<CourseEnrollment>> students(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<CourseEnrollment> queryWrapper = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getCourseId, id);
        Page<CourseEnrollment> enrollmentPage = enrollmentService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(enrollmentPage));
    }

    /**
     * 获取课程统计数据
     * @param id 课程ID
     * @return 统计信息
     */
    @GetMapping("/{id}/stats")
    public ApiResponse<Map<String, Object>> getStats(@PathVariable Long id) {
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

        return ApiResponse.success(Map.of(
                "totalStudents", totalStudents,
                "pendingStudents", pendingStudents,
                "courseId", id,
                "courseName", course.getName(),
                "status", course.getStatus(),
                "approvalStatus", course.getApprovalStatus()
        ));
    }
}