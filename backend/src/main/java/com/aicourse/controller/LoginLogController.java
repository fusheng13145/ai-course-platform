package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.LoginLog;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.LoginLogService;
import com.aicourse.vo.AbnormalLoginVO;
import com.aicourse.vo.DailyLoginTrendVO;
import com.aicourse.vo.LoginGlobalStatsVO;
import com.aicourse.vo.LoginStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    private static final Logger log = LoggerFactory.getLogger(LoginLogController.class);

    private final LoginLogService loginLogService;
    private final AdminScopeGuard adminScopeGuard;

    public LoginLogController(LoginLogService loginLogService,
                             AdminScopeGuard adminScopeGuard) {
        this.loginLogService = loginLogService;
        this.adminScopeGuard = adminScopeGuard;
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
        adminScopeGuard.assertUserAccessById(userId);
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
    public ApiResponse<LoginStatsVO> getLoginStats(@PathVariable Long userId) {
        adminScopeGuard.assertUserAccessById(userId);
        LoginStatsVO stats = loginLogService.getLoginStats(userId);
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
        adminScopeGuard.assertUserAccessById(userId);
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
        List<Long> collegeUserIds = adminScopeGuard.currentCollegeUserIds();
        Page<LoginLog> result;
        if (collegeUserIds == null) {
            result = loginLogService.getLoginLogList(page, size, status, userId);
        } else {
            if (userId != null) {
                adminScopeGuard.assertUserAccessById(userId);
            }
            LambdaQueryWrapper<LoginLog> qw = new LambdaQueryWrapper<LoginLog>()
                    .eq(userId != null, LoginLog::getUserId, userId)
                    .eq(status != null && !status.isEmpty(), LoginLog::getLoginStatus, status)
                    .orderByDesc(LoginLog::getCreatedAt);
            applyCollegeScope(qw, collegeUserIds);
            result = loginLogService.page(Page.of(page, size), qw);
        }
        return ApiResponse.success(PagedResponse.of(result));
    }

    /**
     * 获取全局登录统计数据
     * @return 统计信息（总登录次数、成功/失败次数等）
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LoginGlobalStatsVO> getGlobalStats() {
        List<Long> collegeUserIds = adminScopeGuard.currentCollegeUserIds();
        LoginGlobalStatsVO stats = collegeUserIds == null
                ? loginLogService.getGlobalLoginStats()
                : loginLogService.getGlobalLoginStats(collegeUserIds);
        return ApiResponse.success(stats);
    }

    /**
     * 获取最近登录记录（修复 #6：为审计/运维端点补齐角色校验）
     *
     * <p>原端点无任何 {@code @PreAuthorize}，且服务层 {@code getRecentLogs} 无 userId 过滤，
     * 导致任意已登录用户可读取全平台他人的 username/ip/browser/device。此处收敛为 ADMIN 专属，
     * 与同文件其余审计端点（list/stats/trends）保持一致；服务层方法因被
     * {@code SysMonitorComponent} 内部复用而保持不变。</p>
     * @param limit 返回条数上限
     * @return 最近登录记录列表
     */
    @GetMapping("/recent")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<LoginLog>> getRecentLogs(
            @RequestParam(defaultValue = "10") int limit) {
        List<Long> collegeUserIds = adminScopeGuard.currentCollegeUserIds();
        List<LoginLog> recentLogs;
        if (collegeUserIds == null) {
            recentLogs = loginLogService.getRecentLogs(limit);
        } else {
            int effectiveLimit = limit <= 0 ? 10 : Math.min(limit, 100);
            LambdaQueryWrapper<LoginLog> qw = new LambdaQueryWrapper<LoginLog>()
                    .orderByDesc(LoginLog::getCreatedAt)
                    .last("LIMIT " + effectiveLimit);
            applyCollegeScope(qw, collegeUserIds);
            recentLogs = loginLogService.list(qw);
        }
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
        List<Long> collegeUserIds = adminScopeGuard.currentCollegeUserIds();
        Page<LoginLog> result;
        if (collegeUserIds == null) {
            result = loginLogService.getFailureLogs(page, size, userId);
        } else {
            if (userId != null) {
                adminScopeGuard.assertUserAccessById(userId);
            }
            LambdaQueryWrapper<LoginLog> qw = new LambdaQueryWrapper<LoginLog>()
                    .eq(LoginLog::getLoginStatus, "FAILED")
                    .eq(userId != null, LoginLog::getUserId, userId)
                    .orderByDesc(LoginLog::getCreatedAt);
            applyCollegeScope(qw, collegeUserIds);
            result = loginLogService.page(Page.of(page, size), qw);
        }
        return ApiResponse.success(PagedResponse.of(result));
    }

    /**
     * 异常登录检测（同账号短时间内多地登录 → 异常标记）
     * @param userId 用户ID
     * @return 异常检测结果
     */
    @GetMapping("/abnormal/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN') or #userId == authentication.principal")
    public ApiResponse<AbnormalLoginVO> getAbnormalLogin(@PathVariable Long userId) {
        adminScopeGuard.assertUserAccessById(userId);
        AbnormalLoginVO result = loginLogService.detectAbnormalLogin(userId);
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
    public ApiResponse<List<DailyLoginTrendVO>> getLoginTrends(
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
        List<Long> collegeUserIds = adminScopeGuard.currentCollegeUserIds();
        List<DailyLoginTrendVO> trends = collegeUserIds == null
                ? loginLogService.getLoginTrends(start, end)
                : loginLogService.getLoginTrends(start, end, collegeUserIds);
        return ApiResponse.success(trends);
    }

    /**
     * 登录日志学院口径：院系管理员仅限本院用户；校级管理员不追加条件。
     * @param qw 查询条件
     * @param collegeUserIds 本院用户ID列表（非 null）
     */
    private void applyCollegeScope(LambdaQueryWrapper<LoginLog> qw, List<Long> collegeUserIds) {
        if (collegeUserIds.isEmpty()) {
            qw.eq(LoginLog::getUserId, -1L);
        } else {
            qw.in(LoginLog::getUserId, collegeUserIds);
        }
    }
}
