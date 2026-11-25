package com.aicourse.controller;

import com.aicourse.application.admin.OperationLogApplicationService;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.vo.OperationLogVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 操作日志查询控制器（管理员权限）
 *
 * <p>3E-05D：筛选条件拼接、院系范围助手与统计口径（3 处 wrapper）已下移到
 * {@link OperationLogApplicationService}。</p>
 */
@Tag(name = "操作审计", description = "操作日志查询与统计")
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class OperationLogController {

    private final OperationLogApplicationService operationLogApplicationService;

    public OperationLogController(OperationLogApplicationService operationLogApplicationService) {
        this.operationLogApplicationService = operationLogApplicationService;
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
    public ApiResponse<PagedResponse<OperationLogVO>> queryLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(
                operationLogApplicationService.queryLogs(page, size, module, action, username, status));
    }

    /**
     * 获取操作日志统计
     * @return 按模块/操作类型统计
     */
    @GetMapping("/operation-logs/statistics")
    public ApiResponse<Map<String, Object>> getStatistics() {
        return ApiResponse.success(operationLogApplicationService.statistics());
    }
}
