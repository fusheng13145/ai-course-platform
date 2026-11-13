package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.AdminClass;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.AdvisorService;
import com.aicourse.vo.AdvisorClassProfileVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 班主任画像接口（B-12／P-08 WP9，交接文档 §5.6 的 /advisor 前端 API 面）。
 * <p>
 * 6A：画像只含聚合值（人数/均值/预警数），不含学生个人成绩字段；
 * {@code advisor_teacher_id} 入参在本控制器不存在——班级归属一律取当前登录用户
 * 或经管理员作用域校验（验收 1：查他人班 403 的实现载体）。</p>
 */
@Tag(name = "班主任画像")
@RestController
@RequestMapping("/advisor")
public class AdvisorController {

    private final AdvisorService advisorService;
    private final AdminScopeGuard adminScopeGuard;

    public AdvisorController(AdvisorService advisorService, AdminScopeGuard adminScopeGuard) {
        this.advisorService = advisorService;
        this.adminScopeGuard = adminScopeGuard;
    }

    /** 当前教师担任班主任的行政班列表。 */
    @GetMapping("/classes")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<AdminClass>> myClasses(Authentication auth) {
        return ApiResponse.success(advisorService.listMyClasses((Long) auth.getPrincipal()));
    }

    /** 班级聚合画像：班主任本人或管理员按学院作用域。 */
    @GetMapping("/classes/{id}/profile")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<AdvisorClassProfileVO> profile(@PathVariable Long id, Authentication auth) {
        boolean adminCaller = adminScopeGuard.isSchoolAdmin() || adminScopeGuard.isCollegeAdmin();
        return ApiResponse.success(
                advisorService.classProfile(id, (Long) auth.getPrincipal(), adminCaller));
    }

    /**
     * 学生预警明细（交接文档 §5.6「学生预警摘要」，6A 允许面；2026-11-07 全量实现批三）。
     */
    @GetMapping("/classes/{id}/alerts")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<java.util.Map<String, Object>>> alerts(@PathVariable Long id, Authentication auth) {
        boolean adminCaller = adminScopeGuard.isSchoolAdmin() || adminScopeGuard.isCollegeAdmin();
        return ApiResponse.success(
                advisorService.classAlerts(id, (Long) auth.getPrincipal(), adminCaller));
    }
}
