package com.aicourse.application.admin;

import com.aicourse.entity.OperationLog;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.OperationLogService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 3E-05D：{@code OperationLogApplicationService} 用例。
 *
 * <p>锁两条：①两个端点都必须先 {@code assertSchoolOnly()}（院系管理员 403，而不是降级成本院可见）；
 * ②统计口径 成功数 + 失败数 = 总数（失败数由差值推出，不单独查库）。</p>
 */
@ExtendWith(MockitoExtension.class)
class OperationLogApplicationServiceTest {

    @Mock
    private OperationLogService operationLogService;
    @Mock
    private AdminScopeGuard adminScopeGuard;

    @InjectMocks
    private OperationLogApplicationService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OperationLog.class);
    }

    @Test
    @DisplayName("查询日志-先校级校验，否则不查库")
    void queryLogsAssertsSchoolOnlyFirst() {
        org.mockito.Mockito.doThrow(new com.aicourse.exception.BusinessException(
                        com.aicourse.common.ApiCode.FORBIDDEN, "仅校级"))
                .when(adminScopeGuard).assertSchoolOnly();

        org.junit.jupiter.api.Assertions.assertThrows(com.aicourse.exception.BusinessException.class,
                () -> service.queryLogs(1, 15, null, null, null, null));

        verify(operationLogService, never()).page(any(), any());
    }

    @Test
    @DisplayName("查询日志-筛选条件只对非空值生效，结果按分页转 VO")
    void queryLogsAppliesFilters() {
        Page<OperationLog> result = new Page<>(2, 15);
        OperationLog log = new OperationLog();
        log.setModule("COURSE");
        result.setRecords(List.of(log));
        result.setTotal(1);
        when(operationLogService.page(any(), any())).thenReturn(result);

        var page = service.queryLogs(2, 15, "COURSE", "", null, "SUCCESS");

        assertEquals(1, page.getList().size());
        assertEquals(2L, page.getPage());
    }

    @Test
    @DisplayName("统计-校级：total 走 count() 全表，success 带 status=SUCCESS，failed=差值")
    void statisticsSchoolWide() {
        when(adminScopeGuard.currentCollegeUserIds()).thenReturn(null);
        when(operationLogService.count()).thenReturn(10L);
        when(operationLogService.count(ArgumentMatchers.<LambdaQueryWrapper<OperationLog>>any())).thenReturn(7L);

        Map<String, Object> stats = service.statistics();

        assertEquals(10L, stats.get("totalCount"));
        assertEquals(7L, stats.get("successCount"));
        assertEquals(3L, stats.get("failedCount"));
    }

    @Test
    @DisplayName("统计-院系用户集为空：用 userId=-1 假条件，不回落全表")
    void statisticsCollegeEmptyUsesSentinel() {
        when(adminScopeGuard.currentCollegeUserIds()).thenReturn(List.of());
        when(operationLogService.count(ArgumentMatchers.<LambdaQueryWrapper<OperationLog>>any()))
                .thenReturn(0L, 0L);

        Map<String, Object> stats = service.statistics();

        assertEquals(0L, stats.get("totalCount"));
        verify(operationLogService, never()).count();
    }
}
