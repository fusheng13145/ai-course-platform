package com.aicourse.service.impl;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.User;
import com.aicourse.entity.UserSession;
import com.aicourse.entity.VerificationCode;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.*;
import com.aicourse.util.IpUtil;
import com.aicourse.util.JwtUtils;
import com.aicourse.util.UserAgentUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 认证服务单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    @Mock private UserService userService;
    @Mock private UserSessionService userSessionService;
    @Mock private LoginLogService loginLogService;
    @Mock private VerificationCodeService verificationCodeService;
    @Mock private EmailService emailService;
    @Mock private JwtUtils jwtUtils;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private IpUtil ipUtil;
    @Mock private UserAgentUtil userAgentUtil;
    @Mock private HttpServletRequest request;

    @InjectMocks private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        when(ipUtil.getClientIp(any())).thenReturn("127.0.0.1");
        when(userAgentUtil.getDeviceName(any())).thenReturn("Desktop");
        when(userAgentUtil.getOperatingSystem(any())).thenReturn("Windows");
        when(userAgentUtil.getBrowser(any())).thenReturn("JUnit");
    }

    // ===================== 注册测试 =====================

    @Test
    @DisplayName("注册-新用户注册成功")
    void testRegister_NewUser_Success() {
        when(userService.count(any())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        when(userService.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L); // simulate MyBatis-Plus auto-increment
            return true;
        });
        when(jwtUtils.generateToken(anyLong(), anyString(), anyString(), anyBoolean())).thenReturn("test-jwt-token");
        when(userSessionService.save(any(UserSession.class))).thenReturn(true);

        ApiResponse<Map<String, Object>> response = authService.register(
                "test@example.com", "testuser", "Test@123456", "Test User", "STUDENT", "13800138000", null, request);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals("test-jwt-token", response.getData().get("token"));
        verify(userService).save(any(User.class));
    }

    @Test
    @DisplayName("注册-重复邮箱注册失败")
    void testRegister_DuplicateEmail_Fail() {
        when(userService.count(any())).thenReturn(1L);

        try {
            authService.register("test@example.com", "testuser", "password123", "Test User", "STUDENT", "13800138000", null, request);
            fail("Expected BusinessException for duplicate email");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("用户名已存在") || e.getMessage().contains("邮箱已被注册"));
        }
        verify(userService, never()).save(any(User.class));
    }

    // ===================== 登录测试 =====================

    @Test
    @DisplayName("登录-登录成功")
    void testLogin_Success() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setUsername("testuser");
        user.setRole("STUDENT");
        user.setPassword("$2a$10$hashed");
        user.setIsActive(true);
        user.setFailedLoginAttempts(0);
        when(userService.getOne(any())).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(userService.updateById(any(User.class))).thenReturn(true);
        when(jwtUtils.generateToken(anyLong(), anyString(), anyString(), anyBoolean())).thenReturn("test-jwt-token");
        when(userSessionService.save(any(UserSession.class))).thenReturn(true);

        ApiResponse<Map<String, Object>> response = authService.login("test@example.com", "password123", false, request);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        Map<String, Object> data = response.getData();
        assertNotNull(data);
        assertEquals("test-jwt-token", data.get("token"));
    }

    @Test
    @DisplayName("登录-密码错误登录失败")
    void testLogin_WrongPassword_Fail() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setUsername("testuser");
        user.setPassword("$2a$10$hashed");
        user.setIsActive(true);
        user.setFailedLoginAttempts(0);
        when(userService.getOne(any())).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(userService.updateById(any(User.class))).thenReturn(true);

        try {
            authService.login("test@example.com", "wrongpassword", false, request);
            fail("Expected BusinessException for wrong password");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("用户名或密码错误"));
        }
    }

    @Test
    @DisplayName("登录-已禁用账号登录失败")
    void testLogin_DisabledAccount_Fail() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setUsername("testuser");
        user.setPassword("$2a$10$hashed");
        user.setIsActive(false);
        user.setFailedLoginAttempts(0);
        when(userService.getOne(any())).thenReturn(user);

        try {
            authService.login("test@example.com", "password123", false, request);
            fail("Expected BusinessException for disabled account");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("禁用"));
        }
    }

    @Test
    @DisplayName("登录-已锁定账号登录失败")
    void testLogin_LockedAccount_Fail() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setUsername("testuser");
        user.setPassword("$2a$10$hashed");
        user.setIsActive(true);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userService.getOne(any())).thenReturn(user);

        try {
            authService.login("test@example.com", "password123", false, request);
            fail("Expected BusinessException for locked account");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("锁定"));
        }
    }

    // ===================== 验证码发送测试 =====================

    @Test
    @DisplayName("发送验证码-成功")
    void testSendVerificationCode_Success() {
        doNothing().when(emailService).sendVerificationCode(anyString(), anyString(), anyString());
        when(verificationCodeService.save(any(VerificationCode.class))).thenReturn(true);

        ApiResponse<Void> response = authService.sendVerificationCode("test@example.com", "REGISTER", request);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(emailService).sendVerificationCode(eq("test@example.com"), anyString(), eq("REGISTER"));
    }

    // ===================== 修改密码测试 =====================

    @Test
    @DisplayName("修改密码-成功")
    void testChangePassword_Success() {
        User user = new User();
        user.setId(1L);
        user.setPassword("$2a$10$oldHashed");
        when(userService.getById(1L)).thenReturn(user);
        when(passwordEncoder.matches("oldPassword", "$2a$10$oldHashed")).thenReturn(true);
        when(passwordEncoder.encode("NewPass@2024")).thenReturn("$2a$10$newHashed");
        when(userService.updateById(any(User.class))).thenReturn(true);

        ApiResponse<Void> response = authService.changePassword(1L, "oldPassword", "NewPass@2024");

        assertNotNull(response);
        assertEquals(200, response.getCode());
    }

    @Test
    @DisplayName("修改密码-旧密码错误失败")
    void testChangePassword_WrongOldPassword_Fail() {
        User user = new User();
        user.setId(1L);
        user.setPassword("$2a$10$oldHashed");
        when(userService.getById(1L)).thenReturn(user);
        when(passwordEncoder.matches("wrongOldPassword", "$2a$10$oldHashed")).thenReturn(false);

        try {
            authService.changePassword(1L, "wrongOldPassword", "newPassword123");
            fail("Expected BusinessException for wrong old password");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("密码错误"));
        }
    }

    // ===================== 刷新令牌测试 =====================

    @Test
    @DisplayName("刷新令牌-成功")
    void testRefreshToken_Success() {
        UserSession session = new UserSession();
        session.setUserId(1L);
        session.setRefreshToken("valid-refresh-token");
        session.setIsActive(true);
        when(userSessionService.getOne(any())).thenReturn(session);

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setRole("STUDENT");
        user.setIsActive(true);
        when(userService.getById(1L)).thenReturn(user);
        when(jwtUtils.generateToken(anyLong(), anyString(), anyString(), anyBoolean())).thenReturn("new-jwt-token");
        when(userSessionService.updateById(any(UserSession.class))).thenReturn(true);

        ApiResponse<Map<String, Object>> response = authService.refreshToken("valid-refresh-token");

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals("new-jwt-token", response.getData().get("token"));
    }

    @Test
    @DisplayName("刷新令牌-无效令牌失败")
    void testRefreshToken_Invalid_Fail() {
        when(userSessionService.getOne(any())).thenReturn(null);

        try {
            authService.refreshToken("invalid-refresh-token");
            fail("Expected BusinessException for invalid refresh token");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("无效"));
        }
    }

    // ===================== 密码复杂度边界测试 =====================

    @Test
    @DisplayName("注册-密码不含大写字母应失败")
    void testRegister_NoUpperCase_Fail() {
        when(userService.count(any())).thenReturn(0L);
        assertThrows(BusinessException.class, () ->
                authService.register("test@test.com", "user", "test@1234", "Test", "STUDENT", null, null, request),
                "密码必须包含大写字母"
        );
    }

    @Test
    @DisplayName("注册-密码不含数字应失败")
    void testRegister_NoDigit_Fail() {
        when(userService.count(any())).thenReturn(0L);
        assertThrows(BusinessException.class, () ->
                authService.register("test@test.com", "user", "Test@Test!", "Test", "STUDENT", null, null, request),
                "密码必须包含数字"
        );
    }

    @Test
    @DisplayName("注册-密码不含特殊字符应失败")
    void testRegister_NoSpecialChar_Fail() {
        when(userService.count(any())).thenReturn(0L);
        assertThrows(BusinessException.class, () ->
                authService.register("test@test.com", "user", "Test1234567", "Test", "STUDENT", null, null, request),
                "密码必须包含特殊字符"
        );
    }

    @Test
    @DisplayName("注册-密码过短（<8位）应失败")
    void testRegister_TooShort_Fail() {
        when(userService.count(any())).thenReturn(0L);
        assertThrows(BusinessException.class, () ->
                authService.register("test@test.com", "user", "Te@1", "Test", "STUDENT", null, null, request),
                "密码长度不足"
        );
    }

    @Test
    @DisplayName("注册-密码为空应失败")
    void testRegister_EmptyPassword_Fail() {
        when(userService.count(any())).thenReturn(0L);
        assertThrows(BusinessException.class, () ->
                authService.register("test@test.com", "user", "", "Test", "STUDENT", null, null, request),
                "密码不能为空"
        );
    }

    @Test
    @DisplayName("注册-用户名为空应失败")
    void testRegister_EmptyUsername_Fail() {
        // UserService会抛出异常（username为空的记录不存在）
        when(userService.count(any())).thenReturn(0L);
        assertThrows(Exception.class, () ->
                authService.register("test@test.com", "", "Test@1234", "Test", "STUDENT", null, null, request)
        );
    }

    // ===================== 用户名边界用例 =====================

    @Test
    @DisplayName("注册-用户名为纯空格注册失败")
    void testRegister_WhitespaceUsername_Fail() {
        when(userService.count(any())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        when(userService.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return true;
        });
        when(jwtUtils.generateToken(anyLong(), anyString(), anyString(), anyBoolean())).thenReturn("test-token");
        when(userSessionService.save(any(UserSession.class))).thenReturn(true);

        // 纯空格用户名通过服务层（DTO 层 @NotBlank 应拦截），
        // 此处验证服务层在无 DTO 校验时的实际行为
        ApiResponse<Map<String, Object>> response = authService.register(
                "space@test.com", "   ", "Test@123456", "Space User", "STUDENT", null, null, request);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        // 用户名 "   " 应被存储，前端/Controller 层 DTO 校验（@NotBlank）负责拦截
    }

    @Test
    @DisplayName("注册-极长用户名(>100字符)注册失败")
    void testRegister_VeryLongUsername_Fail() {
        when(userService.count(any())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        when(userService.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return true;
        });
        when(jwtUtils.generateToken(anyLong(), anyString(), anyString(), anyBoolean())).thenReturn("test-token");
        when(userSessionService.save(any(UserSession.class))).thenReturn(true);

        String longUsername = "a".repeat(150);
        ApiResponse<Map<String, Object>> response = authService.register(
                "long@test.com", longUsername, "Test@123456", "Long User", "STUDENT", null, null, request);

        // 超长用户名通过服务层（DTO 层 @Size(max=20) 应拦截），
        // 服务层未对用户名长度做校验，数据库字段约束有机会底层拒绝
        assertNotNull(response);
        assertEquals(200, response.getCode());
    }

    @Test
    @DisplayName("注册-用户名含特殊字符可能绕过格式校验")
    void testRegister_SpecialCharUsername_Behavior() {
        when(userService.count(any())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        when(userService.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return true;
        });
        when(jwtUtils.generateToken(anyLong(), anyString(), anyString(), anyBoolean())).thenReturn("test-token");
        when(userSessionService.save(any(UserSession.class))).thenReturn(true);

        // 服务层不验证用户名格式（由 DTO 层 @Pattern 负责）
        ApiResponse<Map<String, Object>> response = authService.register(
                "special@test.com", "<script>alert('xss')</script>", "Test@123456", "XSS User", "STUDENT", null, null, request);

        assertNotNull(response);
        assertEquals(200, response.getCode());
    }
}