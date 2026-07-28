package com.aicourse.controller;
import com.aicourse.controller.SystemConfigController;
import com.aicourse.service.EmailService;
import com.aicourse.service.SystemConfigService;
import com.aicourse.service.UserService;
import com.aicourse.entity.SystemConfig;
import com.aicourse.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * SystemConfigController 接口测试（升级版）。
 * 原生成器弱断言（status != 401 && status != 403）已全部替换为真实业务断言：
 * 对依赖的 SystemConfigService / UserService / EmailService 方法打桩返回合理对象，断言 200/code=200，
 * 并校验关键字段透传、verify 路由委托。
 * 对带有 @PreAuthorize("hasRole('ADMIN')") 的端点新增 STUDENT 越权 403 测试。
 */
@WebMvcTest(SystemConfigController.class)
class SystemConfigControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.SystemConfigService systemConfigService;
    @MockitoBean
    private com.aicourse.service.EmailService emailService;
    @MockitoBean
    private com.aicourse.service.UserService userService;

    private SystemConfig sampleConfig(Long id, String key, String value) {
        SystemConfig c = new SystemConfig();
        c.setId(id);
        c.setConfigKey(key);
        c.setConfigValue(value);
        c.setConfigGroup("GENERAL");
        return c;
    }

    @Test
    @DisplayName("GET /system-configs - 返回200且配置列表透传")
    void list_authorized_returns_200() throws Exception {
        when(systemConfigService.list()).thenReturn(List.of(sampleConfig(1L, "site.name", "AI")));
        mockMvc.perform(get("/system-configs").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].configKey").value("site.name"));
        verify(systemConfigService).list();
    }


    @Test
    @DisplayName("GET /system-configs - STUDENT 越权返回403")
    void list_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/system-configs").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /system-configs - 未认证返回401")
    void list_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/system-configs"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /system-configs/group/1 - 返回200且分组配置透传")
    void getByGroup_authorized_returns_200() throws Exception {
        when(systemConfigService.getConfigsByGroup("1")).thenReturn(List.of(sampleConfig(1L, "a", "v")));
        mockMvc.perform(get("/system-configs/group/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].configKey").value("a"));
        verify(systemConfigService).getConfigsByGroup(eq("1"));
    }


    @Test
    @DisplayName("GET /system-configs/group/1 - 未认证返回401")
    void getByGroup_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/system-configs/group/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /system-configs/public - 返回200且公开配置透传")
    void getPublic_authorized_returns_200() throws Exception {
        when(systemConfigService.getPublicConfigs()).thenReturn(List.of(sampleConfig(2L, "public.key", "pv")));
        mockMvc.perform(get("/system-configs/public").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].configKey").value("public.key"));
        verify(systemConfigService).getPublicConfigs();
    }


    @Test
    @DisplayName("GET /system-configs/public - 未认证返回401")
    void getPublic_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/system-configs/public"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /system-configs/key/1 - 返回200且配置值透传")
    void getValue_authorized_returns_200() throws Exception {
        when(systemConfigService.getConfigValue("1")).thenReturn("value1");
        mockMvc.perform(get("/system-configs/key/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("value1"));
        verify(systemConfigService).getConfigValue(eq("1"));
    }


    @Test
    @DisplayName("GET /system-configs/key/1 - 未认证返回401")
    void getValue_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/system-configs/key/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("PUT /system-configs/key/1 - 返回200且配置更新委托被调用")
    void updateByKey_authorized_returns_200() throws Exception {
        mockMvc.perform(put("/system-configs/key/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"newval\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(systemConfigService).updateConfig(eq("1"), eq("newval"));
    }


    @Test
    @DisplayName("PUT /system-configs/key/1 - STUDENT 越权返回403")
    void updateByKey_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/system-configs/key/1").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"newval\"}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("PUT /system-configs/key/1 - 未认证返回401")
    void updateByKey_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/system-configs/key/1").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"newval\"}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /system-configs/batch-update - 返回200且批量更新委托被调用")
    void batchUpdate_authorized_returns_200() throws Exception {
        mockMvc.perform(post("/system-configs/batch-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key1\":\"v1\",\"key2\":\"v2\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(systemConfigService).batchUpdateConfigs(any());
    }


    @Test
    @DisplayName("POST /system-configs/batch-update - STUDENT 越权返回403")
    void batchUpdate_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/system-configs/batch-update").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /system-configs/batch-update - 未认证返回401")
    void batchUpdate_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/system-configs/batch-update").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /system-configs/search?keyword=test - 返回200且搜索结果透传")
    void search_authorized_returns_200() throws Exception {
        when(systemConfigService.searchConfigs("test")).thenReturn(List.of(sampleConfig(3L, "test.key", "tv")));
        mockMvc.perform(get("/system-configs/search?keyword=test").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].configKey").value("test.key"));
        verify(systemConfigService).searchConfigs(eq("test"));
    }


    @Test
    @DisplayName("GET /system-configs/search?keyword=test - 未认证返回401")
    void search_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/system-configs/search?keyword=test"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /system-configs/test-email - 返回200且测试邮件发送委托被调用")
    void testEmail_authorized_returns_200() throws Exception {
        User u = new User();
        u.setId(1L);
        u.setEmail("admin@aicourse.com");
        when(userService.getById(1L)).thenReturn(u);
        mockMvc.perform(post("/system-configs/test-email").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(userService).getById(eq(1L));
        verify(emailService).sendVerificationCode(eq("admin@aicourse.com"), eq("888888"), eq("TEST"));
    }


    @Test
    @DisplayName("POST /system-configs/test-email - STUDENT 越权返回403")
    void testEmail_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/system-configs/test-email").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /system-configs/test-email - 未认证返回401")
    void testEmail_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/system-configs/test-email"))
                .andExpect(status().isUnauthorized());
    }

}
