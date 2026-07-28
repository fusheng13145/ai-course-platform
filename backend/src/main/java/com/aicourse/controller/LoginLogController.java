package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.LoginLog;
import com.aicourse.service.LoginLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 登录日志管理接口控制器
 * 提供登录历史、统计、异常检测、趋势分析等RESTful API
 */
@RestController
@RequestMapping("/login-logs")
@Tag(name = "登录日志")
public class LoginLogController {

    private final LoginLogService loginLogService;

    public LoginLogController(LoginLogService loginLogService) {
        this.loginLogService = loginLogService;
    }

    /**
     * 获取用户登录历史（分页）
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 分页登录日志列表
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN') or #userId == authentication.principal")
    public ApiResponse<PagedResponse<LoginLog>> getUserLoginHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<LoginLog> result = loginLogService.getUserLoginHistory(userId, page, size);
        return ApiResponse.success(PagedResponse.of(result));
    }

    /**
     * 获取用户登录统计数据
     * @param userId 用户ID
     * @return 统计信息（成功/失败次数、最后登录时间等）
     */
    @GetMapping("/user/{userId}/stats")
    @PreAuthorize("hasAnyRole('ADMIN') or #userId == authentication.principal")
    public ApiResponse<Map<String, Object>> getLoginStats(@PathVariable Long userId) {
        Map<String, Object> stats = loginLogService.getLoginStats(userId);
        return ApiResponse.success(stats);
    }

    /**
     * 获取异常登录记录（频繁失败IP）
     * @param userId 用户ID
     * @return 可疑登录记录列表
     */
    @GetMapping("/suspicious/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN') or #userId == authentication.principal")
    public ApiResponse<List<LoginLog>> getSuspiciousLogins(@PathVariable Long userId) {
        List<LoginLog> suspiciousLogs = loginLogService.detectSuspiciousLogins(userId);
        return ApiResponse.success(suspiciousLogs);
    }

    /**
     * 获取登录日志列表（支持分页、状态筛选）
     * @param page 页码
     * @param size 每页大小
     * @param status 登录状态筛选（可选）
     * @param userId 用户ID筛选（可选）
     * @return 分页登录日志列表
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PagedResponse<LoginLog>> getList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId) {
        Page<LoginLog> result = loginLogService.getLoginLogList(page, size, status, userId);
        return ApiResponse.success(PagedResponse.of(result));
    }

    /**
     * 获取全局登录统计数据
     * @return 统计信息（总登录次数、成功/失败次数等）
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> getGlobalStats() {
        Map<String, Object> stats = loginLogService.getGlobalLoginStats();
        return ApiResponse.success(stats);
    }

    /**
     * 获取最近登录记录
     * @param limit 返回条数上限
     * @return 最近登录记录列表
     */
    @GetMapping("/recent")
    public ApiResponse<List<LoginLog>> getRecentLogs(
            @RequestParam(defaultValue = "10") int limit) {
        List<LoginLog> recentLogs = loginLogService.getRecentLogs(limit);
        return ApiResponse.success(recentLogs);
    }

    /**
     * 获取失败登录记录（支持分页和用户筛选）
     * @param page 页码
     * @param size 每页大小
     * @param userId 用户ID筛选（可选）
     * @return 失败登录记录分页列表
     */
    @GetMapping("/failures")
    @PreAuthorize("hasAnyRole('ADMIN') or hasRole('TEACHER')")
    public ApiResponse<PagedResponse<LoginLog>> getFailureLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long userId) {
        Page<LoginLog> result = loginLogService.getFailureLogs(page, size, userId);
        return ApiResponse.success(PagedResponse.of(result));
    }

    /**
     * 异常登录检测（同账号短时间内多地登录 → 异常标记）
     * @param userId 用户ID
     * @return 异常检测结果
     */
    @GetMapping("/abnormal/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN') or #userId == authentication.principal")
    public ApiResponse<Map<String, Object>> getAbnormalLogin(@PathVariable Long userId) {
        Map<String, Object> result = loginLogService.detectAbnormalLogin(userId);
        return ApiResponse.success(result);
    }

    /**
     * 获取登录趋势数据（按日期统计）
     * 支持两种模式：传入 days=N 自动计算日期范围，或传入 start+end 指定范围
     * @param days 最近N天（与start/end二选一）
     * @param start 开始日期
     * @param end 结束日期
     * @return 每日登录统计数据列表
     */
    @GetMapping("/trends")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> getLoginTrends(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        // 优先使用 days 参数计算日期范围
        if (days != null && days > 0) {
            end = LocalDate.now();
            start = end.minusDays(days - 1);
        } else if (start == null || end == null) {
            // 默认近7天
            end = LocalDate.now();
            start = end.minusDays(6);
        }
        List<Map<String, Object>> trends = loginLogService.getLoginTrends(start, end);
        return ApiResponse.success(trends);
    }
}
