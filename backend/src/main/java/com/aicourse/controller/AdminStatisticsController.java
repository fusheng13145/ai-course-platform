package com.aicourse.controller;

import com.aicourse.application.admin.AdminStatisticsApplicationService;
import com.aicourse.common.ApiResponse;
import com.aicourse.util.ExcelUtil;
import com.aicourse.vo.CourseStatisticsVO;
import com.aicourse.vo.StatisticsOverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 管理员数据统计接口（S-01 从 {@code SystemAdminController} 拆出）。
 *
 * <p>URL、请求参数、响应体与类级 {@code @PreAuthorize} 全部保持原样；
 * 院系管理员的 {@code AdminScopeGuard#currentCollegeFilter()} 收敛逻辑不变。</p>
 *
 * <p>3E-05A：7 处「if (collegeId == null) 校级口径 else 院系口径」双分支、
 * 20 处 {@code LambdaQueryWrapper} 与报表组装已下移到
 * {@link AdminStatisticsApplicationService}；本类只剩路由与 Excel 下载。</p>
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "系统管理", description = "平台数据统计与报表导出")
public class AdminStatisticsController {

    private final AdminStatisticsApplicationService adminStatisticsApplicationService;

    public AdminStatisticsController(AdminStatisticsApplicationService adminStatisticsApplicationService) {
        this.adminStatisticsApplicationService = adminStatisticsApplicationService;
    }

    // ==================== 数据统计（6个接口） ====================

    /**
     * 获取平台数据概览
     */
    @GetMapping("/admin/statistics/overview")
    public ApiResponse<StatisticsOverviewVO> getStatisticsOverview() {
        return ApiResponse.success(adminStatisticsApplicationService.overview());
    }

    /**
     * 获取用户增长趋势
     */
    @GetMapping("/admin/statistics/user-growth")
    public ApiResponse<List<Map<String, Object>>> getUserGrowth(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(adminStatisticsApplicationService.userGrowth(days));
    }

    /**
     * 获取课程统计
     */
    @GetMapping("/admin/statistics/courses")
    public ApiResponse<CourseStatisticsVO> getCourseStatistics() {
        return ApiResponse.success(adminStatisticsApplicationService.courseStatistics());
    }

    /**
     * 获取AI功能使用统计
     */
    @GetMapping("/admin/statistics/ai-usage")
    public ApiResponse<Map<String, Object>> getAIUsageStatistics() {
        return ApiResponse.success(adminStatisticsApplicationService.aiUsage());
    }

    /**
     * 获取**按课程聚合**的 AI 使用统计（跨课程 AI 统计）。
     *
     * <p>数据源是 {@code ai_call_logs}（2026-10-13 埋点上线）——与上方 {@code /ai-usage} 的
     * "无逐次留痕"口径是两回事，两者不可互换、也不可用一个去校正另一个。</p>
     *
     * <p>⚠️ 只统计自埋点上线后的调用，历史数据不可回填；响应内的 {@code collectionNote}
     * 会把这条边界显式带给前端，避免页面自己编一条"历史趋势"。</p>
     *
     * @param days 统计天数（默认 30，1–365 越界收敛）
     */
    @GetMapping("/admin/statistics/ai-usage/by-course")
    public ApiResponse<Map<String, Object>> getAIUsageByCourse(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(adminStatisticsApplicationService.aiUsageByCourse(days));
    }

    /**
     * 按日聚合的 AI 用量（2026-11-07 P2 批次：补齐「按日维度」，B-13④）。
     * 无调用的日期不出行，由前端按区间补零渲染趋势；院系管理员自动收敛到本院口径。
     *
     * @param days 统计天数（默认 30，1–365 越界收敛）
     */
    @GetMapping("/admin/statistics/ai-usage/by-day")
    public ApiResponse<Map<String, Object>> getAIUsageByDay(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(adminStatisticsApplicationService.aiUsageByDay(days));
    }

    /**
     * 按能力（调用场景）聚合的 AI 用量（补齐「按能力维度」，B-13④）；
     * scene 取值即 ai_call_logs 埋点的 12 个业务场景，院系管理员自动收敛到本院口径。
     *
     * @param days 统计天数（默认 30，1–365 越界收敛）
     */
    @GetMapping("/admin/statistics/ai-usage/by-scene")
    public ApiResponse<Map<String, Object>> getAIUsageByScene(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(adminStatisticsApplicationService.aiUsageByScene(days));
    }

    /**
     * 导出数据报表
     */
    @Operation(summary = "导出数据报表", description = "导出平台综合数据报表为 Excel(.xlsx) 文件（多 Sheet：平台概览/AI使用统计/课程统计/用户统计等）")
    @GetMapping("/admin/statistics/export")
    public void exportStatisticsReport(HttpServletResponse response) throws IOException {
        ExcelUtil.setDownloadHeaders(response, "平台数据报表.xlsx");
        ExcelUtil.writeReport(response.getOutputStream(), adminStatisticsApplicationService.exportReport());
    }
}
