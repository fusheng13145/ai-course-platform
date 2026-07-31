package com.aicourse.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.OperationLog;
import com.aicourse.service.OperationLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志查询控制器（管理员权限）
 */
@Tag(name = "操作审计", description = "操作日志查询与统计")
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
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
        long totalCount = operationLogService.count();
        long successCount = operationLogService.count(
                new LambdaQueryWrapper<OperationLog>().eq(OperationLog::getStatus, "SUCCESS"));
        long failedCount = totalCount - successCount;
        return ApiResponse.success(java.util.Map.of(
                "totalCount", totalCount,
                "successCount", successCount,
                "failedCount", failedCount
        ));
    }
}
