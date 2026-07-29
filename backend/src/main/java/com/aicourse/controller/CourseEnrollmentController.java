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
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 课程报名记录接口控制器
 */
@RestController
@RequestMapping("/enrollments")
@Tag(name = "选课管理")
public class CourseEnrollmentController {

    private final CourseEnrollmentService enrollmentService;
    private final CourseService courseService;

    public CourseEnrollmentController(CourseEnrollmentService enrollmentService, CourseService courseService) {
        this.enrollmentService = enrollmentService;
        this.courseService = courseService;
    }

    /**
     * 课程报名
     * @param auth 认证信息
     * @param body 请求参数
     * @return 报名记录
     */
    @PostMapping
    public ApiResponse<CourseEnrollment> enroll(Authentication auth, @RequestBody Map<String, Long> body) {
        Long studentId = (Long) auth.getPrincipal();
        Long courseId = body.get("courseId");

        // 校验是否已有有效报名
        LambdaQueryWrapper<CourseEnrollment> existWrapper = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, studentId)
                .eq(CourseEnrollment::getCourseId, courseId)
                .eq(CourseEnrollment::getIsActive, true);
        long existCount = enrollmentService.count(existWrapper);
        if (existCount > 0) {
            throw new BusinessException("您已报名该课程");
        }

        // 检查是否存在软退课记录（is_active=false），若存在则直接恢复
        LambdaQueryWrapper<CourseEnrollment> inactiveWrapper = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, studentId)
                .eq(CourseEnrollment::getCourseId, courseId)
                .eq(CourseEnrollment::getIsActive, false);
        CourseEnrollment inactiveEnrollment = enrollmentService.getOne(inactiveWrapper);
        if (inactiveEnrollment != null) {
            inactiveEnrollment.setIsActive(true);
            inactiveEnrollment.setApprovalStatus("APPROVED");
            inactiveEnrollment.setProgress(BigDecimal.ZERO);
            enrollmentService.updateById(inactiveEnrollment);
            return ApiResponse.success("报名成功", inactiveEnrollment);
        }

        // 组装报名数据
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(courseId);

        // 根据课程报名类型设置审核状态
        Course course = courseService.getById(courseId);
        String enrollmentType = course != null ? course.getEnrollmentType() : "OPEN";
        if ("APPROVAL".equals(enrollmentType) || "RESTRICTED".equals(enrollmentType)) {
            enrollment.setApprovalStatus("PENDING");
        } else {
            enrollment.setApprovalStatus("APPROVED");
        }
        enrollment.setIsActive(true);
        enrollment.setProgress(BigDecimal.ZERO);

        enrollmentService.save(enrollment);
        return ApiResponse.success("报名成功", enrollment);
    }

    /**
     * 查询我的报名记录
     * @param auth 认证信息
     * @return 报名记录分页数据
     */
    @GetMapping("/my")
    public ApiResponse<PagedResponse<CourseEnrollment>> myEnrollments(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<CourseEnrollment> queryWrapper = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, userId);
        Page<CourseEnrollment> enrollmentPage = enrollmentService.page(new Page<>(1, 100), queryWrapper);
        return ApiResponse.success(PagedResponse.of(enrollmentPage));
    }

    /**
     * 退出课程
     * @param id 报名记录ID
     * @param auth 认证信息
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> unenroll(@PathVariable Long id, Authentication auth) {
        CourseEnrollment enrollment = enrollmentService.getById(id);
        Long userId = (Long) auth.getPrincipal();
        if (enrollment == null || !userId.equals(enrollment.getStudentId())) {
            throw new BusinessException("您没有权限退课或该退课记录不存在");
        }
        enrollment.setIsActive(false);
        enrollmentService.updateById(enrollment);
        return ApiResponse.success("退课成功", null);
    }

    /**
     * 报名审核通过（教师/管理员权限）
     * @param id 报名记录ID
     * @param auth 认证信息
     * @return 审核后报名记录
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<CourseEnrollment> approve(@PathVariable Long id, Authentication auth) {
        CourseEnrollment enrollment = enrollmentService.getById(id);
        if (enrollment == null) {
            throw new BusinessException("选课记录不存在");
        }
        Long approverId = (Long) auth.getPrincipal();
        enrollment.setApprovalStatus("APPROVED");
        enrollment.setApprovedBy(approverId);
        enrollment.setApprovalTime(LocalDateTime.now());
        enrollment.setIsActive(true);

        enrollmentService.updateById(enrollment);
        return ApiResponse.success("审核通过", enrollment);
    }

    /**
     * 报名审核驳回（教师/管理员权限）
     * @param id 报名记录ID
     * @param body 驳回原因
     * @param auth 认证信息
     * @return 驳回后报名记录
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<CourseEnrollment> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        CourseEnrollment enrollment = enrollmentService.getById(id);
        if (enrollment == null) {
            throw new BusinessException("选课记录不存在");
        }
        Long approverId = (Long) auth.getPrincipal();
        enrollment.setApprovalStatus("REJECTED");
        enrollment.setApprovedBy(approverId);
        enrollment.setApprovalTime(LocalDateTime.now());

        if (body != null && body.get("reason") != null) {
            enrollment.setApprovalComment(body.get("reason"));
        }
        enrollmentService.updateById(enrollment);
        return ApiResponse.success("已拒绝", enrollment);
    }
}