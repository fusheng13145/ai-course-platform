package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.User;
import com.aicourse.service.AIService;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.LoginLogService;
import com.aicourse.service.OperationLogService;
import com.aicourse.service.SystemService;
import com.aicourse.service.UserService;
import com.aicourse.security.AdminScopeGuard;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 系统管理控制器接口测试（受保护 + 角色鉴权范例）。
 * /admin/** 在 SecurityConfig 中要求 ADMIN 角色，且类上标注 @PreAuthorize("hasRole('ADMIN')")，
 * 故可验证：未认证 -> 401、非管理员(STUDENT) -> 403、管理员(ADMIN) -> 200。
 *
 * <p>注意：PasswordEncoder 由 SecurityConfig 提供的真实 bean 注入，无需 mock。
 */
@WebMvcTest(SystemAdminController.class)
@Import(AdminScopeGuard.class)
class SystemAdminControllerTest extends ControllerTestBase {

    @MockitoBean
    private SystemService systemService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private CourseService courseService;
    @MockitoBean
    private AnalyticsService analyticsService;
    @MockitoBean
    private CourseEnrollmentService enrollmentService;
    @MockitoBean
    private AIService aiService;
    @MockitoBean
    private LoginLogService loginLogService;
    @MockitoBean
    private OperationLogService operationLogService;

    @Test
    @DisplayName("未认证访问管理员接口-返回401")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("非管理员(学生)访问管理员接口-返回403")
    void forbidden_for_student() throws Exception {
        mockMvc.perform(get("/admin/users").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员访问用户列表-返回200并调用service")
    void admin_list_users() throws Exception {
        when(userService.page(any(), any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());
        mockMvc.perform(get("/admin/users").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk());
        verify(userService).page(any(), any());
    }

    @Test
    @DisplayName("管理员获取系统健康状态-返回200")
    void admin_health() throws Exception {
        when(systemService.getHealth()).thenReturn(java.util.Map.of("status", "UP"));
        mockMvc.perform(get("/admin/health").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk());
        verify(systemService).getHealth();
    }

    @Test
    @DisplayName("GET /admin/statistics/export - 真实业务断言：导出多 Sheet xlsx 文件流")
    void admin_export_statistics_xlsx() throws Exception {
        when(analyticsService.getDashboard()).thenReturn(Map.of("totalUsers", 100L));
        when(systemService.getAIUsageStats()).thenReturn(Map.of("aiCalls", 42L));
        mockMvc.perform(get("/admin/statistics/export").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment; filename*=UTF-8''")))
                .andExpect(mvcResult -> {
                    byte[] body = mvcResult.getResponse().getContentAsByteArray();
                    assertTrue(body.length > 4, "导出内容不应为空");
                    assertTrue(body[0] == 'P' && body[1] == 'K' && body[2] == 3 && body[3] == 4,
                            "响应体应为 xlsx(ZIP) 字节流");
                });
        verify(analyticsService).getDashboard();
        verify(systemService).getAIUsageStats();
    }

    @Test
    @DisplayName("POST /admin/users/import-excel - 真实业务断言：解析 xlsx 逐用户入库（BCrypt 加密）并统计")
    void admin_import_users_excel() throws Exception {
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$mockedBcryptHash");
        when(userService.save(any(User.class))).thenReturn(true);
        MockMultipartFile file = excelUserFile("zhangsan", "lisi");
        mockMvc.perform(multipart("/admin/users/import-excel").file(file).with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failCount").value(0))
                .andExpect(jsonPath("$.data.total").value(2));
        // 密码必须经 BCrypt 加密后才入库（编码结果以 $2 开头），且默认启用账号
        verify(userService, times(2)).save(argThat(u -> u.getPassword() != null
                && u.getPassword().startsWith("$2") && Boolean.TRUE.equals(u.getIsActive())));
    }

    @Test
    @DisplayName("POST /admin/users/import-excel - 非 xlsx 文件返回业务错误400")
    void admin_import_users_bad_format() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bad.csv", "text/csv", "a,b,c".getBytes());
        mockMvc.perform(multipart("/admin/users/import-excel").file(file).with(withUser(1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verify(userService, never()).save(any(User.class));
    }

    @Test
    @DisplayName("POST /admin/users/import-excel - 未认证返回401")
    void admin_import_users_unauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx", "application/octet-stream", new byte[]{1});
        mockMvc.perform(multipart("/admin/users/import-excel").file(file))
                .andExpect(status().isUnauthorized());
    }

    /** 生成与导入列头一致的用户 xlsx 上传文件（表头 + 若干行用户名） */
    private MockMultipartFile excelUserFile(String... usernames) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("用户");
            String[] heads = {"用户名", "姓名", "邮箱", "角色", "初始密码", "手机号", "学号", "专业", "年级", "院系"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < heads.length; i++) {
                header.createCell(i).setCellValue(heads[i]);
            }
            for (int r = 0; r < usernames.length; r++) {
                Row row = sheet.createRow(r + 1);
                row.createCell(0).setCellValue(usernames[r]);
                row.createCell(1).setCellValue("测试用户" + (r + 1));
                row.createCell(2).setCellValue(usernames[r] + "@example.com");
                row.createCell(3).setCellValue("STUDENT");
                row.createCell(4).setCellValue("123456");
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return new MockMultipartFile("file", "users.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
        }
    }
}
