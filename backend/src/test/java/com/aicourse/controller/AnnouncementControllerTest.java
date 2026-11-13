package com.aicourse.controller;

import com.aicourse.entity.Announcement;
import com.aicourse.security.AdminClassAccessControl;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.TeachingClassAccessControl;
import com.aicourse.service.AnnouncementService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公告控制器切片测试（2026-11-07 全量实现批）。
 *
 * <p>钉三类契约：管理员全量列表（/announcements/admin/all）的状态码与分页透传；
 * 发布的鉴权面（ADMIN 放行 / 匿名 401 / STUDENT 403）；发布动作确实委托 service。</p>
 */
@WebMvcTest(AnnouncementController.class)
class AnnouncementControllerTest extends ControllerTestBase {

    @MockitoBean
    private AnnouncementService announcementService;

    @MockitoBean
    private CourseAccessControl courseAccessControl;

    @MockitoBean
    private TeachingClassAccessControl teachingClassAccessControl;

    @MockitoBean
    private AdminClassAccessControl adminClassAccessControl;

    @Test
    @DisplayName("GET /announcements/admin/all - ADMIN 200，分页透传")
    void listAll_admin_returns_200() throws Exception {
        Page<Announcement> page = new Page<>(1, 10);
        Announcement row = new Announcement();
        row.setId(100L);
        row.setTitle("开学通知");
        row.setCourseId(3L);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(announcementService.page(any(), any())).thenReturn(page);

        mockMvc.perform(get("/announcements/admin/all").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].title").value("开学通知"));
        verify(announcementService).page(any(), any());
    }

    @Test
    @DisplayName("GET /announcements/admin/all - STUDENT 403（管理端治理面）")
    void listAll_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/announcements/admin/all").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
        verify(announcementService, never()).page(any(), any());
    }

    @Test
    @DisplayName("POST /announcements - ADMIN 发课程公告 200 并委托 service（publish 参数透传）")
    void publish_admin_courseTarget_200() throws Exception {
        Announcement published = new Announcement();
        published.setId(100L);
        published.setTitle("开学通知");
        published.setCourseId(3L);
        published.setPublisherRole(Announcement.ROLE_TEACHER);
        when(announcementService.publish(eq(3L), eq(null), eq(null), eq("开学通知"),
                eq("下周开课"), eq(1L), any()))
                .thenReturn(published);

        mockMvc.perform(post("/announcements").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":3,\"title\":\"开学通知\",\"content\":\"下周开课\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(100));
        verify(announcementService).publish(eq(3L), eq(null), eq(null), eq("开学通知"),
                eq("下周开课"), eq(1L), any());
    }

    @Test
    @DisplayName("POST /announcements - STUDENT 403（角色门）")
    void publish_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/announcements").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":3,\"title\":\"t\",\"content\":\"c\"}")
                        .with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
        verify(announcementService, never()).publish(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /announcements - 未认证 401")
    void list_unauthorized_401() throws Exception {
        mockMvc.perform(get("/announcements")).andExpect(status().isUnauthorized());
    }
}
