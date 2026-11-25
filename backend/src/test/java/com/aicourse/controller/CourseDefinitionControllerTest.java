package com.aicourse.controller;

import com.aicourse.entity.CourseDefinition;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.CourseDefinitionService;
import com.aicourse.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CourseDefinitionController 接口测试：专项回归「入参收实体」加固。
 *
 * <p>课程库创建／更新改为 {@code CourseDefinitionSaveDTO} 后，请求体里的
 * {@code id}／{@code createdBy}／{@code createdAt}／{@code updatedAt} 这些服务端字段必须被挡掉
 * ——否则教师可直接指定主键写入脏行、或伪造课程归属人。</p>
 */
@WebMvcTest(CourseDefinitionController.class)
class CourseDefinitionControllerTest extends ControllerTestBase {

    @MockitoBean
    private CourseDefinitionService definitionService;
    @MockitoBean
    private AdminScopeGuard adminScopeGuard;
    @MockitoBean
    private UserService userService;

    private CourseDefinition existingDefinition() {
        CourseDefinition definition = new CourseDefinition();
        definition.setId(1L);
        definition.setCode("CS101");
        definition.setName("计算机网络");
        definition.setCollegeId(1L);
        definition.setCreatedBy(7L);
        return definition;
    }

    @Test
    @DisplayName("POST /course-definitions - 注入 id/createdBy/createdAt 一律忽略，只落业务字段")
    void create_ignoresInjectedServerFields() throws Exception {
        when(definitionService.create(any(), any(), anyBoolean())).thenReturn(existingDefinition());
        String body = "{\"id\":777,\"createdBy\":888,\"createdAt\":\"2026-01-01T00:00:00\","
                + "\"updatedAt\":\"2026-01-01T00:00:00\",\"code\":\"CS101\",\"name\":\"计算机网络\","
                + "\"credits\":3,\"hours\":48,\"status\":\"ACTIVE\"}";
        mockMvc.perform(post("/course-definitions").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(withUser(7L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<CourseDefinition> captor = ArgumentCaptor.forClass(CourseDefinition.class);
        verify(definitionService).create(captor.capture(), eq(7L), eq(false));
        CourseDefinition created = captor.getValue();
        assertNull(created.getId(), "主键不能被请求体指定");
        assertNull(created.getCreatedBy(), "创建人由 service 按登录身份填写");
        assertNull(created.getCreatedAt(), "创建时间由填充器生成");
        assertNull(created.getUpdatedAt(), "更新时间由填充器生成");
        assertEquals("CS101", created.getCode());
        assertEquals("计算机网络", created.getName());
        assertEquals("ACTIVE", created.getStatus());
    }

    @Test
    @DisplayName("POST /course-definitions - 非法状态返回400（状态域收敛为 ACTIVE/INACTIVE）")
    void create_invalidStatus_returns400() throws Exception {
        mockMvc.perform(post("/course-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CS101\",\"name\":\"网络\",\"status\":\"DELETED\"}")
                        .with(withUser(7L, "TEACHER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /course-definitions/1 - 注入 id/createdBy 一律忽略，只落业务字段（null 表示不改）")
    void update_ignoresInjectedServerFields() throws Exception {
        when(definitionService.getById(1L)).thenReturn(existingDefinition());
        when(definitionService.update(any(), any(), any(), anyBoolean())).thenReturn(existingDefinition());
        String body = "{\"id\":777,\"createdBy\":888,\"name\":\"计算机网络（新版）\",\"status\":\"inactive\"}";
        mockMvc.perform(put("/course-definitions/1").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(withUser(7L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<CourseDefinition> captor = ArgumentCaptor.forClass(CourseDefinition.class);
        verify(definitionService).update(eq(1L), captor.capture(), eq(7L), eq(false));
        CourseDefinition patch = captor.getValue();
        assertNull(patch.getId(), "主键恒取路径变量");
        assertNull(patch.getCreatedBy(), "创建人不可被请求体改写");
        assertEquals("计算机网络（新版）", patch.getName());
        assertEquals("inactive", patch.getStatus(), "状态原样交给 service 归一化");
        assertNull(patch.getCode(), "未传字段保持 null，即「不改」");
    }
}
