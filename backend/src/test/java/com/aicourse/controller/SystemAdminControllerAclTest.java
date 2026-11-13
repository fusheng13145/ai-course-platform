package com.aicourse.controller;

import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.SystemService;
import com.aicourse.service.UserService;
import com.aicourse.security.AdminScopeGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 系统管理控制器（/admin/**，仅 ADMIN）权限矩阵回归测试。
 *
 * <p>覆盖此前审计点名的约 16 个管理端点：backup/restore、configs、logs、statistics 等。
 * 该控制器在 SecurityConfig 中以 {@code /admin/** -> hasRole('ADMIN')} 于过滤器层拦截，
 * 且类上标注 {@code @PreAuthorize("hasRole('ADMIN')")}，故鉴权发生在进入处理方法<b>之前</b>：
 * 未登录 → 401；已登录但非 ADMIN（STUDENT/TEACHER）→ 403。据此<b>无需</b>为负向用例准备合法请求体。</p>
 *
 * <p>另对读取型端点补 ADMIN 成功路径，断言 service 委托与 {@code ApiResponse} 信封，
 * 证明「放行的是真正的管理员」而非把鉴权整个关掉导致的假 200。</p>
 */
@WebMvcTest(SystemAdminController.class)
@Import(AdminScopeGuard.class)
class SystemAdminControllerAclTest extends ControllerTestBase {

    @MockitoBean
    private SystemService systemService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private CourseService courseService;
    @MockitoBean
    private AnalyticsService analyticsService;
    @MockitoBean
    private CourseEnrollmentService enrollmentService;

    /** 受保护管理端点清单（HTTP 方法 + 路径），用于参数化越权矩阵。 */
    static Stream<Arguments> adminEndpoints() {
        return Stream.of(
                Arguments.of("GET", "/admin/users"),
                Arguments.of("GET", "/admin/users/1"),
                Arguments.of("GET", "/admin/configs"),
                Arguments.of("GET", "/admin/logs"),
                Arguments.of("GET", "/admin/health"),
                Arguments.of("GET", "/admin/statistics/overview"),
                Arguments.of("GET", "/admin/statistics/user-growth"),
                Arguments.of("GET", "/admin/statistics/courses"),
                Arguments.of("GET", "/admin/statistics/ai-usage"),
                Arguments.of("GET", "/admin/backup"),
                Arguments.of("GET", "/admin/system/overview"),
                Arguments.of("GET", "/admin/system/configs"),
                Arguments.of("GET", "/admin/system/health"),
                Arguments.of("GET", "/admin/system/logs"),
                Arguments.of("GET", "/admin/system/logs/app.log"),
                Arguments.of("GET", "/admin/ai-monitor"),
                Arguments.of("POST", "/admin/users"),
                Arguments.of("POST", "/admin/users/import"),
                Arguments.of("POST", "/admin/backup"),
                Arguments.of("POST", "/admin/system/configs"),
                Arguments.of("POST", "/admin/system/backup/backup.sql/restore"),
                Arguments.of("PUT", "/admin/users/1"),
                Arguments.of("PUT", "/admin/users/1/reset-password"),
                Arguments.of("PUT", "/admin/configs/site.name"),
                Arguments.of("DELETE", "/admin/users/1")
        );
    }

