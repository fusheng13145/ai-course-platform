package com.aicourse.controller;

import jakarta.validation.Valid;

import com.aicourse.common.ApiCode;
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
    public ApiResponse<CourseEnrollment> enroll(Authentication auth, @Valid @RequestBody Map<String, Long> body) {
        Long studentId = (Long) auth.getPrincipal();
        Long courseId = body.get("courseId");
        // 查重/软退课恢复/审批状态判定等业务下沉至服务层（事务化）
        CourseEnrollment enrollment = enrollmentService.enroll(studentId, courseId);
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
        Long userId = (Long) auth.getPrincipal();
        // 归属校验与软退课落库下沉至服务层（事务化）
        enrollmentService.unenroll(id, userId);
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
        assertEnrollmentCourseOwner(enrollment, auth);
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
            @Valid @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        CourseEnrollment enrollment = enrollmentService.getById(id);
        if (enrollment == null) {
            throw new BusinessException("选课记录不存在");
        }
        assertEnrollmentCourseOwner(enrollment, auth);
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

    /**
     * 选课归属校验：管理员放行；教师仅能审批自己课程的选课记录
     */
    private void assertEnrollmentCourseOwner(CourseEnrollment enrollment, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;
        Course course = courseService.getById(enrollment.getCourseId());
        if (course == null) throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        if (!userId.equals(course.getTeacherId())) throw new BusinessException(ApiCode.FORBIDDEN, "无权审批该课程的选课记录");
    }
}