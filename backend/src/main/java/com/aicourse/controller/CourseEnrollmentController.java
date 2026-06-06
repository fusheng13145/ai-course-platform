package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.service.CourseEnrollmentService;
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

    public CourseEnrollmentController(CourseEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    public ApiResponse<CourseEnrollment> enroll(Authentication auth, @RequestBody Map<String, Long> body) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudentId((Long) auth.getPrincipal());
        enrollment.setCourseId(body.get("courseId"));
        enrollment.setApprovalStatus("APPROVED");
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
}
