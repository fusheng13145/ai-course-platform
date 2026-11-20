package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.security.TeacherAccessGuard;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.TaskService;
import com.aicourse.vo.TaskCompletionStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 教师端学情分析接口（2026-11-18 从 TeacherController 拆出）。
 *
 * <p>班级/学生/作业/知识点/报告导出共 5 个端点；学生维度沿用教师—学生归属校验。</p>
 */
@RestController
@RequestMapping("/teacher")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
@Tag(name = "教师端学情分析接口", description = "班级/学生/作业/知识点学情与报告导出")
public class TeacherAnalyticsController {

    private final AnalyticsService analyticsService;
    private final TaskService taskService;
    private final TeacherAccessGuard teacherAccessGuard;

    public TeacherAnalyticsController(AnalyticsService analyticsService,
                                      TaskService taskService,
                                      TeacherAccessGuard teacherAccessGuard) {
        this.analyticsService = analyticsService;
        this.taskService = taskService;
        this.teacherAccessGuard = teacherAccessGuard;
    }

    /** 获取班级学情分析（需课程归属校验） */
    @GetMapping("/analytics/class/{courseId}")
    @Operation(summary = "获取班级学情分析")
    public ApiResponse<Map<String, Object>> getClassAnalytics(@PathVariable Long courseId, Authentication auth) {
        teacherAccessGuard.assertCourseOwner(courseId, auth);
        return ApiResponse.success(analyticsService.getCourseAnalysis(courseId));
    }

    /** 获取单个学生的学情分析（需教师—学生归属校验） */
    @GetMapping("/analytics/students/{studentId}")
    @Operation(summary = "获取学生学情分析")
    public ApiResponse<Map<String, Object>> getStudentAnalytics(@PathVariable Long studentId, Authentication auth) {
        teacherAccessGuard.assertTeacherOwnsStudent(studentId, auth);
        return ApiResponse.success(analyticsService.getUserDashboard(studentId));
    }

    /** 获取作业学情分析（需作业归属校验） */
    @GetMapping("/analytics/assignments/{assignmentId}")
    @Operation(summary = "获取作业学情分析")
    public ApiResponse<TaskCompletionStatsVO> getAssignmentAnalytics(@PathVariable Long assignmentId,
                                                                     Authentication auth) {
        teacherAccessGuard.assertTaskOwner(assignmentId, auth);
        return ApiResponse.success(taskService.getTaskCompletionStats(assignmentId));
    }

    /** 获取知识点分析（可按课程筛选） */
    @GetMapping("/analytics/knowledge-points")
    @Operation(summary = "获取知识点分析")
    public ApiResponse<Map<String, Object>> getKnowledgePointAnalytics(
            @RequestParam(required = false) Long courseId, Authentication auth) {
        if (courseId != null) {
            teacherAccessGuard.assertCourseOwner(courseId, auth);
        }
        return ApiResponse.success(analyticsService.getGradeDistribution(courseId));
    }

    /** 导出学情报告（返回报告 JSON，需课程归属校验） */
    @GetMapping("/analytics/report/export")
    @Operation(summary = "导出学情报告", description = "返回学情报告 JSON 数据（含报告各章节内容），前端基于该数据生成 PDF 文件下载")
    public ApiResponse<Map<String, Object>> exportReport(@RequestParam Long courseId, Authentication auth) {
        teacherAccessGuard.assertCourseOwner(courseId, auth);
        return ApiResponse.success("报告导出成功", analyticsService.exportReport(courseId));
    }
}
