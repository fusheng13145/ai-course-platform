package com.aicourse.controller;
import com.aicourse.controller.ClassroomController;
import com.aicourse.entity.Classroom;
import com.aicourse.entity.User;
import com.aicourse.service.ClassroomService;
import com.aicourse.vo.ClassroomStatsVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
 * ClassroomController 接口测试（升级版：真实业务断言）。
 * 对每个 endpoint 打桩 service 并断言实体 / Map / 分页字段透传，verify 确认路由委托。
 */
@WebMvcTest(ClassroomController.class)
class ClassroomControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.ClassroomService classroomService;

    @Test
    @DisplayName("GET /classrooms - 班级列表查询返回200，数组与字段透传")
    void list_authorized_passes_security() throws Exception {
        Classroom c = new Classroom();
        c.setId(1L);
        when(classroomService.searchClassrooms(any(), any(), any())).thenReturn(List.of(c));
        mockMvc.perform(get("/classrooms?keyword=test").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(classroomService).searchClassrooms(any(), any(), any());
    }

    @Test
    @DisplayName("GET /classrooms?keyword=test - 未认证返回401")
    void list_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/classrooms?keyword=test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /classrooms/1 - 查询班级详情返回200，ID透传")
    void getById_authorized_passes_security() throws Exception {
        Classroom c = new Classroom();
        c.setId(1L);
        when(classroomService.getById(eq(1L))).thenReturn(c);
        mockMvc.perform(get("/classrooms/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(classroomService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /classrooms/1 - 未认证返回401")
    void getById_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/classrooms/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /classrooms/1/detail - 查询班级详情统计返回200，字段透传")
    void getDetail_authorized_passes_security() throws Exception {
        com.aicourse.entity.Classroom cls = new com.aicourse.entity.Classroom();
        cls.setName("一班");
        when(classroomService.getClassroomDetail(eq(1L))).thenReturn(new com.aicourse.vo.ClassroomDetailVO(cls, 30, 50));
        mockMvc.perform(get("/classrooms/1/detail").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.classroom.name").value("一班"));
        verify(classroomService).getClassroomDetail(eq(1L));
    }

    @Test
    @DisplayName("GET /classrooms/1/detail - 未认证返回401")
    void getDetail_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/classrooms/1/detail"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /classrooms - 管理员创建班级返回200")
    void create_authorized_passes_security() throws Exception {
        mockMvc.perform(post("/classrooms").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"测试班级\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
        verify(classroomService).save(any());
    }

    @Test
    @DisplayName("POST /classrooms - 未认证返回401")
    void create_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/classrooms").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /classrooms - STUDENT角色返回403")
    void create_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/classrooms").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /classrooms/1 - 管理员更新班级返回200，ID透传")
    void update_authorized_passes_security() throws Exception {
        Classroom c = new Classroom();
        c.setId(1L);
        when(classroomService.getById(eq(1L))).thenReturn(c);
        mockMvc.perform(put("/classrooms/1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"测试班级\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(classroomService).updateById(any());
    }

    @Test
    @DisplayName("PUT /classrooms/1 - 未认证返回401")
    void update_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/classrooms/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /classrooms/1 - STUDENT角色返回403")
    void update_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/classrooms/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /classrooms/1 - 管理员删除班级返回200")
    void delete_authorized_passes_security() throws Exception {
        mockMvc.perform(delete("/classrooms/1").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(classroomService).removeById(eq(1L));
    }

    @Test
    @DisplayName("DELETE /classrooms/1 - 未认证返回401")
    void delete_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/classrooms/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /classrooms/1 - STUDENT角色返回403")
    void delete_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/classrooms/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /classrooms/1/students - 查询班级学生返回200，分页数组透传")
    void getStudents_authorized_passes_security() throws Exception {
        User u = new User();
        u.setId(1L);
        Page<User> page = new Page<>();
        page.setRecords(List.of(u));
        page.setTotal(1L);
        when(classroomService.getClassroomStudents(eq(1L), any(), anyInt(), anyInt())).thenReturn(page);
        mockMvc.perform(get("/classrooms/1/students?keyword=test&page=1&size=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].id").value(1));
        verify(classroomService).getClassroomStudents(eq(1L), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("GET /classrooms/1/students?keyword=test&page=1&size=1 - 未认证返回401")
    void getStudents_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/classrooms/1/students?keyword=test&page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /classrooms/1/students/1 - 教师添加学生返回200")
    void addStudent_authorized_passes_security() throws Exception {
        mockMvc.perform(post("/classrooms/1/students/1").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(classroomService).addStudent(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("POST /classrooms/1/students/1 - 未认证返回401")
    void addStudent_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/classrooms/1/students/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /classrooms/1/students/1 - STUDENT角色返回403")
    void addStudent_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/classrooms/1/students/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /classrooms/1/students/1 - 教师移除学生返回200")
    void removeStudent_authorized_passes_security() throws Exception {
        mockMvc.perform(delete("/classrooms/1/students/1").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(classroomService).removeStudent(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("DELETE /classrooms/1/students/1 - 未认证返回401")
    void removeStudent_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/classrooms/1/students/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /classrooms/1/students/1 - STUDENT角色返回403")
    void removeStudent_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/classrooms/1/students/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /classrooms/1/stats - 查询班级统计返回200，字段透传")
    void getStats_authorized_passes_security() throws Exception {
        when(classroomService.getClassroomStats(eq(1L))).thenReturn(
                new ClassroomStatsVO(20, 15, 5, 50, 20, Map.of("计算机", 20L)));
        mockMvc.perform(get("/classrooms/1/stats").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.totalStudents").value(20));
        verify(classroomService).getClassroomStats(eq(1L));
    }

    @Test
    @DisplayName("GET /classrooms/1/stats - 未认证返回401")
    void getStats_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/classrooms/1/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /classrooms/my - 班主任自查：只按当前用户查（WP9 验收 1），旧 advisorId 参数被忽略")
    void getMyClassrooms_authorized_passes_security() throws Exception {
        Classroom c = new Classroom();
        c.setId(1L);
        when(classroomService.getAdvisorClassrooms(eq(1L))).thenReturn(List.of(c));
        // 兼容期旧客户端仍会多带 advisorId=99：参数已被签名移除，判定只认当前用户 1
        mockMvc.perform(get("/classrooms/my?advisorId=99").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(classroomService).getAdvisorClassrooms(eq(1L));
        verify(classroomService, never()).getAdvisorClassrooms(eq(99L));
    }

    @Test
    @DisplayName("GET /classrooms/my?advisorId=1 - 未认证返回401")
    void getMyClassrooms_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/classrooms/my?advisorId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /classrooms/search - 高级搜索班级返回200，数组透传")
    void search_authorized_passes_security() throws Exception {
        Classroom c = new Classroom();
        c.setId(1L);
        when(classroomService.searchClassrooms(any(), any(), any())).thenReturn(List.of(c));
        mockMvc.perform(get("/classrooms/search?keyword=test&gradeLevel=test&major=test").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(classroomService).searchClassrooms(any(), any(), any());
    }

    @Test
    @DisplayName("GET /classrooms/search?keyword=test&gradeLevel=test&major=test - 未认证返回401")
    void search_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/classrooms/search?keyword=test&gradeLevel=test&major=test"))
                .andExpect(status().isUnauthorized());
    }
}
