package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Course;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.aicourse.exception.BusinessException;

import java.util.Map;

@RestController
@RequestMapping("/enrollments")
public class CourseEnrollmentController {
    private final CourseEnrollmentService enrollmentService;
    private final CourseService courseService;

    public CourseEnrollmentController(CourseEnrollmentService enrollmentService, CourseService courseService) {
        this.enrollmentService = enrollmentService;
        this.courseService = courseService;
    }

    @PostMapping
    public ApiResponse<CourseEnrollment> enroll(Authentication auth, @RequestBody Map<String, Long> body) {
        Long studentId = (Long) auth.getPrincipal();
        Long courseId = body.get("courseId");

        // Check duplicate enrollment
        long existing = enrollmentService.count(new LambdaQueryWrapper<CourseEnrollment>()
            .eq(CourseEnrollment::getStudentId, studentId)
            .eq(CourseEnrollment::getCourseId, courseId)
            .eq(CourseEnrollment::getIsActive, true));
        if (existing > 0) {
            throw new BusinessException("您已报名该课程");
        }

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(courseId);

        // Determine approval status based on course enrollmentType
        Course course = courseService.getById(courseId);
        String enrollmentType = course != null ? course.getEnrollmentType() : "OPEN";
        if ("APPROVAL".equals(enrollmentType) || "RESTRICTED".equals(enrollmentType)) {
            enrollment.setApprovalStatus("PENDING");
        } else {
            enrollment.setApprovalStatus("APPROVED");
        }
        enrollment.setIsActive(true);
        enrollment.setProgress(java.math.BigDecimal.ZERO);
        enrollmentService.save(enrollment);
        return ApiResponse.success("报名成功", enrollment);
    }

    @GetMapping("/my")
    public ApiResponse<PagedResponse<CourseEnrollment>> myEnrollments(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(PagedResponse.of(enrollmentService.page(new Page<>(1, 100), new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getStudentId, userId))));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> unenroll(@PathVariable Long id, Authentication auth) {
        CourseEnrollment enrollment = enrollmentService.getById(id);
        if (enrollment != null && enrollment.getStudentId().equals((Long) auth.getPrincipal())) {
            enrollment.setIsActive(false);
            enrollmentService.updateById(enrollment);
        } else {
            throw new BusinessException("您没有权限退课或该退课记录不存在");
        }
        return ApiResponse.success("退课成功", null);
    }

    @PostMapping("/{id}/approve")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<CourseEnrollment> approve(@PathVariable Long id, Authentication auth) {
        CourseEnrollment enrollment = enrollmentService.getById(id);
        if (enrollment == null) {
            throw new BusinessException("选课记录不存在");
        }
        enrollment.setApprovalStatus("APPROVED");
        enrollment.setApprovedBy((Long) auth.getPrincipal());
        enrollment.setApprovalTime(java.time.LocalDateTime.now());
        enrollment.setIsActive(true);
        enrollmentService.updateById(enrollment);
        return ApiResponse.success("审核通过", enrollment);
    }

    @PostMapping("/{id}/reject")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<CourseEnrollment> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, Authentication auth) {
        CourseEnrollment enrollment = enrollmentService.getById(id);
        if (enrollment == null) {
            throw new BusinessException("选课记录不存在");
        }
        enrollment.setApprovalStatus("REJECTED");
        enrollment.setApprovedBy((Long) auth.getPrincipal());
        enrollment.setApprovalTime(java.time.LocalDateTime.now());
        if (body != null && body.get("reason") != null) {
            enrollment.setApprovalComment(body.get("reason"));
        }
        enrollmentService.updateById(enrollment);
        return ApiResponse.success("已拒绝", enrollment);
    }
}
