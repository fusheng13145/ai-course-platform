package com.aicourse.controller;

import com.aicourse.entity.Course;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.SystemService;
import com.aicourse.service.UserService;
import com.aicourse.security.AdminScopeGuard;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SystemAdminController 覆盖率补强测试（与既有 {@link SystemAdminControllerTest} /
 * {@link SystemAdminControllerAclTest} 互补，不改动它们）。
 *
 * <p>逐条枚举源码分支后的用例族：</p>
 * <ul>
 *     <li>列表筛选：{@code /admin/users} 的 role/keyword 「null / 空串 / 非空」三态组合与密码脱敏；</li>
 *     <li>CRUD 不存在分叉：详情 / 更新 / 删除 / 重置密码在用户不存在时业务 400，存在时真实落库交互；</li>
 *     <li>口令策略：创建用户必须通过统一 PasswordPolicy（DTO 正则放行但复杂度不足 → 400），
 *         isActive 显式 false 与缺省 true 两分支；</li>
 *     <li>重置密码：newPassword 缺失（null）与空串两条独立分支；</li>
 *     <li>JSON 批量导入：全部成功（errors 省略）、空集合、逐行异常经 friendlyImportReason
 *         映射（用户名/邮箱/手机号唯一冲突、BusinessException 透传、超长消息 80 字截断、null 消息）；</li>
 *     <li>Excel 批量导入：空文件、仅表头（无数据行）、非法角色、非法手机号、缺省密码随机生成
 *         （tempPasswords 回传）、唯一冲突跳行统计；</li>
 *     <li>兼容端点（/admin/system/**）与统计报表：ADMIN 成功路径委托断言；课程分类分布 / Top5 截断 /
 *         isActive 三态过滤；</li>
 *     <li>鉴权面（安全语义正证）：/admin/** 未认证 401、非 ADMIN 403 —— 覆盖 Acl 矩阵未含的
 *         export 与 import-excel 端点。</li>
 * </ul>
 */
@WebMvcTest(SystemAdminController.class)
@Import(AdminScopeGuard.class)
class SystemAdminControllerCoverageTest extends ControllerTestBase {

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

    /** 满足 AdminCreateUserDTO 正则与统一 PasswordPolicy 的强口令样本（含大小写/数字/特殊字符）。 */
    private static final String STRONG_PWD = "Abc@1234";
    /** 满足 DTO 正则（字母+数字）但违反 PasswordPolicy（缺特殊字符）的弱口令样本。 */
    private static final String DTO_ONLY_PWD = "Abcdef12";

    // ==================== 用户列表筛选 ====================

    @Test
    @DisplayName("GET /admin/users?role=STUDENT&keyword=张 - 筛选条件生效且密码脱敏")
    void getUsers_withRoleAndKeyword_filtersAndMasks() throws Exception {
        User u = new User();
        u.setUsername("stu1");
        u.setPassword("hashed-secret");
        Page<User> page = new Page<>(1, 10);
        page.setRecords(List.of(u));
        page.setTotal(1);
        when(userService.page(any(), any())).thenReturn(page);

        mockMvc.perform(get("/admin/users").param("role", "STUDENT").param("keyword", "张")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].username").value("stu1"))
                .andExpect(jsonPath("$.data.list[0].password").doesNotExist())
                .andExpect(jsonPath("$.data.total").value(1));
        verify(userService).page(any(), any());
    }

