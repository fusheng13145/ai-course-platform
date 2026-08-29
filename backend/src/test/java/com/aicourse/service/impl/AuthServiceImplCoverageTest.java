package com.aicourse.service.impl;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.LoginLog;
import com.aicourse.entity.User;
import com.aicourse.entity.UserSession;
import com.aicourse.entity.VerificationCode;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.EmailService;
import com.aicourse.service.LoginLogService;
import com.aicourse.service.UserService;
import com.aicourse.service.UserSessionService;
import com.aicourse.service.VerificationCodeService;
import com.aicourse.util.IpUtil;
import com.aicourse.util.JwtUtils;
import com.aicourse.util.UserAgentUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 认证授权服务单元测试：最大化 INSTRUCTION 覆盖（纯 Mockito，构造注入协作者）。
 * 覆盖登录/注册/刷新/登出/改密/找回/发送验证码/邮箱找回/校验验证码的全部成功与错误分支，
 * 以及验证码爆破防护限流（Redis 存在与不存在两种构造）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplCoverageTest {

    @Mock UserService userService;
    @Mock UserSessionService userSessionService;
    @Mock LoginLogService loginLogService;
    @Mock VerificationCodeService verificationCodeService;
    @Mock EmailService emailService;
    @Mock JwtUtils jwtUtils;
    @Mock PasswordEncoder passwordEncoder;
    @Mock IpUtil ipUtil;
    @Mock UserAgentUtil userAgentUtil;
    @Mock ObjectProvider<StringRedisTemplate> redisProvider;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock HttpServletRequest request;

    private static final String STRONG_PWD = "Passw0rd!";

    private AuthServiceImpl buildService(boolean redisActive) {
        // 必须在构造 AuthServiceImpl 之前桩 getIfAvailable()，否则构造时调用会返回 null（redisTemplate 恒为 null）
        when(redisProvider.getIfAvailable()).thenReturn(redisActive ? redisTemplate : null);
        AuthServiceImpl svc = new AuthServiceImpl(userService, userSessionService, loginLogService,
                verificationCodeService, emailService, jwtUtils, passwordEncoder, ipUtil, userAgentUtil, redisProvider);
        // 通用 stub
        when(ipUtil.getClientIp(any())).thenReturn("127.0.0.1");
        when(userAgentUtil.getDeviceName(any())).thenReturn("Computer");
        when(userAgentUtil.getBrowser(any())).thenReturn("Chrome");
        when(userAgentUtil.getOperatingSystem(any())).thenReturn("Windows");
        when(request.getHeader("User-Agent")).thenReturn("UA");
        when(loginLogService.save(any(LoginLog.class))).thenReturn(true);
        when(userSessionService.save(any(UserSession.class))).thenReturn(true);
        when(jwtUtils.generateToken(any(), any(), any(), anyBoolean())).thenReturn("jwt-token");
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        return svc;
    }

    private User activeUser() {
        User u = new User();
        u.setId(1L);
        u.setUsername("alice");
        u.setEmail("alice@b.com");
        u.setPhone("13800000000");
        u.setPassword("encoded");
        u.setRole(RoleConstants.STUDENT);
        u.setIsActive(true);
        u.setFailedLoginAttempts(0);
        return u;
    }

    // ===================== login =====================
    @Test
    @DisplayName("login: 用户不存在")
    void login_userNotFound() {
        AuthServiceImpl svc = buildService(false);
        when(userService.getOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.login("nobody", "x", false, request));
        assertTrue(ex.getMessage().contains("用户名或密码错误"));
    }

    @Test
    @DisplayName("login: 账号已禁用")
    void login_inactive() {
        AuthServiceImpl svc = buildService(false);
        User u = activeUser();
        u.setIsActive(false);
        when(userService.getOne(any())).thenReturn(u);
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.login("alice", "x", false, request));
        assertTrue(ex.getMessage().contains("禁用"));
    }

    @Test
    @DisplayName("login: 账号已锁定")
    void login_locked() {
        AuthServiceImpl svc = buildService(false);
        User u = activeUser();
        u.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userService.getOne(any())).thenReturn(u);
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.login("alice", "x", false, request));
        assertTrue(ex.getMessage().contains("锁定"));
    }

    @Test
    @DisplayName("login: 密码错误（未达锁定阈值）")
    void login_wrongPasswordNoLock() {
        AuthServiceImpl svc = buildService(false);
        User u = activeUser();
        u.setFailedLoginAttempts(0);
        when(userService.getOne(any())).thenReturn(u);
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        when(userService.updateById(any())).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.login("alice", "bad", false, request));
        assertTrue(ex.getMessage().contains("用户名或密码错误"));
        assertEquals(1, u.getFailedLoginAttempts());
    }

    @Test
    @DisplayName("login: 密码错误（达锁定阈值 -> 锁定账号）")
    void login_wrongPasswordLock() {
        AuthServiceImpl svc = buildService(false);
        User u = activeUser();
        u.setFailedLoginAttempts(4);
        when(userService.getOne(any())).thenReturn(u);
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        when(userService.updateById(any())).thenReturn(true);
        assertThrows(BusinessException.class, () -> svc.login("alice", "bad", false, request));
        assertNotNull(u.getLockedUntil());
    }

    @Test
    @DisplayName("login: 成功")
    void login_success() {
        AuthServiceImpl svc = buildService(false);
        User u = activeUser();
        when(userService.getOne(any())).thenReturn(u);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(userService.updateById(any())).thenReturn(true);
        ApiResponse<?> resp = svc.login("alice", "Passw0rd!", true, request);
        assertEquals(200, resp.getCode());
        assertNotNull(resp.getData());
        verify(userSessionService).save(any(UserSession.class));
    }

    // ===================== register =====================
    @Test
    @DisplayName("register: 用户名为空")
    void register_emptyUsername() {
        AuthServiceImpl svc = buildService(false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.register("a@b.com", "", STRONG_PWD, "Al", "STUDENT", null, "123456", request));
        assertTrue(ex.getMessage().contains("用户名不能为空"));
    }

    @Test
    @DisplayName("register: 用户名已存在")
    void register_usernameExists() {
        AuthServiceImpl svc = buildService(false);
        when(userService.count(any())).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.register("a@b.com", "bob", STRONG_PWD, "Al", "STUDENT", null, "123456", request));
        assertTrue(ex.getMessage().contains("用户名已存在"));
    }

    @Test
    @DisplayName("register: 邮箱已注册")
    void register_emailExists() {
        AuthServiceImpl svc = buildService(false);
        when(userService.count(any())).thenReturn(0L, 1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.register("a@b.com", "bob", STRONG_PWD, "Al", "STUDENT", null, "123456", request));
        assertTrue(ex.getMessage().contains("邮箱已被注册"));
    }

    @Test
    @DisplayName("register: 手机号已注册")
    void register_phoneExists() {
        AuthServiceImpl svc = buildService(false);
        when(userService.count(any())).thenReturn(0L, 0L, 1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.register("a@b.com", "bob", STRONG_PWD, "Al", "STUDENT", "13800000000", "123456", request));
        assertTrue(ex.getMessage().contains("手机号已被注册"));
    }

    @Test
    @DisplayName("register: 弱密码 -> 复杂度校验失败")
    void register_weakPassword() {
        AuthServiceImpl svc = buildService(false);
        when(userService.count(any())).thenReturn(0L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.register("a@b.com", "bob", "abc", "Al", "STUDENT", null, "123456", request));
        assertTrue(ex.getMessage().contains("密码必须包含"));
    }

    @Test
    @DisplayName("register: 验证码无效")
    void register_invalidCode() {
        AuthServiceImpl svc = buildService(false);
        when(userService.count(any())).thenReturn(0L);
        when(verificationCodeService.getOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.register("a@b.com", "bob", STRONG_PWD, "Al", "STUDENT", null, "999999", request));
        assertTrue(ex.getMessage().contains("验证码无效"));
    }

    @Test
    @DisplayName("register: 验证码有效 -> 成功")
    void register_codeValid() {
        AuthServiceImpl svc = buildService(false);
        when(userService.count(any())).thenReturn(0L);
        VerificationCode vc = new VerificationCode();
        vc.setId(1L);
        vc.setIsUsed(false);
        when(verificationCodeService.getOne(any())).thenReturn(vc);
        when(verificationCodeService.updateById(any())).thenReturn(true);
        when(userService.save(any())).thenReturn(true);
        ApiResponse<?> resp = svc.register("a@b.com", "bob", STRONG_PWD, "Al", "STUDENT", null, "123456", request);
        assertEquals(200, resp.getCode());
        verify(userService).save(any(User.class));
    }

    @Test
    @DisplayName("register: 无验证码 -> 直接成功")
    void register_noCode() {
        AuthServiceImpl svc = buildService(false);
        when(userService.count(any())).thenReturn(0L);
        when(userService.save(any())).thenReturn(true);
        ApiResponse<?> resp = svc.register("a@b.com", "bob", STRONG_PWD, "Al", "STUDENT", null, null, request);
        assertEquals(200, resp.getCode());
    }

    // ===================== refreshToken =====================
    @Test
    @DisplayName("refreshToken: 会话不存在")
    void refresh_sessionNull() {
        AuthServiceImpl svc = buildService(false);
        when(userSessionService.getOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> svc.refreshToken("rt"));
    }

    @Test
    @DisplayName("refreshToken: 用户不存在/禁用")
    void refresh_userNull() {
        AuthServiceImpl svc = buildService(false);
        UserSession s = new UserSession();
        s.setUserId(1L);
        s.setSessionToken("tok");
        s.setRefreshToken("rh");
        when(userSessionService.getOne(any())).thenReturn(s);
        when(userService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> svc.refreshToken("rt"));
    }

    @Test
    @DisplayName("refreshToken: 成功（轮换刷新令牌）")
    void refresh_success() {
        AuthServiceImpl svc = buildService(false);
        UserSession s = new UserSession();
        s.setUserId(1L);
        s.setSessionToken("tok");
        s.setRefreshToken("rh");
        when(userSessionService.getOne(any())).thenReturn(s);
        User u = activeUser();
        when(userService.getById(1L)).thenReturn(u);
        when(userSessionService.updateById(any())).thenReturn(true);
        ApiResponse<?> resp = svc.refreshToken("rt");
        assertEquals(200, resp.getCode());
        verify(userSessionService).evictSessionCache("tok");
        verify(userSessionService).updateById(s);
    }

    // ===================== logout =====================
    @Test
    @DisplayName("logout: 成功（失效所有活跃会话）")
    void logout_success() {
        AuthServiceImpl svc = buildService(false);
        UserSession s = new UserSession();
        s.setSessionToken("tok");
        when(userSessionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        LambdaUpdateChainWrapper<UserSession> chain = mock(LambdaUpdateChainWrapper.class);
        when(userSessionService.lambdaUpdate()).thenReturn(chain);
        when(chain.eq(any(), any())).thenReturn(chain);
        when(chain.set(any(), any())).thenReturn(chain);
        when(chain.update()).thenReturn(true);
        ApiResponse<?> resp = svc.logout(1L, request);
        assertEquals(200, resp.getCode());
        verify(userSessionService).evictSessionCache("tok");
    }

    // ===================== resetPassword (phone) =====================
    @Test
    @DisplayName("resetPassword(phone): 验证码无效")
    void resetPhone_invalidCode() {
        AuthServiceImpl svc = buildService(false);
        when(verificationCodeService.getOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> svc.resetPassword("13800000000", "000000", STRONG_PWD));
    }

    @Test
    @DisplayName("resetPassword(phone): 手机号未注册")
    void resetPhone_userNull() {
        AuthServiceImpl svc = buildService(false);
        VerificationCode vc = new VerificationCode();
        vc.setIsUsed(false);
        when(verificationCodeService.getOne(any())).thenReturn(vc);
        when(verificationCodeService.updateById(any())).thenReturn(true);
        when(userService.getOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> svc.resetPassword("13800000000", "123456", STRONG_PWD));
    }

    @Test
    @DisplayName("resetPassword(phone): 成功")
    void resetPhone_success() {
        AuthServiceImpl svc = buildService(false);
        VerificationCode vc = new VerificationCode();
        vc.setIsUsed(false);
        when(verificationCodeService.getOne(any())).thenReturn(vc);
        when(verificationCodeService.updateById(any())).thenReturn(true);
        User u = activeUser();
        when(userService.getOne(any())).thenReturn(u);
        when(userService.updateById(any())).thenReturn(true);
        ApiResponse<?> resp = svc.resetPassword("13800000000", "123456", STRONG_PWD);
        assertEquals(200, resp.getCode());
    }

    // ===================== changePassword =====================
    @Test
    @DisplayName("changePassword: 用户不存在")
    void change_userNull() {
        AuthServiceImpl svc = buildService(false);
        when(userService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> svc.changePassword(1L, "old", STRONG_PWD));
    }

    @Test
    @DisplayName("changePassword: 原密码错误")
    void change_wrongOld() {
        AuthServiceImpl svc = buildService(false);
        when(userService.getById(1L)).thenReturn(activeUser());
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        assertThrows(BusinessException.class, () -> svc.changePassword(1L, "old", STRONG_PWD));
    }

    @Test
    @DisplayName("changePassword: 成功")
    void change_success() {
        AuthServiceImpl svc = buildService(false);
        when(userService.getById(1L)).thenReturn(activeUser());
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(userService.updateById(any())).thenReturn(true);
        ApiResponse<?> resp = svc.changePassword(1L, "old", STRONG_PWD);
        assertEquals(200, resp.getCode());
    }

    // ===================== getCurrentUser =====================
    @Test
    @DisplayName("getCurrentUser: 用户不存在")
    void getCurrent_null() {
        AuthServiceImpl svc = buildService(false);
        when(userService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> svc.getCurrentUser(1L));
    }

    @Test
    @DisplayName("getCurrentUser: 成功")
    void getCurrent_success() {
        AuthServiceImpl svc = buildService(false);
        when(userService.getById(1L)).thenReturn(activeUser());
        ApiResponse<?> resp = svc.getCurrentUser(1L);
        assertEquals(200, resp.getCode());
    }

    // ===================== sendVerificationCode =====================
    @Test
    @DisplayName("sendVerificationCode: 频率限制")
    void sendVC_rateLimited() {
        AuthServiceImpl svc = buildService(false);
        when(verificationCodeService.count(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> svc.sendVerificationCode("a@b.com", "REGISTER", request));
    }

    @Test
    @DisplayName("sendVerificationCode: 成功")
    void sendVC_success() {
        AuthServiceImpl svc = buildService(false);
        when(verificationCodeService.count(any())).thenReturn(0L);
        when(verificationCodeService.save(any())).thenReturn(true);
        ApiResponse<?> resp = svc.sendVerificationCode("a@b.com", "REGISTER", request);
        assertEquals(200, resp.getCode());
        verify(emailService).sendVerificationCode(eq("a@b.com"), anyString(), eq("REGISTER"));
    }

    // ===================== resetPasswordByEmail（含爆破防护限流） =====================
    @Test
    @DisplayName("resetPasswordByEmail: 无 Redis -> 限流早退，正常流程")
    void resetEmail_noRedis() {
        AuthServiceImpl svc = buildService(false);
        VerificationCode vc = new VerificationCode();
        vc.setIsUsed(false);
        when(verificationCodeService.getOne(any())).thenReturn(vc);
        when(verificationCodeService.updateById(any())).thenReturn(true);
        User u = activeUser();
        when(userService.getOne(any())).thenReturn(u);
        when(userService.updateById(any())).thenReturn(true);
        ApiResponse<?> resp = svc.resetPasswordByEmail("a@b.com", "123456", STRONG_PWD);
        assertEquals(200, resp.getCode());
    }

    @Test
    @DisplayName("resetPasswordByEmail: 限流超限(>5) -> 429")
    void resetEmail_rateLimited() {
        AuthServiceImpl svc = buildService(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(6L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.resetPasswordByEmail("a@b.com", "123456", STRONG_PWD));
        assertEquals(429, ex.getCode());
    }

    @Test
    @DisplayName("resetPasswordByEmail: 验证码无效")
    void resetEmail_invalidCode() {
        AuthServiceImpl svc = buildService(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any())).thenReturn(true);
        when(verificationCodeService.getOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> svc.resetPasswordByEmail("a@b.com", "000000", STRONG_PWD));
    }

    @Test
    @DisplayName("resetPasswordByEmail: 邮箱未注册")
    void resetEmail_userNull() {
        AuthServiceImpl svc = buildService(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any())).thenReturn(true);
        VerificationCode vc = new VerificationCode();
        vc.setIsUsed(false);
        when(verificationCodeService.getOne(any())).thenReturn(vc);
        when(verificationCodeService.updateById(any())).thenReturn(true);
        when(userService.getOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> svc.resetPasswordByEmail("a@b.com", "123456", STRONG_PWD));
    }

    @Test
    @DisplayName("resetPasswordByEmail: 成功（有 Redis 限流计数）")
    void resetEmail_success() {
        AuthServiceImpl svc = buildService(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(2L);
        when(redisTemplate.expire(anyString(), any())).thenReturn(true);
        VerificationCode vc = new VerificationCode();
        vc.setIsUsed(false);
        when(verificationCodeService.getOne(any())).thenReturn(vc);
        when(verificationCodeService.updateById(any())).thenReturn(true);
        when(userService.getOne(any())).thenReturn(activeUser());
        when(userService.updateById(any())).thenReturn(true);
        ApiResponse<?> resp = svc.resetPasswordByEmail("a@b.com", "123456", STRONG_PWD);
        assertEquals(200, resp.getCode());
    }

    // ===================== verifyCode（含爆破防护限流） =====================
    @Test
    @DisplayName("verifyCode: 无 Redis -> 限流早退，校验成功")
    void verifyCode_noRedis() {
        AuthServiceImpl svc = buildService(false);
        VerificationCode vc = new VerificationCode();
        vc.setIsUsed(false);
        when(verificationCodeService.getOne(any())).thenReturn(vc);
        ApiResponse<?> resp = svc.verifyCode("a@b.com", "123456");
        assertEquals(200, resp.getCode());
    }

    @Test
    @DisplayName("verifyCode: 限流超限 -> 429")
    void verifyCode_rateLimited() {
        AuthServiceImpl svc = buildService(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(6L);
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.verifyCode("a@b.com", "123456"));
        assertEquals(429, ex.getCode());
    }

    @Test
    @DisplayName("verifyCode: 验证码无效")
    void verifyCode_invalid() {
        AuthServiceImpl svc = buildService(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any())).thenReturn(true);
        when(verificationCodeService.getOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> svc.verifyCode("a@b.com", "000000"));
    }

    @Test
    @DisplayName("verifyCode: 成功")
    void verifyCode_success() {
        AuthServiceImpl svc = buildService(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(3L);
        when(redisTemplate.expire(anyString(), any())).thenReturn(true);
        VerificationCode vc = new VerificationCode();
        vc.setIsUsed(false);
        when(verificationCodeService.getOne(any())).thenReturn(vc);
        ApiResponse<?> resp = svc.verifyCode("a@b.com", "123456");
        assertEquals(200, resp.getCode());
    }

    // ===================== assertVerifyAttemptAllowed 边界 =====================
    @Test
    @DisplayName("assertVerifyAttemptAllowed: email 为 null/blank -> 早退不抛异常")
    void assertAttempt_emailBlank() {
        AuthServiceImpl svc = buildService(true);
        VerificationCode vc = new VerificationCode();
        vc.setIsUsed(false);
        // 让 getOne 返回有效验证码，避免后续抛出，从而验证限流在 email 为空时提前返回
        when(verificationCodeService.getOne(any())).thenReturn(vc);
        assertDoesNotThrow(() -> svc.verifyCode(null, "123456"));
        assertDoesNotThrow(() -> svc.verifyCode("   ", "123456"));
    }
}