    private static MockHttpServletRequestBuilder builder(String method, String path) {
        return switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path).contentType(MediaType.APPLICATION_JSON).content("{}");
            case "PUT" -> put(path).contentType(MediaType.APPLICATION_JSON).content("{}");
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException(method);
        };
    }

    @ParameterizedTest(name = "{0} {1} 未登录 -> 401")
    @MethodSource("adminEndpoints")
    @DisplayName("全部管理端点：未登录一律 401")
    void allEndpoints_unauthenticated_401(String method, String path) throws Exception {
        mockMvc.perform(builder(method, path))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} {1} 学生 -> 403")
    @MethodSource("adminEndpoints")
    @DisplayName("全部管理端点：已登录学生一律 403（非 ADMIN）")
    void allEndpoints_student_403(String method, String path) throws Exception {
        mockMvc.perform(builder(method, path).with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0} {1} 教师 -> 403")
    @MethodSource("adminEndpoints")
    @DisplayName("全部管理端点：已登录教师一律 403（教师非管理员）")
    void allEndpoints_teacher_403(String method, String path) throws Exception {
        mockMvc.perform(builder(method, path).with(withUser(3L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    // ===================== ADMIN 成功路径（抽样读取型端点） =====================

    @Test
    @DisplayName("GET /admin/configs - 管理员读取配置列表 200，透传 service 分组参数")
    void configs_admin_ok() throws Exception {
        when(systemService.getConfigs(eq("sys"))).thenReturn(List.of(Map.of("key", "k")));
        mockMvc.perform(get("/admin/configs").param("group", "sys").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].key").value("k"));
        verify(systemService).getConfigs(eq("sys"));
    }

    @Test
    @DisplayName("GET /admin/logs - 管理员读取日志文件列表 200")
    void logs_admin_ok() throws Exception {
        when(systemService.getLogFiles()).thenReturn(List.of(Map.of("name", "app.log")));
        mockMvc.perform(get("/admin/logs").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("app.log"));
        verify(systemService).getLogFiles();
    }

    @Test
    @DisplayName("GET /admin/system/logs/{file} - 读取日志内容 200，委托 readLogFile")
    void readLog_admin_ok() throws Exception {
        when(systemService.readLogFile(eq("app.log"), eq(100), any())).thenReturn("line1\nline2");
        mockMvc.perform(get("/admin/system/logs/app.log").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(systemService).readLogFile(eq("app.log"), eq(100), any());
    }

    @Test
    @DisplayName("POST /admin/backup - 管理员触发备份 200，返回文件名并委托 createBackup")
    void createBackup_admin_ok() throws Exception {
        when(systemService.createBackup()).thenReturn("backup-2026.sql");
        mockMvc.perform(post("/admin/backup").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("backup-2026.sql"));
        verify(systemService).createBackup();
    }

    @Test
    @DisplayName("POST /admin/system/backup/{file}/restore - 管理员恢复备份 200，委托 restoreBackup")
    void restoreBackup_admin_ok() throws Exception {
        mockMvc.perform(post("/admin/system/backup/backup.sql/restore").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(systemService).restoreBackup(eq("backup.sql"));
    }

    @Test
    @DisplayName("GET /admin/statistics/overview - 统计概览 200（计数缺省 0 不得除零/NPE）")
    void statisticsOverview_admin_ok() throws Exception {
        // 全部 count() 未打桩，Mockito 对 long 返回默认 0L —— 校验空数据下的返回形状稳定
        mockMvc.perform(get("/admin/statistics/overview").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalUsers").value(0))
                .andExpect(jsonPath("$.data.totalCourses").value(0));
    }

    @Test
    @DisplayName("GET /admin/statistics/courses - 课程统计 200，无课程数据时分类分布为空 Map")
    void statisticsCourses_admin_ok() throws Exception {
        when(courseService.list()).thenReturn(List.of());
        when(analyticsService.getCourseRanking()).thenReturn(List.of());
        mockMvc.perform(get("/admin/statistics/courses").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCourses").value(0))
                .andExpect(jsonPath("$.data.topCourses").isArray());
    }

    @Test
    @DisplayName("GET /admin/statistics/user-growth?days=7 - 委托趋势统计并透传 days")
    void userGrowth_admin_ok() throws Exception {
        when(analyticsService.getUserGrowthTrend(eq(7))).thenReturn(List.of(Map.of("d", "x")));
        mockMvc.perform(get("/admin/statistics/user-growth").param("days", "7")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].d").value("x"));
        verify(analyticsService).getUserGrowthTrend(eq(7));
    }

    @Test
    @DisplayName("GET /admin/statistics/ai-usage - 委托 getAIUsageStats 200")
    void aiUsage_admin_ok() throws Exception {
        when(systemService.getAIUsageStats()).thenReturn(Map.of("calls", 42));
        mockMvc.perform(get("/admin/statistics/ai-usage").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calls").value(42));
        verify(systemService).getAIUsageStats();
    }

    @Test
    @DisplayName("PUT /admin/configs/{key} - 管理员更新配置 200，委托 saveConfig(key,value,group)")
    void updateConfig_admin_ok() throws Exception {
        mockMvc.perform(put("/admin/configs/site.name").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"AI课堂\",\"group\":\"basic\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(systemService).saveConfig(eq("site.name"), eq("AI课堂"), eq("basic"));
    }
}
