package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.exception.GlobalExceptionHandler;
import com.aicourse.entity.Course;
import com.aicourse.entity.User;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.SystemService;
import com.aicourse.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.io.ByteArrayOutputStream;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖率测试：SystemAdminController（独立 MockMvc，不加载 Spring 上下文）。
 * 覆盖全部 27 个端点的成功路径与错误/异常分支（BusinessException、空文件、非 Excel、空数据、导入失败计数）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SystemAdminController 覆盖率测试")
class SystemAdminControllerCoverageTest {

    @Mock
    private SystemService systemService;
    @Mock
    private UserService userService;
    @Mock
    private CourseService courseService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private CourseEnrollmentService enrollmentService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    private static final String VALID_PWD = "Passw0rd!";

    @BeforeEach
    void setup() {
        SystemAdminController controller = new SystemAdminController(
                systemService, userService, courseService, analyticsService, enrollmentService, passwordEncoder);
        // 独立 MockMvc 不加载 Spring 上下文，需手动注册全局异常处理器，
        // 否则控制器抛出的 BusinessException 会穿透为 500 而不是被统一转换为 4xx。
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        lenient().when(passwordEncoder.encode(any())).thenReturn("encoded");

        User u = makeUser(1L);
        lenient().when(userService.getById(any())).thenReturn(u);
        lenient().when(userService.getUserProfile(any())).thenReturn(u);
        lenient().when(userService.save(any())).thenReturn(true);
        lenient().when(userService.updateById(any())).thenReturn(true);
        lenient().when(userService.removeById(anyLong())).thenReturn(true);
        lenient().when(userService.count()).thenReturn(5L);
        lenient().when(userService.count(any())).thenReturn(2L);
        Page<User> up = new Page<>(1, 10);
        up.setRecords(List.of(u));
        up.setTotal(1);
        lenient().when(userService.page(any(), any())).thenReturn(up);

        Course c = new Course();
        c.setId(10L);
        c.setTeacherId(1L);
        c.setIsActive(true);
        lenient().when(courseService.getById(any())).thenReturn(c);
        lenient().when(courseService.count()).thenReturn(3L);
        lenient().when(courseService.count(any())).thenReturn(1L);
        lenient().when(courseService.list()).thenReturn(List.of(c));

        lenient().when(enrollmentService.count()).thenReturn(4L);
        lenient().when(enrollmentService.count(any())).thenReturn(1L);

        lenient().when(systemService.getConfigs(any())).thenReturn(List.of(Map.of("key", "k", "value", "v")));
        lenient().doNothing().when(systemService).saveConfig(any(), any(), any());
        lenient().when(systemService.getLogFiles()).thenReturn(List.of(Map.of("file", "a.log")));
        lenient().when(systemService.getHealth()).thenReturn(Map.of("status", "UP"));
        lenient().when(analyticsService.getUserGrowthTrend(anyInt())).thenReturn(List.of(Map.of("d", 1)));
        lenient().when(analyticsService.getCourseRanking()).thenReturn(List.of(Map.of("name", "c")));
        lenient().when(systemService.getAIUsageStats()).thenReturn(Map.of("calls", 1));
        lenient().when(analyticsService.getDashboard()).thenReturn(Map.of("users", 1));
        lenient().when(systemService.createBackup()).thenReturn("backup-1.zip");
        lenient().when(systemService.getOverview()).thenReturn(Map.of("total", 1));
        lenient().when(systemService.getBackups()).thenReturn(List.of(Map.of("name", "b.zip")));
        lenient().doNothing().when(systemService).restoreBackup(any());
        lenient().when(systemService.readLogFile(any(), anyInt(), any())).thenReturn("log line");
        lenient().when(systemService.aiMonitor()).thenReturn(Map.of("risk", "low"));
    }

