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

/**
 * 登录日志管理接口控制器
 * 提供登录历史、统计、异常检测、趋势分析等RESTful API
 */
@RestController
@RequestMapping("/login-logs")
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
     * 获取登录趋势数据（按日期统计）
     * @param start 开始日期
     * @param end 结束日期
     * @return 每日登录统计数据列表
     */
    @GetMapping("/trends")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> getLoginTrends(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        List<Map<String, Object>> trends = loginLogService.getLoginTrends(start, end);
        return ApiResponse.success(trends);
    }
}
