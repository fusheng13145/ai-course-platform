package com.aicourse.controller;

import com.aicourse.entity.AiGenerationRestriction;
import com.aicourse.entity.User;
import com.aicourse.service.AiGenerationRestrictionService;
import com.aicourse.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AiGovernanceController 接口测试（角色矩阵 ④ 修订：逐人限制名单）。
 *
 * <p>权限面：管理员专属（ADMIN 200 / TEACHER 403 / 匿名 401）。
 * 业务面钉住三条：① 只能限制**教师**（对其他角色 400，不静默接受）；
 * ② 账号不存在 404；③ 解除不在名单内的账号返回 404（不静默成功）。</p>
 */
@WebMvcTest(AiGovernanceController.class)
class AiGovernanceControllerTest extends ControllerTestBase {

    @MockitoBean
    private AiGenerationRestrictionService restrictionService;
    @MockitoBean
    private UserService userService;

    private User user(Long id, String role, String username, String fullName) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setUsername(username);
        u.setFullName(fullName);
        return u;
    }

    private AiGenerationRestriction restriction(Long userId, String reason) {
        AiGenerationRestriction r = new AiGenerationRestriction();
        r.setId(1L);
        r.setUserId(userId);
        r.setReason(reason);
        r.setRestrictedBy(1L);
        r.setCreatedAt(LocalDateTime.of(2026, 10, 13, 12, 0));
        return r;
    }

    @Test
    @DisplayName("GET /admin/ai-governance/restrictions - ADMIN 返回 200，且带上被限制教师的登录名与姓名")
    void list_admin_200() throws Exception {
        when(restrictionService.listAll()).thenReturn(List.of(restriction(7L, "重复出题")));
        when(userService.listByIds(any())).thenReturn(List.of(user(7L, "TEACHER", "t001", "张老师")));

        mockMvc.perform(get("/admin/ai-governance/restrictions").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].userId").value(7))
                .andExpect(jsonPath("$.data[0].username").value("t001"))
                .andExpect(jsonPath("$.data[0].fullName").value("张老师"))
                .andExpect(jsonPath("$.data[0].reason").value("重复出题"));
    }

    @Test
    @DisplayName("GET /admin/ai-governance/restrictions - TEACHER 403（管理员专属）")
    void list_teacher_403() throws Exception {
        mockMvc.perform(get("/admin/ai-governance/restrictions").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/ai-governance/restrictions - 匿名 401")
    void list_anonymous_401() throws Exception {
        mockMvc.perform(get("/admin/ai-governance/restrictions")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /admin/ai-governance/restrictions - 限制教师 200，操作管理员ID留痕")
    void restrict_teacher_200() throws Exception {
        when(userService.getById(7L)).thenReturn(user(7L, "TEACHER", "t001", "张老师"));
        when(restrictionService.restrict(eq(7L), eq("重复出题"), eq(1L))).thenReturn(restriction(7L, "重复出题"));

        mockMvc.perform(post("/admin/ai-governance/restrictions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"reason\":\"重复出题\"}").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.fullName").value("张老师"));
        verify(restrictionService).restrict(eq(7L), eq("重复出题"), eq(1L));
    }

    @Test
    @DisplayName("POST /admin/ai-governance/restrictions - 目标非教师 → 400（不静默接受无意义限制）")
    void restrict_nonTeacher_400() throws Exception {
        when(userService.getById(3L)).thenReturn(user(3L, "ADMIN", "admin", "管理员"));
        mockMvc.perform(post("/admin/ai-governance/restrictions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":3,\"reason\":\"x\"}").with(withUser(1L, "ADMIN")))
                .andExpect(status().isBadRequest());
        verify(restrictionService, never()).restrict(any(), any(), any());
    }

    @Test
    @DisplayName("POST /admin/ai-governance/restrictions - 账号不存在 → 404")
    void restrict_unknownUser_404() throws Exception {
        when(userService.getById(99L)).thenReturn(null);
        mockMvc.perform(post("/admin/ai-governance/restrictions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":99}").with(withUser(1L, "ADMIN")))
                .andExpect(status().isNotFound());
        verify(restrictionService, never()).restrict(any(), any(), any());
    }

    @Test
    @DisplayName("POST /admin/ai-governance/restrictions - 缺 userId → 400（DTO 校验生效）")
    void restrict_missingUserId_400() throws Exception {
        mockMvc.perform(post("/admin/ai-governance/restrictions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}").with(withUser(1L, "ADMIN")))
                .andExpect(status().isBadRequest());
        verify(restrictionService, never()).restrict(any(), any(), any());
    }

    @Test
    @DisplayName("DELETE /admin/ai-governance/restrictions/{userId} - 在名单内 → 200 且 removed=true")
    void unrestrict_200() throws Exception {
        when(restrictionService.unrestrict(7L)).thenReturn(true);
        mockMvc.perform(delete("/admin/ai-governance/restrictions/7").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.removed").value(true));
    }

    @Test
    @DisplayName("DELETE /admin/ai-governance/restrictions/{userId} - 不在名单内 → 404（不静默成功）")
    void unrestrict_notInList_404() throws Exception {
        when(restrictionService.unrestrict(7L)).thenReturn(false);
        mockMvc.perform(delete("/admin/ai-governance/restrictions/7").with(withUser(1L, "ADMIN")))
                .andExpect(status().isNotFound());
        verify(restrictionService).unrestrict(anyLong());
    }
}
