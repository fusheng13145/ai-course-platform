package com.aicourse.controller;

import com.aicourse.entity.User;
import com.aicourse.exception.GlobalExceptionHandler;
import com.aicourse.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖率测试：UserController（独立 MockMvc，不加载 Spring 上下文）。
 * 覆盖用户 CRUD、个人资料、脱敏、状态切换等端点的成功与异常/未登录分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserController 覆盖率测试")
class UserControllerCoverageTest {

    @Mock
    private UserService userService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        UserController controller = new UserController(userService, passwordEncoder);
        // 独立 MockMvc 不加载 Spring 上下文，需手动注册全局异常处理器，
        // 否则控制器抛出的 BusinessException 会穿透为 500 而非统一转换为 4xx。
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        User u = makeUser(1L);
        u.setEmail("alice@example.com");
        u.setPhone("13800138000");
        lenient().when(userService.searchUsers(any(), any(), anyInt(), anyInt())).thenReturn(pageOf(u));
        lenient().when(userService.getById(any())).thenReturn(u);
        lenient().when(userService.getUserProfile(any())).thenReturn(u);
        lenient().when(userService.getClassmates(any())).thenReturn(List.of(u));
        lenient().when(userService.getTeacherStudents(any())).thenReturn(List.of(u));
        // 控制器 getUserStats 会对返回的 VO 执行 setEmail(maskEmail(...)) 脱敏（仅非本人访问时）。
        com.aicourse.vo.UserStatsVO statsStub = new com.aicourse.vo.UserStatsVO();
        statsStub.setEmail("alice@example.com");
        lenient().when(userService.getUserStats(any())).thenReturn(statsStub);
        lenient().when(userService.save(any())).thenReturn(true);
        lenient().when(userService.updateById(any())).thenReturn(true);
        lenient().when(userService.removeById(anyLong())).thenReturn(true);
        lenient().doNothing().when(userService).updateProfile(any(), any());
        lenient().when(passwordEncoder.encode(any())).thenReturn("encoded");
    }

    private User makeUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("alice");
        u.setRole("STUDENT");
        return u;
    }

    private <T> Page<T> pageOf(T record) {
        Page<T> p = new Page<>(1, 10);
        p.setRecords(List.of(record));
        p.setTotal(1);
        return p;
    }

    private static RequestPostProcessor withAuth(Long userId, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        Authentication auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        return request -> {
            request.setUserPrincipal(auth);
            if (userId != null) request.setRemoteUser(String.valueOf(userId));
            return request;
        };
    }

    // ===================== 列表 / 查询 =====================

    @Test
    @DisplayName("GET /users 列表 / 搜索 / 详情 / 同学")
    void listAndQuery() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/users")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/users").param("role", "STUDENT").param("keyword", "ali"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/users/search").param("keyword", "ali")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/users/1")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/users/1/classmates")).andExpect(status().isOk());
    }

    // ===================== 资料 / 脱敏 =====================

    @Test
    @DisplayName("GET /users/{id}/profile 本人(不脱敏) / 他人(脱敏) / GET /users/{id}/stats 各脱敏分支")
    void profileAndStats() throws Exception {
        // 本人 -> 不脱敏
        mockMvc.perform(MockMvcRequestBuilders.get("/users/1/profile").with(withAuth(1L, "STUDENT")))
                .andExpect(status().isOk());
        // 他人 -> 脱敏（email + 11位手机）
        mockMvc.perform(MockMvcRequestBuilders.get("/users/1/profile").with(withAuth(2L, "STUDENT")))
                .andExpect(status().isOk());
        // 本人 stats
        mockMvc.perform(MockMvcRequestBuilders.get("/users/1/stats").with(withAuth(1L, "STUDENT")))
                .andExpect(status().isOk());
        // 他人 stats -> 脱敏（email 含@且name长度>1）
        mockMvc.perform(MockMvcRequestBuilders.get("/users/1/stats").with(withAuth(2L, "STUDENT")))
                .andExpect(status().isOk());
        // maskEmail 边界：email 无@ / null / name长度<=1
        com.aicourse.vo.UserStatsVO noAt = new com.aicourse.vo.UserStatsVO();
        noAt.setEmail("noatsign");
        when(userService.getUserStats(any())).thenReturn(noAt);
        mockMvc.perform(MockMvcRequestBuilders.get("/users/1/stats").with(withAuth(2L, "STUDENT")))
                .andExpect(status().isOk());
        // VO 字段默认 null 即对应原 Map 的 null email
        com.aicourse.vo.UserStatsVO nullEmail = new com.aicourse.vo.UserStatsVO();
        when(userService.getUserStats(any())).thenReturn(nullEmail);
        mockMvc.perform(MockMvcRequestBuilders.get("/users/1/stats").with(withAuth(2L, "STUDENT")))
                .andExpect(status().isOk());
        com.aicourse.vo.UserStatsVO normal = new com.aicourse.vo.UserStatsVO();
        normal.setEmail("a@x.com");
        when(userService.getUserStats(any())).thenReturn(normal);
        mockMvc.perform(MockMvcRequestBuilders.get("/users/1/stats").with(withAuth(2L, "STUDENT")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /users/teacher/students 教师身份")
    void teacherStudents() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/users/teacher/students").with(withAuth(1L, "TEACHER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /users/profile（当前用户） / PUT /users/profile 更新")
    void currentProfile() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/users/profile").with(withAuth(1L, "STUDENT")))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.put("/users/profile").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json")
                        .content("{\"fullName\":\"P\",\"email\":\"p@example.com\",\"avatar\":\"http://x/a.png\",\"bio\":\"b\",\"phone\":\"13800138000\"}"))
                .andExpect(status().isOk());
    }

    // ===================== 管理员 CRUD =====================

    @Test
    @DisplayName("POST /users 创建")
    void create() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/users")
                        .contentType("application/json")
                        .content("{\"username\":\"newuser\",\"password\":\"Password123\",\"role\":\"STUDENT\","
                                + "\"isActive\":true,\"fullName\":\"New\",\"email\":\"new@example.com\",\"phone\":\"13800138000\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /users/{id} 更新成功 / 不存在")
    void update() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/users/1")
                        .contentType("application/json")
                        .content("{\"fullName\":\"Upd\",\"email\":\"upd@example.com\",\"role\":\"TEACHER\","
                                + "\"isActive\":true,\"username\":\"upduser\",\"phone\":\"13800138000\","
                                + "\"avatarUrl\":\"http://x/a.png\",\"bio\":\"b\"}"))
                .andExpect(status().isOk());
        when(userService.getById(999L)).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.put("/users/999")
                        .contentType("application/json").content("{\"fullName\":\"Upd\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("DELETE /users/{id}")
    void delete() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/users/1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /users/{id}/status 启用/禁用 / 不存在")
    void toggleStatus() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.patch("/users/1/status")
                        .contentType("application/json").content("{\"status\":true}")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.patch("/users/1/status")
                        .contentType("application/json").content("{\"status\":false}")).andExpect(status().isOk());
        when(userService.getById(999L)).thenReturn(null);
        // 用户不存在 -> BusinessException -> 400
        mockMvc.perform(MockMvcRequestBuilders.patch("/users/999/status")
                        .contentType("application/json").content("{\"status\":true}"))
                .andExpect(status().is4xxClientError());
    }
}
