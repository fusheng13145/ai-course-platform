package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.OperationLog;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.OperationLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 操作日志查询控制器（管理员权限）
 */
@Tag(name = "操作审计", description = "操作日志查询与统计")
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class OperationLogController {

    private static final Logger log = LoggerFactory.getLogger(OperationLogController.class);

    private final OperationLogService operationLogService;
    private final AdminScopeGuard adminScopeGuard;

    public OperationLogController(OperationLogService operationLogService,
                                  AdminScopeGuard adminScopeGuard) {
        this.operationLogService = operationLogService;
        this.adminScopeGuard = adminScopeGuard;
    }

    /**
     * 分页查询操作日志
     * @param page 页码
     * @param size 每页大小
     * @param module 模块筛选（可选）
     * @param action 操作类型筛选（可选）
     * @param username 用户名筛选（可选）
     * @param status 状态筛选（可选）
     * @return 分页操作日志
     */
    @GetMapping("/operation-logs")
    public ApiResponse<PagedResponse<OperationLog>> queryLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String status) {
        // 8A（WP9）：审计对院系管理员先 403，校级原口径不变——不再做「本院可见」降级
        adminScopeGuard.assertSchoolOnly();
        LambdaQueryWrapper<OperationLog> qw = new LambdaQueryWrapper<OperationLog>()
                .eq(module != null && !module.isEmpty(), OperationLog::getModule, module)
                .eq(action != null && !action.isEmpty(), OperationLog::getAction, action)
                .eq(status != null && !status.isEmpty(), OperationLog::getStatus, status)
                .like(username != null && !username.isEmpty(), OperationLog::getUsername, username)
                .orderByDesc(OperationLog::getCreatedAt);
        Page<OperationLog> logPage = operationLogService.page(Page.of(page, size), qw);
        return ApiResponse.success(PagedResponse.of(logPage));
    }

    /**
     * 获取操作日志统计
     * @return 按模块/操作类型统计
     */
    @GetMapping("/operation-logs/statistics")
    public ApiResponse<java.util.Map<String, Object>> getStatistics() {
        // 8A（WP9）：同上，院系管理员 403
        adminScopeGuard.assertSchoolOnly();
        List<Long> collegeUserIds = adminScopeGuard.currentCollegeUserIds();
        LambdaQueryWrapper<OperationLog> totalWrapper = new LambdaQueryWrapper<>();
        applyCollegeScope(totalWrapper, collegeUserIds);
        // 校级保持原口径（count() 全表统计）；院系按本院用户过滤
        long totalCount = collegeUserIds == null
                ? operationLogService.count()
                : operationLogService.count(totalWrapper);

        LambdaQueryWrapper<OperationLog> successWrapper = new LambdaQueryWrapper<OperationLog>()
                .eq(OperationLog::getStatus, "SUCCESS");
        applyCollegeScope(successWrapper, collegeUserIds);
        long successCount = operationLogService.count(successWrapper);
        long failedCount = totalCount - successCount;
        return ApiResponse.success(java.util.Map.of(
                "totalCount", totalCount,
                "successCount", successCount,
                "failedCount", failedCount
        ));
    }

    private void applyCollegeScope(LambdaQueryWrapper<OperationLog> qw, List<Long> collegeUserIds) {
        if (collegeUserIds == null) {
            return;
        }
        if (collegeUserIds.isEmpty()) {
            qw.eq(OperationLog::getUserId, -1L);
        } else {
            qw.in(OperationLog::getUserId, collegeUserIds);
        }
    }
}
