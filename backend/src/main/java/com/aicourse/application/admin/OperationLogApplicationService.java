package com.aicourse.application.admin;

import com.aicourse.common.PagedResponse;
import com.aicourse.entity.OperationLog;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.OperationLogService;
import com.aicourse.vo.OperationLogVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 操作日志查询应用服务（3E-05D）。
 *
 * <p>从 {@code OperationLogController} 下移 3 处 {@code LambdaQueryWrapper}、院系范围拼接
 * 与统计口径。</p>
 *
 * <p><b>口径说明（保留原实现，未改行为）</b>：两个端点都先 {@code assertSchoolOnly()}，
 * 院系管理员直接 403；因此紧随其后的 {@code currentCollegeUserIds()} 分支在<b>可达路径上恒为
 * null</code>。这段代码是 8A（WP9）之前的「本院可见」降级残留，属于死分支。[登记] 是否物理删除
 * 待定（删掉即少一条口径，若将来放开院系审计需重写；保留则是一条不可达的防御）。</p>
 */
@Service
public class OperationLogApplicationService {

    private final OperationLogService operationLogService;
    private final AdminScopeGuard adminScopeGuard;

    public OperationLogApplicationService(OperationLogService operationLogService,
                                          AdminScopeGuard adminScopeGuard) {
        this.operationLogService = operationLogService;
        this.adminScopeGuard = adminScopeGuard;
    }

    /** 分页查询操作日志（模块 / 操作类型 / 状态 / 用户名筛选）。 */
    public PagedResponse<OperationLogVO> queryLogs(int page, int size, String module, String action,
                                                   String username, String status) {
        // 8A（WP9）：审计对院系管理员先 403，校级原口径不变——不再做「本院可见」降级
        adminScopeGuard.assertSchoolOnly();
        LambdaQueryWrapper<OperationLog> qw = new LambdaQueryWrapper<OperationLog>()
                .eq(!isBlank(module), OperationLog::getModule, module)
                .eq(!isBlank(action), OperationLog::getAction, action)
                .eq(!isBlank(status), OperationLog::getStatus, status)
                .like(!isBlank(username), OperationLog::getUsername, username)
                .orderByDesc(OperationLog::getCreatedAt);
        Page<OperationLog> logPage = operationLogService.page(Page.of(page, size), qw);
        return PagedResponse.of(logPage.convert(OperationLogVO::from));
    }

    /** 操作日志统计（总数 / 成功数 / 失败数）。 */
    public Map<String, Object> statistics() {
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
        return Map.of(
                "totalCount", totalCount,
                "successCount", successCount,
                "failedCount", totalCount - successCount);
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

    private static boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }
}
