package com.aicourse.controller;

import com.aicourse.service.OrgService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminOrgController 鉴权切片测试（V12 / B-12-P-08 WP1 验收项 4：组织树端点 401/403）。
 */
@WebMvcTest(AdminOrgController.class)
class AdminOrgControllerAclTest extends ControllerTestBase {

    @MockitoBean
    private OrgService orgService;

    @Test
    @DisplayName("未登录访问组织树返回 401")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/org/tree"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("学生访问组织树返回 403")
    void studentIsForbidden() throws Exception {
        mockMvc.perform(withUser(get("/admin/org/tree"), 2L, "STUDENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员访问组织树返回 200")
    void adminCanReadTree() throws Exception {
        when(orgService.getTree()).thenReturn(List.of());

        mockMvc.perform(withUser(get("/admin/org/tree"), 1L, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }
}
