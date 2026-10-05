package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.AuthService;
import com.aicourse.service.PermissionService;
import com.aicourse.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证控制器「改密 / 找回密码」HTTP 契约与权限矩阵测试。
 *
 * <p>此前 {@code PUT /auth/password}、{@code POST /auth/reset-password}、
 * {@code /auth/send-code}、{@code /auth/reset-password-by-email}、{@code /auth/verify-code}
 * 五个端点在 HTTP 层<b>零覆盖</b>。{@code /auth/**} 为 permitAll，故这些端点<b>不设角色门槛</b>，
 * 但「修改自己密码」需登录态：未登录时 {@code AuthController#requireLogin} 抛
 * {@code BusinessException(401)}，经 GlobalExceptionHandler 转 401（而非此前隐患的 500）。</p>
 *
 * <p>真实旧密码校验、验证码有效性等在 {@code AuthPasswordComponentTest} 覆盖；此处聚焦控制器的
 * 参数校验、认证边界、委托语义与失败传播（失败时绝不触碰用户写库）。</p>
 */
@WebMvcTest(AuthController.class)
class AuthPasswordControllerTest extends ControllerTestBase {

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private PermissionService permissionService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private com.aicourse.common.CookieUtils cookieUtils;
    @MockitoBean
    private com.aicourse.util.ClientInfoResolver clientInfoResolver;

    private static final String CHANGE_BODY =
            "{\"oldPassword\":\"Right123!\",\"newPassword\":\"NewPass123!\",\"confirmPassword\":\"NewPass123!\"}";

    // ===================== PUT /auth/password（备用改密入口） =====================

    @Test
    @DisplayName("PUT /auth/password - 未登录返回401（requireLogin 拒绝，绝不委托改密）")
    void altChangePassword_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/auth/password").contentType(MediaType.APPLICATION_JSON).content(CHANGE_BODY))
                .andExpect(status().isUnauthorized());
        verify(authService, never()).changePassword(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("PUT /auth/password - 已登录成功返回200，userId 取自认证主体并委托 Service")
    void altChangePassword_authenticated_ok() throws Exception {
        when(authService.changePassword(eq(10L), eq("Right123!"), eq("NewPass123!")))
                .thenReturn(ApiResponse.success("密码已修改，请重新登录", null));
        mockMvc.perform(put("/auth/password").contentType(MediaType.APPLICATION_JSON)
                        .content(CHANGE_BODY).with(withUser(10L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("密码已修改，请重新登录"));
        // 关键：principal 的 userId 必须透传，避免越权改他人密码
        verify(authService).changePassword(eq(10L), eq("Right123!"), eq("NewPass123!"));
    }

    @Test
    @DisplayName("PUT /auth/password - 旧密码错误：Service 抛业务异常，HTTP 失败且不改用户库")
    void altChangePassword_wrongOld_noDbWrite() throws Exception {
        when(authService.changePassword(eq(10L), anyString(), anyString()))
                .thenThrow(new BusinessException("原密码错误"));
        mockMvc.perform(put("/auth/password").contentType(MediaType.APPLICATION_JSON)
                        .content(CHANGE_BODY).with(withUser(10L, "STUDENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("原密码错误"));
        // 失败路径下控制器/门面绝不应直接改写用户表
        verify(userService, never()).updateById(any());
    }

    @Test
    @DisplayName("PUT /auth/password - 缺旧密码被 @Valid 拦截返回400")
    void altChangePassword_missingOld_returns400() throws Exception {
        mockMvc.perform(put("/auth/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPass123!\",\"confirmPassword\":\"NewPass123!\"}")
                        .with(withUser(10L, "STUDENT")))
                .andExpect(status().isBadRequest());
        verify(authService, never()).changePassword(any(), anyString(), anyString());
    }

    // ===================== POST /auth/reset-password（手机/邮箱统一入口） =====================

    @Test
    @DisplayName("POST /auth/reset-password - 邮箱/手机号均未提供：返回业务错误码400且不改库")
    void resetPassword_noIdentifier_returnsCode400() throws Exception {
        mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\",\"newPassword\":\"NewPass123!\"}"))
                .andExpect(status().isOk())
                // 控制器直接 return ApiResponse.error(400,...)（非抛异常），故 HTTP 200 + 业务 code 400
                .andExpect(jsonPath("$.code").value(400));
        verify(authService, never()).resetPassword(anyString(), anyString(), anyString());
        verify(authService, never()).resetPasswordByEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /auth/reset-password - 携带 email 走邮箱重置分支并委托 resetPasswordByEmail")
    void resetPassword_withEmail_delegatesByEmail() throws Exception {
        when(authService.resetPasswordByEmail(eq("a@b.com"), eq("123456"), eq("NewPass123!")))
                .thenReturn(ApiResponse.success("密码已重置，请重新登录", null));
        mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"code\":\"123456\",\"newPassword\":\"NewPass123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(authService).resetPasswordByEmail(eq("a@b.com"), eq("123456"), eq("NewPass123!"));
        verify(authService, never()).resetPassword(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /auth/reset-password - 仅携带 phone 走手机号重置分支")
    void resetPassword_withPhone_delegatesByPhone() throws Exception {
        when(authService.resetPassword(eq("13800001111"), eq("123456"), eq("NewPass123!")))
                .thenReturn(ApiResponse.success("密码已重置，请重新登录", null));
        mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800001111\",\"code\":\"123456\",\"newPassword\":\"NewPass123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(authService).resetPassword(eq("13800001111"), eq("123456"), eq("NewPass123!"));
    }

    @Test
    @DisplayName("POST /auth/reset-password - 验证码无效：Service 抛业务异常，失败且不改用户库")
    void resetPassword_invalidCode_noDbWrite() throws Exception {
        when(authService.resetPasswordByEmail(anyString(), anyString(), anyString()))
                .thenThrow(new BusinessException("验证码无效或已过期"));
        mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"code\":\"000000\",\"newPassword\":\"NewPass123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("验证码无效或已过期"));
        verify(userService, never()).updateById(any());
    }

    // ===================== POST /auth/send-code =====================

    @Test
    @DisplayName("POST /auth/send-code - 合法邮箱委托 sendVerificationCode 并返回200")
    void sendCode_ok() throws Exception {
        when(authService.sendVerificationCode(eq("a@b.com"), eq("FORGOT_PASSWORD"), any()))
                .thenReturn(ApiResponse.success("验证码已发送", null));
        mockMvc.perform(post("/auth/send-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"type\":\"FORGOT_PASSWORD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(authService).sendVerificationCode(eq("a@b.com"), eq("FORGOT_PASSWORD"), any());
    }

    @Test
    @DisplayName("POST /auth/send-code - 缺邮箱被 @Valid 拦截返回400，不发送验证码")
    void sendCode_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/auth/send-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FORGOT_PASSWORD\"}"))
                .andExpect(status().isBadRequest());
        verify(authService, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    // ===================== POST /auth/reset-password-by-email =====================

    @Test
    @DisplayName("POST /auth/reset-password-by-email - 直接委托 resetPasswordByEmail 成功")
    void resetByEmail_ok() throws Exception {
        when(authService.resetPasswordByEmail(eq("a@b.com"), eq("123456"), eq("NewPass123!")))
                .thenReturn(ApiResponse.success("密码已重置，请重新登录", null));
        mockMvc.perform(post("/auth/reset-password-by-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"code\":\"123456\",\"newPassword\":\"NewPass123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(authService).resetPasswordByEmail(eq("a@b.com"), eq("123456"), eq("NewPass123!"));
    }

    // ===================== POST /auth/verify-code =====================

    @Test
    @DisplayName("POST /auth/verify-code - 合法请求委托 verifyCode 并返回200")
    void verifyCode_ok() throws Exception {
        when(authService.verifyCode(eq("a@b.com"), eq("123456")))
                .thenReturn(ApiResponse.success("验证码正确", null));
        mockMvc.perform(post("/auth/verify-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(authService).verifyCode(eq("a@b.com"), eq("123456"));
    }

    @Test
    @DisplayName("POST /auth/verify-code - 缺验证码被 @Valid 拦截返回400")
    void verifyCode_missingCode_returns400() throws Exception {
        mockMvc.perform(post("/auth/verify-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\"}"))
                .andExpect(status().isBadRequest());
        verify(authService, never()).verifyCode(anyString(), anyString());
    }
}
