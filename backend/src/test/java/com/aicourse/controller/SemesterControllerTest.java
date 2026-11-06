package com.aicourse.controller;

import com.aicourse.entity.Semester;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.SemesterService;
import com.aicourse.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
 * SemesterController 接口测试（角色矩阵 ① 创建学期 / ② 归档过期学期）。
 *
 * <p>权限面按"管理员专属"钉住：ADMIN 200、TEACHER 403、匿名 401；
 * 业务面钉住创建入参透传、归档幂等语义透传与批量归档条数透传。</p>
 */
@WebMvcTest(SemesterController.class)
@Import(AdminScopeGuard.class)
class SemesterControllerTest extends ControllerTestBase {

    @MockitoBean
    private SemesterService semesterService;
    @MockitoBean
    private UserService userService;

    private Semester sample() {
        Semester s = new Semester();
        s.setId(1L);
        s.setName("2026-2027-1");
        s.setStatus("ACTIVE");
        return s;
    }

    @Test
    @DisplayName("GET /admin/semesters - ADMIN 返回 200 且 data 为数组")
    void list_admin_200() throws Exception {
        when(semesterService.listByStatus(isNull())).thenReturn(List.of(sample()));
        mockMvc.perform(get("/admin/semesters").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("2026-2027-1"));
    }

    @Test
    @DisplayName("GET /admin/semesters - 匿名 401")
    void list_anonymous_401() throws Exception {
        mockMvc.perform(get("/admin/semesters")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /admin/semesters - TEACHER 403（管理员专属，不被角色门放过）")
    void list_teacher_403() throws Exception {
        mockMvc.perform(get("/admin/semesters").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /admin/semesters - ADMIN 创建学期：入参原样透传（含 ISO 日期解析）")
    void create_admin_200() throws Exception {
        when(semesterService.create(any(), any(), any())).thenReturn(sample());
        mockMvc.perform(post("/admin/semesters").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"2026-2027-1\",\"startDate\":\"2026-09-01\",\"endDate\":\"2027-01-15\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("2026-2027-1"));
        verify(semesterService).create(eq("2026-2027-1"),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2027, 1, 15)));
    }

    @Test
    @DisplayName("POST /admin/semesters - 缺名称 → 400（DTO 校验生效，不得落到 service）")
    void create_missingName_400() throws Exception {
        mockMvc.perform(post("/admin/semesters").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2026-09-01\",\"endDate\":\"2027-01-15\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isBadRequest());
        verify(semesterService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("POST /admin/semesters/{id}/archive - 幂等语义透传（archived 字段如实回传）")
    void archive_admin_200() throws Exception {
        when(semesterService.archive(1L)).thenReturn(false);
        mockMvc.perform(post("/admin/semesters/1/archive").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.archived").value(false));
    }

    @Test
    @DisplayName("POST /admin/semesters/archive-expired - 返回本次归档条数")
    void archiveExpired_admin_200() throws Exception {
        when(semesterService.archiveExpired(isNull())).thenReturn(3);
        mockMvc.perform(post("/admin/semesters/archive-expired").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archivedCount").value(3));
        verify(semesterService).archiveExpired(isNull());
    }

    @Test
    @DisplayName("POST /admin/semesters/archive-expired - TEACHER 403（批量治理不得被教师触发）")
    void archiveExpired_teacher_403() throws Exception {
        mockMvc.perform(post("/admin/semesters/archive-expired").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
        verify(semesterService, never()).archiveExpired(any());
    }

    @Test
    @DisplayName("POST /admin/semesters - 院系管理员 403（学期为校级治理）")
    void create_collegeAdmin_403() throws Exception {
        mockMvc.perform(post("/admin/semesters").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"2026-2027-1\",\"startDate\":\"2026-09-01\",\"endDate\":\"2027-01-15\"}")
                        .with(withCollegeAdmin(3L)))
                .andExpect(status().isForbidden());
        verify(semesterService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("POST /admin/semesters/{id}/archive - 院系管理员 403")
    void archive_collegeAdmin_403() throws Exception {
        mockMvc.perform(post("/admin/semesters/1/archive").with(withCollegeAdmin(3L)))
                .andExpect(status().isForbidden());
        verify(semesterService, never()).archive(any());
    }

    @Test
    @DisplayName("POST /admin/semesters/archive-expired - 院系管理员 403")
    void archiveExpired_collegeAdmin_403() throws Exception {
        mockMvc.perform(post("/admin/semesters/archive-expired").with(withCollegeAdmin(3L)))
                .andExpect(status().isForbidden());
        verify(semesterService, never()).archiveExpired(any());
    }
}