    private User makeUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("alice");
        u.setFullName("Alice");
        u.setRole("STUDENT");
        u.setEmail("alice@example.com");
        return u;
    }

    private Course makeCourse() {
        Course c = new Course();
        c.setId(10L);
        c.setTeacherId(1L);
        c.setIsActive(true);
        return c;
    }

    // ===================== 用户管理 =====================

    @Test
    @DisplayName("GET /admin/users 成功 + 带筛选")
    void getUsers() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/users"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/users")
                        .param("role", "STUDENT").param("keyword", "ali"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /admin/users/{userId} 成功 + 不存在(异常)")
    void getUserDetail() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/users/1")).andExpect(status().isOk());
        when(userService.getUserProfile(999L)).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/users/999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /admin/users 创建（AdminCreateUserDTO 白名单绑定，password 正常反序列化 -> 200）")
    void createUser() throws Exception {
        String body = "{\"username\":\"bob\",\"password\":\"" + VALID_PWD + "\",\"role\":\"STUDENT\"}";
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/users")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /admin/users 弱口令(密码校验异常)")
    void createUserWeakPassword() throws Exception {
        String body = "{\"username\":\"bob\",\"password\":\"123\",\"role\":\"STUDENT\"}";
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/users")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /admin/users/{userId} 更新（合法请求 200）+ 不存在(异常 -> 400)")
    void updateUser() throws Exception {
        // 合法更新请求（AdminUpdateUserDTO 白名单字段，不含密码）-> 200
        String body = "{\"fullName\":\"Bobby\",\"email\":\"bobby@example.com\",\"role\":\"STUDENT\"}";
        mockMvc.perform(MockMvcRequestBuilders.put("/admin/users/1")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
        when(userService.getById(999L)).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.put("/admin/users/999")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /admin/users/{userId} 删除成功 + 不存在(异常)")
    void deleteUser() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/admin/users/1")).andExpect(status().isOk());
        when(userService.getById(999L)).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.delete("/admin/users/999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /admin/users/{userId}/reset-password 成功 + 空密码(异常->400) + 不存在(异常->400)")
    void resetUserPassword() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/admin/users/1/reset-password")
                        .contentType("application/json").content("{\"newPassword\":\"" + VALID_PWD + "\"}"))
                .andExpect(status().isOk());
        // 新密码为空 -> BusinessException 新密码不能为空 -> 400
        mockMvc.perform(MockMvcRequestBuilders.put("/admin/users/1/reset-password")
                        .contentType("application/json").content("{\"newPassword\":\"\"}"))
                .andExpect(status().isBadRequest());
        // 用户不存在 -> BusinessException 用户不存在 -> 400
        when(userService.getById(999L)).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.put("/admin/users/999/reset-password")
                        .contentType("application/json").content("{\"newPassword\":\"" + VALID_PWD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /admin/users/import 批量导入（AdminCreateUserDTO 白名单绑定 -> 成功）")
    void importUsers() throws Exception {
        // password 经 AdminCreateUserDTO 正常反序列化；username 满足 @Size(min=3) -> 导入成功
        String body = "[{\"username\":\"user01\",\"password\":\"" + VALID_PWD + "\",\"role\":\"STUDENT\"},"
                + "{\"username\":\"user02\",\"password\":\"" + VALID_PWD + "\",\"role\":\"STUDENT\"}]";
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/users/import")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());

        // 第二次 save 抛异常 -> 走 failCount 分支（lenient 避免严格桩报 UnnecessaryStubbing）
        lenient().when(userService.save(any())).thenReturn(true).thenThrow(new RuntimeException("boom"));
        String body2 = "[{\"username\":\"user01\",\"password\":\"" + VALID_PWD + "\",\"role\":\"STUDENT\"},"
                + "{\"username\":\"user02\",\"password\":\"" + VALID_PWD + "\",\"role\":\"STUDENT\"}]";
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/users/import")
                        .contentType("application/json").content(body2))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /admin/users/import-excel 各分支：无文件/空文件/非Excel/空数据/成功+失败计数")
    void importUsersExcel() throws Exception {
        // 无文件
        mockMvc.perform(MockMvcRequestBuilders.multipart("/admin/users/import-excel"))
                .andExpect(status().isBadRequest());
        // 空文件
        MockMultipartFile empty = new MockMultipartFile("file", "x.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);
        mockMvc.perform(MockMvcRequestBuilders.multipart("/admin/users/import-excel").file(empty))
                .andExpect(status().isBadRequest());
        // 非 Excel
        MockMultipartFile notExcel = new MockMultipartFile("file", "x.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(MockMvcRequestBuilders.multipart("/admin/users/import-excel").file(notExcel))
                .andExpect(status().isBadRequest());
        // 空数据（仅表头）
        MockMultipartFile noData = new MockMultipartFile("file", "x.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", userExcel(false, false));
        mockMvc.perform(MockMvcRequestBuilders.multipart("/admin/users/import-excel").file(noData))
                .andExpect(status().isBadRequest());
        // 成功 + 失败计数
        when(userService.save(any())).thenReturn(true).thenThrow(new RuntimeException("boom"));
        MockMultipartFile ok = new MockMultipartFile("file", "x.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", userExcel(true, true));
        mockMvc.perform(MockMvcRequestBuilders.multipart("/admin/users/import-excel").file(ok))
                .andExpect(status().isOk());
    }

    // ===================== 系统配置 =====================

    @Test
    @DisplayName("配置相关端点")
    void configs() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/configs")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/configs").param("group", "g")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.put("/admin/configs/k1")
                        .contentType("application/json").content("{\"value\":\"v\",\"group\":\"g\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/logs")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/health")).andExpect(status().isOk());
    }

    // ===================== 数据统计 =====================

    @Test
    @DisplayName("数据统计相关端点")
    void statistics() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/statistics/overview")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/statistics/user-growth")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/statistics/user-growth").param("days", "7")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/statistics/courses")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/statistics/ai-usage")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /admin/statistics/export 导出报表(void + Excel 写入)")
    void exportStatisticsReport() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/statistics/export"))
                .andExpect(status().isOk());
    }

    // ===================== 备份 / 兼容接口 =====================

    @Test
    @DisplayName("备份与兼容接口")
    void backupAndCompat() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/backup")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/system/overview")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/system/configs")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/system/configs")
                        .contentType("application/json").content("{\"key\":\"k\",\"value\":\"v\",\"group\":\"g\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/system/health")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/backup")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/system/backup/b.zip/restore")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/system/logs")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/system/logs/a.log")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/system/logs/a.log").param("lines", "50").param("keyword", "err"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /admin/ai-monitor 异步返回")
    void aiMonitor() throws Exception {
        MvcResult mvc = mockMvc.perform(MockMvcRequestBuilders.get("/admin/ai-monitor"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted())
                .andReturn();
        mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(mvc)).andExpect(status().isOk());
    }

    // ===================== 工具：构建用户 Excel =====================

    private byte[] userExcel(boolean includeData, boolean twoRows) throws Exception {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("users");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            String[] headers = {"用户名", "姓名", "邮箱", "角色", "初始密码", "手机号", "学号", "专业", "年级", "院系"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            if (includeData) {
                String[][] rows = twoRows ? new String[][]{
                        {"u1", "张三", "u1@e.com", "STUDENT", VALID_PWD, "13800138000", "S1", "CS", "2024", "Dept"},
                        {"u2", "李四", "u2@e.com", "STUDENT", VALID_PWD, "13800138001", "S2", "CS", "2024", "Dept"}
                } : new String[][]{
                        {"u1", "张三", "u1@e.com", "STUDENT", VALID_PWD, "13800138000", "S1", "CS", "2024", "Dept"}
                };
                int r = 1;
                for (String[] row : rows) {
                    org.apache.poi.ss.usermodel.Row rr = sheet.createRow(r++);
                    for (int c = 0; c < row.length; c++) {
                        rr.createCell(c).setCellValue(row[c] == null ? "" : row[c]);
                    }
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }
}
