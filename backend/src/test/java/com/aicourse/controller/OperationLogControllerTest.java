package com.aicourse.controller;
import com.aicourse.controller.OperationLogController;
import com.aicourse.service.OperationLogService;
import com.aicourse.entity.OperationLog;
import com.aicourse.entity.User;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * OperationLogController 接口测试（升级版）。
 * 原生成器弱断言（status != 401 && status != 403）已全部替换为真实业务断言：
 * 对依赖的 OperationLogService 方法打桩返回合理业务对象，断言 200/code=200，并校验关键字段透传、verify 路由委托。
 * 该控制器类级别 @PreAuthorize("hasRole('ADMIN')")，原有 STUDENT 越权 403 测试保留不动。
 */
@WebMvcTest(OperationLogController.class)
@Import(AdminScopeGuard.class)
class OperationLogControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.OperationLogService operationLogService;
    @MockitoBean
    private UserService userService;

    private User user(Long id, Long collegeId, String role) {
        User u = new User();
        u.setId(id);
        u.setCollegeId(collegeId);
        u.setRole(role);
        return u;
    }

    private OperationLog sampleLog(Long id) {
        OperationLog log = new OperationLog();
        log.setId(id);
        log.setModule("USER");
        log.setUsername("admin");
        return log;
    }

    @Test
    @DisplayName("GET /admin/operation-logs - 返回200且操作日志分页透传")
    void queryLogs_authorized_returns_200() throws Exception {
        Page<OperationLog> p = new Page<>(1, 1);
        p.setRecords(List.of(sampleLog(1L)));
        p.setTotal(1);
        when(operationLogService.page(any(), any())).thenReturn(p);
        mockMvc.perform(get("/admin/operation-logs?page=1&size=1&module=test&action=test&username=test&status=test").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(1));
        verify(operationLogService).page(any(), any());
    }


    @Test
    @DisplayName("GET /admin/operation-logs?page=1&size=1&module=test&action=test&username=test&status=test - STUDENT角色返回403")
    void queryLogs_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/admin/operation-logs?page=1&size=1&module=test&action=test&username=test&status=test").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /admin/operation-logs?page=1&size=1&module=test&action=test&username=test&status=test - 未认证返回401")
    void queryLogs_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/admin/operation-logs?page=1&size=1&module=test&action=test&username=test&status=test"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /admin/operation-logs/statistics - 返回200且统计透传")
    void getStatistics_authorized_returns_200() throws Exception {
        when(operationLogService.count()).thenReturn(10L);
        when(operationLogService.count(any())).thenReturn(8L);
        mockMvc.perform(get("/admin/operation-logs/statistics").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.totalCount").value(10))
                .andExpect(jsonPath("$.data.successCount").value(8))
                .andExpect(jsonPath("$.data.failedCount").value(2));
        verify(operationLogService).count();
        verify(operationLogService).count(any());
    }


    @Test
    @DisplayName("GET /admin/operation-logs - 院系管理员 403（8A：审计先 403，WP9 收紧）")
    void queryLogs_collegeAdmin_forbidden() throws Exception {
        when(userService.getById(3L)).thenReturn(user(3L, 10L, "ADMIN"));

        mockMvc.perform(get("/admin/operation-logs").with(withCollegeAdmin(3L)))
                .andExpect(status().isForbidden());
        verify(operationLogService, never()).page(any(), any());
    }

    @Test
    @DisplayName("GET /admin/operation-logs/statistics - 院系管理员 403（8A）")
    void getStatistics_collegeAdmin_forbidden() throws Exception {
        when(userService.getById(3L)).thenReturn(user(3L, 10L, "ADMIN"));

        mockMvc.perform(get("/admin/operation-logs/statistics").with(withCollegeAdmin(3L)))
                .andExpect(status().isForbidden());
        verify(operationLogService, never()).count();
        verify(operationLogService, never()).count(any());
    }

    @Test
    @DisplayName("GET /admin/operation-logs/statistics - STUDENT角色返回403")
    void getStatistics_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/admin/operation-logs/statistics").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /admin/operation-logs/statistics - 未认证返回401")
    void getStatistics_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/admin/operation-logs/statistics"))
                .andExpect(status().isUnauthorized());
    }

}
