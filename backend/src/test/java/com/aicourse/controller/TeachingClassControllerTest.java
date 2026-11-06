package com.aicourse.controller;

import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.TeachingClass;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.TeachingClassService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TeachingClassController 鉴权切片测试（V14 / WP4）。
 */
@WebMvcTest(TeachingClassController.class)
class TeachingClassControllerTest extends ControllerTestBase {

    @MockitoBean
    private TeachingClassService teachingClassService;

    @MockitoBean
    private AdminScopeGuard adminScopeGuard;

    @Test
    @DisplayName("未登录访问教学班列表返回 401")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/teaching-classes/course/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("学生访问教学班列表返回 403")
    void studentIsForbidden() throws Exception {
        mockMvc.perform(withUser(get("/teaching-classes/course/1"), 2L, "STUDENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("教师可查询课程下的教学班")
    void teacherCanList() throws Exception {
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(30L);
        when(teachingClassService.listByCourse(1L)).thenReturn(List.of(teachingClass));

        mockMvc.perform(withUser(get("/teaching-classes/course/1"), 100L, "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(30));
    }

    @Test
    @DisplayName("管理员不能代建教学班（写操作仅课程负责人角色）")
    void adminCannotCreate() throws Exception {
        String body = "{\"courseId\":1}";
        mockMvc.perform(withUser(post("/teaching-classes")
                        .contentType(MediaType.APPLICATION_JSON).content(body), 1L, "ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("教师创建教学班返回 200")
    void teacherCanCreate() throws Exception {
        when(adminScopeGuard.currentUserId()).thenReturn(100L);
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(30L);
        when(teachingClassService.create(anyLong(), anyLong(), any(), any(), any(), any()))
                .thenReturn(teachingClass);

        mockMvc.perform(withUser(post("/teaching-classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"name\":\"一班\"}"), 100L, "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(30));
    }

    @Test
    @DisplayName("教师读取名单走服务层 ACL")
    void teacherCanReadMembers() throws Exception {
        when(adminScopeGuard.currentUserId()).thenReturn(100L);
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setId(7L);
        when(teachingClassService.listMembers(30L, 100L)).thenReturn(List.of(enrollment));

        mockMvc.perform(withUser(get("/teaching-classes/30/members"), 100L, "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(7));
    }
}
