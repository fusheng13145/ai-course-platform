package com.aicourse.controller;

import com.aicourse.application.enrollment.CourseEnrollmentApplicationService;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.EnrollmentCancelDTO;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.vo.CourseVOs;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 课程报名记录接口控制器
 *
 * <p>3E-05D：院系收口、关键字学生ID集解析、三端共用归属校验、审核状态机与 3 处 wrapper 已下移到
 * {@link CourseEnrollmentApplicationService}；本类保留报名/退课两条「一行转发」入口与路由。</p>
 */
@RestController
@RequestMapping("/enrollments")
@Tag(name = "选课管理")
public class CourseEnrollmentController {

    private final CourseEnrollmentService enrollmentService;
    private final CourseEnrollmentApplicationService courseEnrollmentApplicationService;

    public CourseEnrollmentController(CourseEnrollmentService enrollmentService,
                                      CourseEnrollmentApplicationService courseEnrollmentApplicationService) {
        this.enrollmentService = enrollmentService;
        this.courseEnrollmentApplicationService = courseEnrollmentApplicationService;
    }

    /**
     * 课程报名（可指定教学班；未指定时自动选中课程下唯一启用的教学班）
     * @param auth 认证信息
     * @param body 请求参数（courseId 必填，teachingClassId 可选）
     * @return 报名记录
     */
    @PostMapping
    public ApiResponse<CourseVOs.EnrollmentVO> enroll(Authentication auth,
                                                     @Valid @RequestBody Map<String, Long> body) {
        Long studentId = (Long) auth.getPrincipal();
        Long courseId = body.get("courseId");
        // 查重/软退课恢复/审批状态判定/教学班容量等业务下沉至服务层（事务化）
        CourseEnrollment enrollment = enrollmentService.enroll(studentId, courseId, body.get("teachingClassId"));
        return ApiResponse.success("报名成功", CourseVOs.EnrollmentVO.from(enrollment));
    }

    /**
     * 全量报名记录分页（2026-11-07 全量实现批）：「选课与名单」管理页的数据源。
     * 管理员治理全平台选课：按审批状态 / 课程 / 学生姓名模糊过滤。
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PagedResponse<CourseVOs.EnrollmentVO>> listAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String keyword,
            Authentication auth) {
        return ApiResponse.success(
                courseEnrollmentApplicationService.listAll(page, size, status, courseId, keyword));
    }

    /**
     * 查询我的报名记录（仅返回审批通过且有效的记录，V14 / WP5）
     * @param auth 认证信息
     * @return 报名记录分页数据
     */
    @GetMapping("/my")
    public ApiResponse<PagedResponse<CourseVOs.EnrollmentVO>> myEnrollments(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(courseEnrollmentApplicationService.myEnrollments(userId));
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
        // 归属校验与取消状态机下沉至服务层（事务化）
        enrollmentService.unenroll(id, userId);
        return ApiResponse.success("退课成功", null);
    }

    /**
     * 取消已通过选课（学生本人 / 课程负责人或教学班主讲 / 管理员）。
     *
     * <p>与 DELETE 的区别：该入口要求显式原因，适用于教师与管理员的管理动作；
     * 学生本人也走同一状态机，保证三端语义一致。</p>
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ApiResponse<CourseVOs.EnrollmentVO> cancel(
            @PathVariable Long id,
            @Valid @RequestBody EnrollmentCancelDTO body,
            Authentication auth) {
        CourseEnrollment cancelled =
                courseEnrollmentApplicationService.cancel(id, body.getReason(), auth);
        return ApiResponse.success("已取消选课", CourseVOs.EnrollmentVO.from(cancelled));
    }

    /**
     * 报名审核通过（教师/管理员权限）
     * @param id 报名记录ID
     * @param auth 认证信息
     * @return 审核后报名记录
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<CourseVOs.EnrollmentVO> approve(@PathVariable Long id, Authentication auth) {
        CourseEnrollment enrollment = courseEnrollmentApplicationService.approve(id, auth);
        return ApiResponse.success("审核通过", CourseVOs.EnrollmentVO.from(enrollment));
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
    public ApiResponse<CourseVOs.EnrollmentVO> reject(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        String reason = body != null ? body.get("reason") : null;
        CourseEnrollment enrollment = courseEnrollmentApplicationService.reject(id, reason, auth);
        return ApiResponse.success("已拒绝", CourseVOs.EnrollmentVO.from(enrollment));
    }
}
