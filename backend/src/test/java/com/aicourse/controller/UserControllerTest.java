package com.aicourse.controller;
import com.aicourse.controller.UserController;
import com.aicourse.service.UserService;
import com.aicourse.entity.User;
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
 * UserController 接口测试（升级版）。
 * 原生成器弱断言（status != 401 && status != 403）已全部替换为真实业务断言：
 * 对依赖的 UserService 方法打桩返回合理业务对象，断言 200/code=200，并校验关键字段透传、verify 路由委托。
 * 保留 3 个手写业务断言（密码加密 / 缺失字段 400 / 越权赋值防护）不动。
 * 对带有 @PreAuthorize("hasRole('ADMIN')") 的端点新增 STUDENT 越权 403 测试。
 */
@WebMvcTest(UserController.class)
class UserControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.UserService userService;

    private User sampleUser(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setFullName("测试用户");
        u.setRole("STUDENT");
        return u;
    }

    @Test
    @DisplayName("GET /users - 返回200且分页用户列表透传")
    void list_authorized_returns_200() throws Exception {
        User u = sampleUser(1L, "zhangsan");
        Page<User> p = new Page<>(1, 1);
        p.setRecords(List.of(u));
        p.setTotal(1);
        when(userService.searchUsers(eq("test"), eq("test"), eq(1), eq(1))).thenReturn(p);
        mockMvc.perform(get("/users?page=1&size=1&role=test&keyword=test").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].username").value("zhangsan"));
        verify(userService).searchUsers(eq("test"), eq("test"), eq(1), eq(1));
    }


    @Test
    @DisplayName("GET /users - STUDENT 越权返回403")
    void list_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/users?page=1&size=1&role=test&keyword=test").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /users?page=1&size=1&role=test&keyword=test - 未认证返回401")
    void list_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/users?page=1&size=1&role=test&keyword=test"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /users/1 - 返回200且用户详情透传")
    void getById_authorized_returns_200() throws Exception {
        when(userService.getById(1L)).thenReturn(sampleUser(1L, "zhangsan"));
        mockMvc.perform(get("/users/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("zhangsan"));
        verify(userService).getById(eq(1L));
    }


    @Test
    @DisplayName("GET /users/1 - 未认证返回401")
    void getById_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/users/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /users/1/profile - 返回200且用户资料透传")
    void getProfile_authorized_returns_200() throws Exception {
        when(userService.getUserProfile(1L)).thenReturn(sampleUser(1L, "lisi"));
        mockMvc.perform(get("/users/1/profile").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(userService).getUserProfile(eq(1L));
    }


    @Test
    @DisplayName("GET /users/1/profile - 未认证返回401")
    void getProfile_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/users/1/profile"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /users/1/classmates - 返回200且同学列表透传")
    void getClassmates_authorized_returns_200() throws Exception {
        when(userService.getClassmates(1L)).thenReturn(List.of(sampleUser(1L, "wangwu")));
        mockMvc.perform(get("/users/1/classmates").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(userService).getClassmates(eq(1L));
    }


    @Test
    @DisplayName("GET /users/1/classmates - 未认证返回401")
    void getClassmates_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/users/1/classmates"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /users/teacher/students - 返回200且教师学生列表透传")
    void getTeacherStudents_authorized_returns_200() throws Exception {
        when(userService.getTeacherStudents(1L)).thenReturn(List.of(sampleUser(7L, "studentA")));
        mockMvc.perform(get("/users/teacher/students").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(7));
        verify(userService).getTeacherStudents(eq(1L));
    }


    @Test
    @DisplayName("GET /users/teacher/students - 未认证返回401")
    void getTeacherStudents_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/users/teacher/students"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /users/1/stats - 返回200且用户统计透传")
    void getUserStats_authorized_returns_200() throws Exception {
        when(userService.getUserStats(1L)).thenReturn(Map.of("taskCount", 5, "avgScore", 88));
        mockMvc.perform(get("/users/1/stats").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.taskCount").value(5));
        verify(userService).getUserStats(eq(1L));
    }


    @Test
    @DisplayName("GET /users/1/stats - 未认证返回401")
    void getUserStats_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/users/1/stats"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /users/2/profile - 非本人（STUDENT访问他人）返回403")
    void getProfile_otherUser_forbidden_403() throws Exception {
        mockMvc.perform(get("/users/2/profile").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /users/2/classmates - 非本人（STUDENT访问他人）返回403")
    void getClassmates_otherUser_forbidden_403() throws Exception {
        mockMvc.perform(get("/users/2/classmates").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /users/2/stats - 非本人（STUDENT访问他人）返回403")
    void getUserStats_otherUser_forbidden_403() throws Exception {
        mockMvc.perform(get("/users/2/stats").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /users/teacher/students - 纯STUDENT角色返回403")
    void getTeacherStudents_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/users/teacher/students").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /users/search - 返回200且分页搜索结果透传")
    void search_authorized_returns_200() throws Exception {
        User u = sampleUser(2L, "zhaoliu");
        Page<User> p = new Page<>(1, 1);
        p.setRecords(List.of(u));
        p.setTotal(1);
        when(userService.searchUsers(eq("test"), eq("test"), eq(1), eq(1))).thenReturn(p);
        mockMvc.perform(get("/users/search?keyword=test&role=test&page=1&size=1").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].username").value("zhaoliu"));
        verify(userService).searchUsers(eq("test"), eq("test"), eq(1), eq(1));
    }


    @Test
    @DisplayName("GET /users/search - STUDENT 越权返回403")
    void search_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/users/search?keyword=test&role=test&page=1&size=1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /users/search?keyword=test&role=test&page=1&size=1 - 未认证返回401")
    void search_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/users/search?keyword=test&role=test&page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /users - 返回200且创建用户透传")
    void create_authorized_returns_200() throws Exception {
        when(passwordEncoder.encode("Password123")).thenReturn("BCrypt__encoded");
        when(userService.save(any())).thenReturn(true);
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"zhangsan\",\"fullName\":\"张三\",\"email\":\"z@e.com\",\"role\":\"STUDENT\",\"password\":\"Password123\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.username").value("zhangsan"));
        verify(userService).save(any(User.class));
    }


    @Test
    @DisplayName("POST /users - STUDENT 越权返回403")
    void create_student_forbidden_403() throws Exception {
        // 必须用合法请求体：@Valid 参数绑定先于 @PreAuthorize 方法级 AOP，空体会先触发400而非403
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"zhangsan\",\"fullName\":\"张三\",\"email\":\"z@e.com\",\"role\":\"STUDENT\",\"password\":\"Password123\"}")
                        .with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /users - 未认证返回401")
    void create_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("PUT /users/1 - 返回200且更新后用户透传")
    void update_authorized_returns_200() throws Exception {
        User existing = sampleUser(1L, "orig");
        when(userService.getById(any())).thenReturn(existing);
        when(userService.updateById(any())).thenReturn(true);
        mockMvc.perform(put("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"新名字\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(userService).updateById(any(User.class));
    }


    @Test
    @DisplayName("PUT /users/1 - STUDENT 越权返回403")
    void update_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/users/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("PUT /users/1 - 未认证返回401")
    void update_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/users/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("DELETE /users/1 - 返回200且删除委托被调用")
    void delete_authorized_returns_200() throws Exception {
        when(userService.removeById(1L)).thenReturn(true);
        mockMvc.perform(delete("/users/1").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(userService).removeById(eq(1L));
    }


    @Test
    @DisplayName("DELETE /users/1 - STUDENT 越权返回403")
    void delete_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/users/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("DELETE /users/1 - 未认证返回401")
    void delete_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/users/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("PATCH /users/1/status - 返回200且状态更新委托被调用")
    void toggleStatus_authorized_returns_200() throws Exception {
        User existing = sampleUser(1L, "orig");
        when(userService.getById(any())).thenReturn(existing);
        when(userService.updateById(any())).thenReturn(true);
        mockMvc.perform(patch("/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":true}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(userService).updateById(any(User.class));
    }


    @Test
    @DisplayName("PATCH /users/1/status - STUDENT 越权返回403")
    void toggleStatus_student_forbidden_403() throws Exception {
        mockMvc.perform(patch("/users/1/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":true}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("PATCH /users/1/status - 未认证返回401")
    void toggleStatus_unauthorized_returns_401() throws Exception {
        mockMvc.perform(patch("/users/1/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":true}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /users/profile - 返回200且当前用户资料透传")
    void profile_authorized_returns_200() throws Exception {
        when(userService.getUserProfile(1L)).thenReturn(sampleUser(1L, "me"));
        mockMvc.perform(get("/users/profile").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(userService).getUserProfile(eq(1L));
    }


    @Test
    @DisplayName("GET /users/profile - 未认证返回401")
    void profile_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/users/profile"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("PUT /users/profile - 返回200且更新后资料透传")
    void updateProfile_authorized_returns_200() throws Exception {
        User updated = sampleUser(1L, "me");
        updated.setFullName("李四");
        when(userService.getUserProfile(1L)).thenReturn(updated);
        mockMvc.perform(put("/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"李四\",\"email\":\"l@e.com\"}")
                        .with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.fullName").value("李四"));
        verify(userService).updateProfile(eq(1L), any(User.class));
        verify(userService).getUserProfile(eq(1L));
    }


    @Test
    @DisplayName("PUT /users/profile - 未认证返回401")
    void updateProfile_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/users/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /users - 合法管理员创建返回200且密码经BCrypt加密（非明文存储）")
    void create_valid_encodes_password() throws Exception {
        when(passwordEncoder.encode("Password123")).thenReturn("BCrypt__encoded");
        // 在 save 调用瞬间捕获实体（控制器随后会把返回对象的 password 置空以脱敏，
        // 故不能在方法结束后通过引用读取 password）。
        final String[] capturedPwd = {null};
        when(userService.save(any())).thenAnswer(inv -> {
            capturedPwd[0] = ((com.aicourse.entity.User) inv.getArgument(0)).getPassword();
            return true;
        });
        when(userService.getById(any())).thenReturn(new com.aicourse.entity.User());

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"zhangsan\",\"fullName\":\"张三\",\"email\":\"z@e.com\",\"role\":\"STUDENT\",\"password\":\"Password123\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals("BCrypt__encoded", capturedPwd[0],
                "密码必须以密文存储，禁止明文写入");
    }

    @Test
    @DisplayName("POST /users - 缺少必填字段（用户名/密码/角色）应返回400")
    void create_missing_required_returns_400() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"张三\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /users/1 - 请求体注入 password/id 等敏感字段应被忽略（越权赋值防护）")
    void update_mass_assignment_blocked() throws Exception {
        com.aicourse.entity.User existing = new com.aicourse.entity.User();
        existing.setId(1L);
        existing.setUsername("orig");
        when(userService.getById(any())).thenReturn(existing);
        when(userService.updateById(any())).thenReturn(true);

        mockMvc.perform(put("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hacked\",\"password\":\"PLAINTEXT\",\"id\":999,\"role\":\"ADMIN\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<com.aicourse.entity.User> captor =
                org.mockito.ArgumentCaptor.forClass(com.aicourse.entity.User.class);
        verify(userService).updateById(captor.capture());
        com.aicourse.entity.User saved = captor.getValue();
        org.junit.jupiter.api.Assertions.assertNull(saved.getPassword(),
                "请求体中的 password 不应被写入实体（防止明文改密）");
        org.junit.jupiter.api.Assertions.assertEquals(1L, saved.getId(),
                "主键必须以 URL 路径为准，忽略请求体注入的 id");
    }

}
