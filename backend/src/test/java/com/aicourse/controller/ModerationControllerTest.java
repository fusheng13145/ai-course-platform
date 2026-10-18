package com.aicourse.controller;

import com.aicourse.common.PagedResponse;
import com.aicourse.service.ModerationService;
import com.aicourse.vo.BannedAuthorVO;
import com.aicourse.vo.ModerationPostVO;
import com.aicourse.vo.ModerationResourceVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ModerationController 接口测试（角色矩阵 ③ 内容审核与违规处理）。
 *
 * <p>权限面：管理员专属（ADMIN 200 / TEACHER 403 / 匿名 401）。
 * 业务面钉住：动作白名单校验（非法动作 400 且不落 service）、管理层ID透传、解封 404 语义。</p>
 */
@WebMvcTest(ModerationController.class)
class ModerationControllerTest extends ControllerTestBase {

    @MockitoBean
    private ModerationService moderationService;

    private PagedResponse<ModerationPostVO> postPage() {
        ModerationPostVO vo = new ModerationPostVO();
        vo.setId(1L);
        vo.setAuthorName("学生甲");
        vo.setModerationStatus("NORMAL");
        return PagedResponse.of(List.of(vo), 1, 1, 20);
    }

    @Test
    @DisplayName("GET /admin/moderation/discussion-posts - ADMIN 返回 200 且带分页结构")
    void listPosts_admin_200() throws Exception {
        when(moderationService.listPosts(eq(1), eq(20), isNull(), isNull())).thenReturn(postPage());

        mockMvc.perform(get("/admin/moderation/discussion-posts").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].authorName").value("学生甲"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("GET /admin/moderation/discussion-posts - TEACHER 403（审核面管理员专属）")
    void listPosts_teacher_403() throws Exception {
        mockMvc.perform(get("/admin/moderation/discussion-posts").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/moderation/discussion-posts - 匿名 401")
    void listPosts_anonymous_401() throws Exception {
        mockMvc.perform(get("/admin/moderation/discussion-posts")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /admin/moderation/resources - ADMIN 返回 200")
    void listResources_admin_200() throws Exception {
        ModerationResourceVO vo = new ModerationResourceVO();
        vo.setId(5L);
        vo.setFileName("课件.pdf");
        when(moderationService.listResources(anyInt(), anyInt(), any(), any()))
                .thenReturn(PagedResponse.of(List.of(vo), 1, 1, 20));

        mockMvc.perform(get("/admin/moderation/resources").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].fileName").value("课件.pdf"));
    }

    @Test
    @DisplayName("POST 处置：动作与原因透传，管理层ID进入 service")
    void applyPostAction_admin_200() throws Exception {
        when(moderationService.applyPostAction(eq(1L), eq("HIDE"), eq("含广告"), eq(true), eq(1L)))
                .thenReturn(Map.of("id", 1L, "action", "HIDE", "moderationStatus", "HIDDEN", "authorBanned", true));

        mockMvc.perform(post("/admin/moderation/discussion-posts/1/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"HIDE\",\"reason\":\"含广告\",\"banAuthor\":true}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.moderationStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.data.authorBanned").value(true));
        verify(moderationService).applyPostAction(eq(1L), eq("HIDE"), eq("含广告"), eq(true), eq(1L));
    }

    @Test
    @DisplayName("POST 处置：非法动作 → 400 且不得落到 service（白名单在 DTO 上）")
    void applyPostAction_invalidAction_400() throws Exception {
        mockMvc.perform(post("/admin/moderation/discussion-posts/1/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"PURGE\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isBadRequest());
        verify(moderationService, never()).applyPostAction(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("资源处置：DELETE 透传（物理删语义由 service 保证）")
    void applyResourceAction_delete_200() throws Exception {
        when(moderationService.applyResourceAction(eq(5L), eq("DELETE"), isNull(), eq(false), eq(1L)))
                .thenReturn(Map.of("id", 5L, "action", "DELETE", "moderationStatus", "DELETED"));

        mockMvc.perform(post("/admin/moderation/resources/5/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DELETE\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.moderationStatus").value("DELETED"));
    }

    @Test
    @DisplayName("GET /admin/moderation/banned-authors - ADMIN 返回 200")
    void listBannedAuthors_admin_200() throws Exception {
        BannedAuthorVO vo = new BannedAuthorVO();
        vo.setUserId(7L);
        vo.setFullName("学生甲");
        vo.setBanReason("刷屏");
        when(moderationService.listBannedAuthors()).thenReturn(List.of(vo));

        mockMvc.perform(get("/admin/moderation/banned-authors").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].banReason").value("刷屏"));
    }

    @Test
    @DisplayName("封禁作者：管理员ID透传，返回 banned=true 与说明文案")
    void banAuthor_admin_200() throws Exception {
        when(moderationService.banAuthor(eq(7L), eq("刷屏"), eq(1L)))
                .thenReturn(Map.of("userId", 7L, "banned", true));

        mockMvc.perform(post("/admin/moderation/users/7/ban")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"刷屏\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.banned").value(true))
                .andExpect(jsonPath("$.data.message").value("已封禁该账号（立即无法登录）"));
    }

    @Test
    @DisplayName("解除封禁：非经审核封禁 → 404（不静默成功，避免顺手解开别的停用状态）")
    void unbanAuthor_notModerationBan_404() throws Exception {
        when(moderationService.unbanAuthor(7L)).thenReturn(false);
        mockMvc.perform(post("/admin/moderation/users/7/unban").with(withUser(1L, "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("解除封禁：成功 → 200 且 unbanned=true")
    void unbanAuthor_200() throws Exception {
        when(moderationService.unbanAuthor(7L)).thenReturn(true);
        mockMvc.perform(post("/admin/moderation/users/7/unban").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unbanned").value(true));
    }
}