    @Test
    @DisplayName("GET /admin/users?role=&keyword= - 空串条件等同不过滤，返回 200")
    void getUsers_blankFilters_skipsConditions() throws Exception {
        when(userService.page(any(), any())).thenReturn(new Page<>(1, 10));

        mockMvc.perform(get("/admin/users").param("role", "").param("keyword", "")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list").isEmpty());
        verify(userService).page(any(), any());
    }

    // ==================== 用户详情 ====================

    @Test
    @DisplayName("GET /admin/users/{id} - 存在返回 200 并委托 getUserProfile")
    void getUserDetail_found_ok() throws Exception {
        User u = new User();
        u.setUsername("alice");
        when(userService.getUserProfile(9L)).thenReturn(u);

        mockMvc.perform(get("/admin/users/9").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice"));
        verify(userService).getUserProfile(eq(9L));
    }

    @Test
    @DisplayName("GET /admin/users/{id} - 不存在业务 400「用户不存在」")
    void getUserDetail_notFound_400() throws Exception {
        when(userService.getUserProfile(404L)).thenReturn(null);

        mockMvc.perform(get("/admin/users/404").with(withUser(1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    // ==================== 创建用户 ====================

    @Test
    @DisplayName("POST /admin/users - isActive 缺省默认启用，密码加密入库且响应不回显")
    void createUser_defaultActive_encryptsPassword() throws Exception {
        when(passwordEncoder.encode(STRONG_PWD)).thenReturn("$2a$10$encNew");
        // createUser 在 save 之后会把同一实例的 password 置 null（防回显），
        // ArgumentCaptor 拿到的是这个被改写后的引用，因此必须在 save 调用时刻快照入库值。
        AtomicReference<String> persistedPassword = new AtomicReference<>();
        AtomicReference<Boolean> persistedActive = new AtomicReference<>();
        when(userService.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            persistedPassword.set(saved.getPassword());
            persistedActive.set(saved.getIsActive());
            return true;
        });

        mockMvc.perform(post("/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new_user\",\"fullName\":\"新用户\",\"phone\":\"13800138000\","
                                + "\"email\":\"nu@example.com\",\"password\":\"" + STRONG_PWD + "\",\"role\":\"STUDENT\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("创建成功"))
                .andExpect(jsonPath("$.data.username").value("new_user"))
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        verify(passwordEncoder).encode(STRONG_PWD);
        assertEquals("$2a$10$encNew", persistedPassword.get(), "必须存加密后的密码");
        assertEquals(Boolean.TRUE, persistedActive.get(), "isActive 缺省应默认 true");
    }

    @Test
    @DisplayName("POST /admin/users - isActive=false 显式停用被保留")
    void createUser_explicitInactive_preserved() throws Exception {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$enc");
        when(userService.save(any(User.class))).thenReturn(true);

        mockMvc.perform(post("/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"off_user\",\"password\":\"" + STRONG_PWD + "\","
                                + "\"role\":\"TEACHER\",\"isActive\":false}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk());

        verify(userService).save(argThat(u -> Boolean.FALSE.equals(u.getIsActive())));
    }

    @Test
    @DisplayName("POST /admin/users - DTO 正则放行但违反 PasswordPolicy 的弱口令 → 400 且不落库")
    void createUser_policyWeakPassword_400() throws Exception {
        mockMvc.perform(post("/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"weak_user\",\"password\":\"" + DTO_ONLY_PWD + "\",\"role\":\"STUDENT\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("密码必须包含大写字母、小写字母、数字和特殊字符")));
        verify(userService, never()).save(any(User.class));
    }

    // ==================== 更新 / 删除用户 ====================

    @Test
    @DisplayName("PUT /admin/users/{id} - 用户不存在 400，不触发更新")
    void updateUser_notFound_400() throws Exception {
        when(userService.getById(7L)).thenReturn(null);

        mockMvc.perform(put("/admin/users/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"upd_user\",\"role\":\"TEACHER\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("用户不存在"));
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("PUT /admin/users/{id} - 白名单字段更新成功并回读最新资料")
    void updateUser_success() throws Exception {
        when(userService.getById(3L)).thenReturn(new User());
        when(userService.updateById(any(User.class))).thenReturn(true);
        User updated = new User();
        updated.setFullName("更新后");
        when(userService.getUserProfile(3L)).thenReturn(updated);

        mockMvc.perform(put("/admin/users/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"upd_user\",\"role\":\"TEACHER\",\"isActive\":true}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("更新成功"))
                .andExpect(jsonPath("$.data.fullName").value("更新后"));

        ArgumentCaptor<User> captor = ArgumentCaptor.captor();
        verify(userService).updateById(captor.capture());
        assertEquals(3L, captor.getValue().getId(), "主键必须取自路径，杜绝请求体注入");
        assertEquals("upd_user", captor.getValue().getUsername());
        verify(userService).getUserProfile(eq(3L));
    }

    @Test
    @DisplayName("DELETE /admin/users/{id} - 不存在 400 / 存在则删除并返回删除成功")
    void deleteUser_notFound_and_success() throws Exception {
        when(userService.getById(8L)).thenReturn(null);
        mockMvc.perform(delete("/admin/users/8").with(withUser(1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("用户不存在"));

        when(userService.getById(9L)).thenReturn(new User());
        mockMvc.perform(delete("/admin/users/9").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("删除成功"));
        verify(userService).removeById(eq(9L));
    }

    // ==================== 重置密码 ====================

    @Test
    @DisplayName("PUT /admin/users/{id}/reset-password - 用户不存在 400")
    void resetPassword_userNotFound_400() throws Exception {
        when(userService.getById(11L)).thenReturn(null);

        mockMvc.perform(put("/admin/users/11/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + STRONG_PWD + "\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    @DisplayName("PUT /admin/users/{id}/reset-password - newPassword 缺失(null) 与空串各自 400")
    void resetPassword_missingOrEmpty_400() throws Exception {
        when(userService.getById(12L)).thenReturn(new User());

        mockMvc.perform(put("/admin/users/12/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{}").with(withUser(1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("新密码不能为空"));

        mockMvc.perform(put("/admin/users/12/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"\"}").with(withUser(1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("新密码不能为空"));
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("PUT /admin/users/{id}/reset-password - 合法强口令：加密后更新")
    void resetPassword_success() throws Exception {
        when(userService.getById(13L)).thenReturn(new User());
        when(passwordEncoder.encode(STRONG_PWD)).thenReturn("$2a$10$rehashed");

        mockMvc.perform(put("/admin/users/13/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + STRONG_PWD + "\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("密码重置成功"));

        ArgumentCaptor<User> captor = ArgumentCaptor.captor();
        verify(userService).updateById(captor.capture());
        assertEquals("$2a$10$rehashed", captor.getValue().getPassword());
    }

    // ==================== JSON 批量导入 ====================

    @Test
    @DisplayName("POST /admin/users/import - 空集合：0/0/0 且 errors 省略")
    void importUsers_emptyList_ok() throws Exception {
        mockMvc.perform(post("/admin/users/import").contentType(MediaType.APPLICATION_JSON)
                        .content("[]").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failCount").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.errors").doesNotExist());
    }

    @Test
    @DisplayName("POST /admin/users/import - 全部成功：isActive 两分支，errors/tempPasswords 省略")
    void importUsers_allSuccess() throws Exception {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$i");
        when(userService.save(any(User.class))).thenReturn(true);

        mockMvc.perform(post("/admin/users/import").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"username\":\"imp_a\",\"password\":\"" + DTO_ONLY_PWD + "\",\"role\":\"STUDENT\"},"
                                + "{\"username\":\"imp_b\",\"password\":\"" + DTO_ONLY_PWD + "\","
                                + "\"role\":\"TEACHER\",\"isActive\":false}]")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.errors").doesNotExist())
                .andExpect(jsonPath("$.data.tempPasswords").doesNotExist());

        verify(userService).save(argThat(u -> "imp_a".equals(u.getUsername())
                && Boolean.TRUE.equals(u.getIsActive())));
        verify(userService).save(argThat(u -> "imp_b".equals(u.getUsername())
                && Boolean.FALSE.equals(u.getIsActive())));
    }

    @Test
    @DisplayName("POST /admin/users/import - 逐行异常映射：三类唯一冲突/业务异常/超长截断/null 消息全分支")
    void importUsers_everyFailureReasonMapped() throws Exception {
        when(userService.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            switch (u.getUsername()) {
                case "u_dupname" -> throw new RuntimeException("Duplicate entry 'u_dupname' for key 'users.uk_users_username'");
                case "u_dupmail" -> throw new RuntimeException("Duplicate entry 'a@b.com' for key 'users.uk_users_email'");
                case "u_dupp" -> throw new RuntimeException("Duplicate entry '138' for key 'users.uk_users_phone'");
                case "u_biz" -> throw new BusinessException("角色服务不可用");
                case "u_long" -> throw new RuntimeException("E".repeat(100));
                case "u_nullmsg" -> throw new RuntimeException((String) null);
                default -> {
                    return true;
                }
            }
        });

        // 片段（不含大括号）：与每行的 {"username":"..."," 拼接后才是合法 JSON 对象
        String dto = "\"password\":\"" + DTO_ONLY_PWD + "\",\"role\":\"STUDENT\"";
        mockMvc.perform(post("/admin/users/import").contentType(MediaType.APPLICATION_JSON)
                        .content("["
                                + "{\"username\":\"u_ok\"," + dto + "},"
                                + "{\"username\":\"u_dupname\"," + dto + "},"
                                + "{\"username\":\"u_dupmail\"," + dto + "},"
                                + "{\"username\":\"u_dupp\"," + dto + "},"
                                + "{\"username\":\"u_biz\"," + dto + "},"
                                + "{\"username\":\"u_long\"," + dto + "},"
                                + "{\"username\":\"u_nullmsg\"," + dto + "}]")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failCount").value(6))
                .andExpect(jsonPath("$.data.total").value(7))
                .andExpect(jsonPath("$.data.errors[0]").value("用户名「u_dupname」：用户名已存在"))
                .andExpect(jsonPath("$.data.errors[1]").value("用户名「u_dupmail」：邮箱已被其他账号使用"))
                .andExpect(jsonPath("$.data.errors[2]").value("用户名「u_dupp」：手机号已被其他账号使用"))
                .andExpect(jsonPath("$.data.errors[3]").value("用户名「u_biz」：角色服务不可用"))
                .andExpect(jsonPath("$.data.errors[4]").value("用户名「u_long」：写入失败：" + "E".repeat(80) + "…"))
                .andExpect(jsonPath("$.data.errors[5]").value("用户名「u_nullmsg」：写入失败："));
    }

    // ==================== Excel 批量导入 ====================

    /** 构造用户导入 xlsx：每行 6 列（用户名/姓名/邮箱/角色/初始密码/手机号），null 或空串跳过单元格。 */
    private static MockMultipartFile userExcel(String[]... rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("用户");
            String[] heads = {"用户名", "姓名", "邮箱", "角色", "初始密码", "手机号", "学号", "专业", "年级", "院系"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < heads.length; i++) {
                header.createCell(i).setCellValue(heads[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null && !rows[r][c].isEmpty()) {
                        row.createCell(c).setCellValue(rows[r][c]);
                    }
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return new MockMultipartFile("file", "users.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
        }
    }

    @Test
    @DisplayName("POST /admin/users/import-excel - 空文件：400「请选择要上传的 Excel 文件」")
    void importExcel_emptyFile_400() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "users.xlsx", "application/octet-stream", new byte[0]);
        mockMvc.perform(multipart("/admin/users/import-excel").file(empty).with(withUser(1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请选择要上传的 Excel 文件"));
    }

    @Test
    @DisplayName("POST /admin/users/import-excel - 仅表头无数据行：400「文件中没有可导入的用户数据」")
    void importExcel_headerOnly_400() throws Exception {
        MockMultipartFile file = userExcel();
        mockMvc.perform(multipart("/admin/users/import-excel").file(file).with(withUser(1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("文件中没有可导入的用户数据"));
    }

    @Test
    @DisplayName("POST /admin/users/import-excel - 角色/手机号校验与随机密码回传及唯一冲突跳行")
    void importExcel_validationsAndGeneratedPasswords() throws Exception {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$e");
        when(userService.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if ("dupx".equals(u.getUsername())) {
                throw new RuntimeException("Duplicate entry 'dupx' for key 'users.uk_users_username'");
            }
            return true;
        });

        MockMultipartFile file = userExcel(
                new String[]{"valid1", "张三", "v1@example.com", "STUDENT", DTO_ONLY_PWD, "13800138000"},
                new String[]{"badrole", "老王", "w@example.com", "BOSS", DTO_ONLY_PWD, null},
                new String[]{"badphone", "老李", "l@example.com", "STUDENT", DTO_ONLY_PWD, "12345"},
                new String[]{"genpwd", "缺密码", "g@example.com", "TEACHER", null, null},
                new String[]{"dupx", "重复", "d@example.com", "STUDENT", DTO_ONLY_PWD, null});

        mockMvc.perform(multipart("/admin/users/import-excel").file(file).with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("导入完成"))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failCount").value(3))
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.tempPasswords.genpwd").exists())
                .andExpect(jsonPath("$.data.errors[0]").value(org.hamcrest.Matchers.containsString(
                        "角色不合法（应为 STUDENT/TEACHER/ADMIN），当前值：「BOSS」")))
                .andExpect(jsonPath("$.data.errors[1]").value(org.hamcrest.Matchers.containsString(
                        "手机号格式不正确（应为 1 开头的 11 位数字），当前值：「12345」")))
                .andExpect(jsonPath("$.data.errors[2]").value("用户名「dupx」：用户名已存在"));

        // 入库前必须置空解析期主键并默认启用（与直接创建口径一致）。
        // 按用户名逐条断言：dupx 那行 save 也被调用（抛异常被逐行捕获计入 failCount），
        // 用 times(n) 计数会把失败行一并算进来。
        verify(userService).save(argThat(u -> "valid1".equals(u.getUsername())
                && u.getId() == null && Boolean.TRUE.equals(u.getIsActive())));
        verify(userService).save(argThat(u -> "genpwd".equals(u.getUsername())
                && u.getId() == null && Boolean.TRUE.equals(u.getIsActive())));
    }

    // ==================== 系统配置 / 兼容端点 ====================

    @Test
    @DisplayName("PUT /admin/configs/{key} - value/group 均缺失时仍以 null 委托 saveConfig")
    void updateConfig_missingFields_delegatesNulls() throws Exception {
        mockMvc.perform(put("/admin/configs/site.name").contentType(MediaType.APPLICATION_JSON)
                        .content("{}").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("配置更新成功"));
        verify(systemService).saveConfig(eq("site.name"), isNullArg(), isNullArg());
    }

    /** 匹配 null 实参（any() 不匹配 null 语义处使用）。 */
    private static <T> T isNullArg() {
        return org.mockito.ArgumentMatchers.isNull();
    }

    @Test
    @DisplayName("GET /admin/system/overview - 兼容端点委托 getOverview 200")
    void compatOverview_ok() throws Exception {
        when(systemService.getOverview()).thenReturn(Map.of("totalUsers", 5));
        mockMvc.perform(get("/admin/system/overview").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(5));
        verify(systemService).getOverview();
    }

    @Test
    @DisplayName("GET /admin/system/configs?group=basic - 兼容端点透传分组并复用 getConfigs")
    void compatSystemConfigs_ok() throws Exception {
        when(systemService.getConfigs(eq("basic"))).thenReturn(List.of(Map.of("key", "k")));
        mockMvc.perform(get("/admin/system/configs").param("group", "basic").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].key").value("k"));
        verify(systemService).getConfigs(eq("basic"));
    }

    @Test
    @DisplayName("POST /admin/system/configs - 兼容保存：key/value/group 全量透传")
    void compatSaveConfig_ok() throws Exception {
        mockMvc.perform(post("/admin/system/configs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"k1\",\"value\":\"v1\",\"group\":\"g1\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("保存成功"));
        verify(systemService).saveConfig(eq("k1"), eq("v1"), eq("g1"));
    }

    @Test
    @DisplayName("GET /admin/system/health / logs / backup - 兼容只读端点各自委托")
    void compatReadOnlyEndpoints_ok() throws Exception {
        when(systemService.getHealth()).thenReturn(Map.of("status", "UP"));
        mockMvc.perform(get("/admin/system/health").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));

        when(systemService.getLogFiles()).thenReturn(List.of(Map.of("name", "app.log")));
        mockMvc.perform(get("/admin/system/logs").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("app.log"));

        when(systemService.getBackups()).thenReturn(List.of(Map.of("file", "b.sql")));
        mockMvc.perform(get("/admin/backup").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].file").value("b.sql"));
    }

    @Test
    @DisplayName("GET /admin/system/logs/{file}?lines&keyword - 参数透传 readLogFile，内容落 data")
    void readLog_withParams() throws Exception {
        when(systemService.readLogFile(eq("app.log"), eq(50), eq("err"))).thenReturn("matched line");
        // DR-33 回归钉：内容必须在 data，不能因 ApiResponse.success(String)/success(T) 重载
        // 绑定陷阱跑到 message 里（那时 non_null 会让 data 键整体消失）。
        mockMvc.perform(get("/admin/system/logs/app.log").param("lines", "50").param("keyword", "err")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("matched line"))
                .andExpect(jsonPath("$.message").value("读取成功"));
        verify(systemService).readLogFile(eq("app.log"), eq(50), eq("err"));
    }

    @Test
    @DisplayName("GET /admin/ai-monitor - ADMIN 触发 AI 异常监控（Callable 异步）返回 200")
    void aiMonitor_admin_ok() throws Exception {
        when(systemService.aiMonitor()).thenReturn(Map.of("risk", "低"));
        MvcResult mvc = mockMvc.perform(get("/admin/ai-monitor").with(withUser(1L, "ADMIN")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.risk").value("低"));
        verify(systemService).aiMonitor();
    }

    // ==================== 数据统计与报表 ====================

    private static Course course(Boolean active, String category) {
        Course c = new Course();
        c.setIsActive(active);
        c.setCategory(category);
        return c;
    }

    @Test
    @DisplayName("GET /admin/statistics/courses - 分类分布聚合、null 分类过滤、Top 榜截断为 5")
    void statisticsCourses_distributionAndTop5() throws Exception {
        when(courseService.list()).thenReturn(List.of(
                course(true, "编程"), course(false, "编程"), course(null, null)));
        when(analyticsService.getCourseRanking()).thenReturn(List.of(
                Map.of("id", 1), Map.of("id", 2), Map.of("id", 3), Map.of("id", 4),
                Map.of("id", 5), Map.of("id", 6), Map.of("id", 7)));

        mockMvc.perform(get("/admin/statistics/courses").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryDistribution.编程").value(2))
                .andExpect(jsonPath("$.data.topCourses.length()").value(5));
    }

    @Test
    @DisplayName("GET /admin/statistics/export - 报表聚合含 isActive 三态课程：仍输出合法 xlsx 流")
    void exportReport_withMixedCourses_stillXlsx() throws Exception {
        when(analyticsService.getDashboard()).thenReturn(Map.of("totalUsers", 10L));
        when(systemService.getAIUsageStats()).thenReturn(Map.of("aiCalls", 3L));
        when(courseService.list()).thenReturn(List.of(
                course(true, "编程"), course(false, "编程"), course(null, null)));

        mockMvc.perform(get("/admin/statistics/export").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(mvcResult -> {
                    byte[] body = mvcResult.getResponse().getContentAsByteArray();
                    org.junit.jupiter.api.Assertions.assertTrue(
                            body[0] == 'P' && body[1] == 'K' && body[2] == 3 && body[3] == 4,
                            "响应体应为 xlsx(ZIP) 字节流");
                });
        verify(userService, org.mockito.Mockito.atLeastOnce()).count();
    }

    // ==================== 鉴权面（安全语义正证） ====================

    @Test
    @DisplayName("GET /admin/statistics/export - 未认证 401 / 教师 403（导出含全量经营数据，仅限 ADMIN）")
    void export_statistics_acl() throws Exception {
        mockMvc.perform(get("/admin/statistics/export"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/statistics/export").with(withUser(3L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /admin/users/import-excel - 未认证 401 / 学生 403（建号入口仅限 ADMIN）")
    void importExcel_acl() throws Exception {
        MockMultipartFile file = userExcel(new String[]{"x1", null, null, "STUDENT", DTO_ONLY_PWD, null});
        mockMvc.perform(multipart("/admin/users/import-excel").file(file))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/admin/users/import-excel").file(file).with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
        verify(userService, never()).save(any(User.class));
    }

    // ==================== isActive 三元 else 分支 / 缺件面 ====================

    @Test
    @DisplayName("POST /admin/users - isActive 显式 null：回落 true（三元 else 分支）")
    void createUser_explicitNullIsActive_fallsBackTrue() throws Exception {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$enc");
        Boolean[] savedActive = new Boolean[1];
        when(userService.save(any(User.class))).thenAnswer(inv -> {
            savedActive[0] = ((User) inv.getArgument(0)).getIsActive();
            return true;
        });

        mockMvc.perform(post("/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"null_active\",\"password\":\"" + STRONG_PWD + "\","
                                + "\"role\":\"STUDENT\",\"isActive\":null}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(true));

        assertEquals(Boolean.TRUE, savedActive[0], "显式 null 应回落为 true（三元 else 分支）");
    }

    @Test
    @DisplayName("POST /admin/users/import - isActive 显式 null：入库前回落 true")
    void importUsers_explicitNullIsActive_fallsBackTrue() throws Exception {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$i");
        when(userService.save(any(User.class))).thenReturn(true);

        mockMvc.perform(post("/admin/users/import").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"username\":\"imp_null\",\"password\":\"" + DTO_ONLY_PWD + "\","
                                + "\"role\":\"STUDENT\",\"isActive\":null}]")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1));

        verify(userService).save(argThat(u -> Boolean.TRUE.equals(u.getIsActive())));
    }

    @Test
    @DisplayName("POST /admin/users/import-excel - 缺 multipart 部件：400 且不触达任何 save")
    void importExcel_missingPart_400() throws Exception {
        mockMvc.perform(multipart("/admin/users/import-excel").with(withUser(1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("缺少必需上传文件")));
        verify(userService, never()).save(any(User.class));
    }

    // ==================== WP2：院系管理员作用域边界 ====================

    /** 院系管理员（id=3，学院 10）的 DB 现用户表示，供 AdminScopeGuard 派生作用域与学院。 */
    private static User collegeAdminUser() {
        User u = new User();
        u.setId(3L);
        u.setCollegeId(10L);
        u.setRole("ADMIN");
        return u;
    }

    @Test
    @DisplayName("GET /admin/users/{id} - 院系管理员访问他院用户 403（管理端第二入口同样受限）")
    void getUserDetail_collegeAdmin_otherCollege_403() throws Exception {
        when(userService.getById(3L)).thenReturn(collegeAdminUser());
        User other = new User();
        other.setId(9L);
        other.setCollegeId(11L);
        when(userService.getUserProfile(9L)).thenReturn(other);

        mockMvc.perform(get("/admin/users/9").with(withCollegeAdmin(3L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("PUT /admin/configs/{key} 与 POST /admin/users/import - 院系管理员 403 且不落库")
    void schoolOnlyWrites_collegeAdmin_403() throws Exception {
        mockMvc.perform(put("/admin/configs/site.name").contentType(MediaType.APPLICATION_JSON)
                        .content("{}").with(withCollegeAdmin(3L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(post("/admin/users/import").contentType(MediaType.APPLICATION_JSON)
                        .content("[]").with(withCollegeAdmin(3L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(systemService, never()).saveConfig(anyString(), anyString(), anyString());
        verify(userService, never()).save(any(User.class));
    }
}
