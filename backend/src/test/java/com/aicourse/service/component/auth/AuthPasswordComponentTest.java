package com.aicourse.service.component.auth;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.User;
import com.aicourse.entity.VerificationCode;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.UserService;
import com.aicourse.service.UserSessionService;
import com.aicourse.service.VerificationCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 改密 / 找回密码链路的<b>真实业务</b>单元测试（AuthPasswordComponent）。
 *
 * <p>控制器层（{@code AuthController}）对这些端点仅做委托，越权与失败语义的真正落地在本组件。
 * 此前该链路<b>零覆盖</b>：旧密码错误、验证码无效/过期、密码复杂度不达标等失败分支若发生副作用
 * （误改库、误吊销会话）将是严重安全事故。本测试的核心断言是：<b>任何失败都必须在写库之前抛出，
 * 且绝不产生 updateById / 会话吊销副作用</b>。</p>
 *
 * <p>满足 PasswordPolicy（8-32，大小写+数字+特殊字符）的合法新密码样例：{@code NewPass123!}。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthPasswordComponentTest {

    private static final String VALID_NEW = "NewPass123!";
    private static final String WEAK_NEW = "123";

    @Mock
    private UserService userService;
    @Mock
    private VerificationCodeService verificationCodeService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthVerificationComponent verificationComponent;
    @Mock
    private UserSessionService userSessionService;

    private AuthPasswordComponent component;

    @BeforeEach
    void setUp() {
        component = new AuthPasswordComponent(userService, verificationCodeService,
                passwordEncoder, verificationComponent, userSessionService);
    }

    private User user(Long id, String hashed) {
        User u = new User();
        u.setId(id);
        u.setPassword(hashed);
        return u;
    }

    private VerificationCode usedCode() {
        VerificationCode vc = new VerificationCode();
        vc.setIsUsed(false);
        return vc;
    }

    // ===================== 修改密码（已知原密码） =====================

    @Test
    @DisplayName("改密-用户不存在拒绝，且不落库、不吊销会话")
    void changePassword_userNotFound_noSideEffect() {
        when(userService.getById(7L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> component.changePassword(7L, "old", VALID_NEW));
        assertTrue(ex.getMessage().contains("用户不存在"), "实际=" + ex.getMessage());
        verify(userService, never()).updateById(any(User.class));
        verify(userSessionService, never()).revokeAllActiveSessions(anyLong());
    }

    @Test
    @DisplayName("改密-原密码错误必须拒绝且不修改数据库（关键安全回归）")
    void changePassword_oldPasswordWrong_noDbWrite() {
        when(userService.getById(7L)).thenReturn(user(7L, "$2a$oldhash"));
        when(passwordEncoder.matches("wrongOld", "$2a$oldhash")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> component.changePassword(7L, "wrongOld", VALID_NEW));
        assertTrue(ex.getMessage().contains("原密码错误"), "实际=" + ex.getMessage());
        // 抛出发生在 updateById 之前：密码绝不被改写，会话绝不被吊销
        verify(userService, never()).updateById(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
        verify(userSessionService, never()).revokeAllActiveSessions(anyLong());
    }

    @Test
    @DisplayName("改密-新密码复杂度不达标拒绝，原密码校验通过后仍不落库")
    void changePassword_complexityFail_noDbWrite() {
        when(userService.getById(7L)).thenReturn(user(7L, "$2a$oldhash"));
        when(passwordEncoder.matches("rightOld", "$2a$oldhash")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> component.changePassword(7L, "rightOld", WEAK_NEW));
        assertTrue(ex.getMessage().contains("密码"), "复杂度失败信息应指向密码规则，实际=" + ex.getMessage());
        verify(userService, never()).updateById(any(User.class));
        verify(userSessionService, never()).revokeAllActiveSessions(anyLong());
    }

    @Test
    @DisplayName("改密-成功：先落库改密再全量吊销会话（修复 #10b），返回重新登录文案")
    void changePassword_success_updatesAndRevokesSessions() {
        when(userService.getById(7L)).thenReturn(user(7L, "$2a$oldhash"));
        when(passwordEncoder.matches("rightOld", "$2a$oldhash")).thenReturn(true);
        when(passwordEncoder.encode(VALID_NEW)).thenReturn("$2a$newhash");
        when(userSessionService.revokeAllActiveSessions(7L)).thenReturn(3);

        ApiResponse<Void> resp = component.changePassword(7L, "rightOld", VALID_NEW);

        assertEquals(200, resp.getCode());
        assertTrue(resp.getMessage().contains("请重新登录"), "成功文案须提示重新登录，实际=" + resp.getMessage());
        // 新密码以 encode 结果落库
        verify(userService).updateById(org.mockito.ArgumentMatchers.argThat(
                u -> "$2a$newhash".equals(u.getPassword()) && u.getId().equals(7L)));
        // 改密后必须吊销全部活跃会话
        verify(userSessionService).revokeAllActiveSessions(7L);
    }

    @Test
    @DisplayName("改密-吊销会话异常不影响改密主结果（密码已改成功，业务应返回成功）")
    void changePassword_revokeThrows_stillSuccess() {
        when(userService.getById(7L)).thenReturn(user(7L, "$2a$oldhash"));
        when(passwordEncoder.matches("rightOld", "$2a$oldhash")).thenReturn(true);
        when(passwordEncoder.encode(VALID_NEW)).thenReturn("$2a$newhash");
        when(userSessionService.revokeAllActiveSessions(7L)).thenThrow(new RuntimeException("redis down"));

        ApiResponse<Void> resp = component.changePassword(7L, "rightOld", VALID_NEW);
        assertEquals(200, resp.getCode(), "吊销失败不得反噬改密结果");
        verify(userService).updateById(any(User.class));
    }

    // ===================== 手机号重置密码 =====================

    @Test
    @DisplayName("手机重置-验证码无效/过期拒绝，且不落库")
    void resetByPhone_invalidCode_noDbWrite() {
        when(verificationCodeService.getOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> component.resetPassword("13800001111", "000000", VALID_NEW));
        assertTrue(ex.getMessage().contains("验证码"), "实际=" + ex.getMessage());
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("手机重置-验证码有效但手机号未注册拒绝，且不落库")
    void resetByPhone_phoneNotRegistered_noDbWrite() {
        when(verificationCodeService.getOne(any())).thenReturn(usedCode());
        when(userService.getOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> component.resetPassword("13800001111", "123456", VALID_NEW));
        assertTrue(ex.getMessage().contains("手机号未注册"), "实际=" + ex.getMessage());
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("手机重置-成功：消费验证码、改密、吊销会话")
    void resetByPhone_success() {
        VerificationCode vc = usedCode();
        when(verificationCodeService.getOne(any())).thenReturn(vc);
        when(userService.getOne(any())).thenReturn(user(9L, "$2a$oldhash"));
        when(passwordEncoder.encode(VALID_NEW)).thenReturn("$2a$newhash");

        ApiResponse<Void> resp = component.resetPassword("13800001111", "123456", VALID_NEW);
        assertEquals(200, resp.getCode());
        assertTrue(Boolean.TRUE.equals(vc.getIsUsed()), "成功重置后验证码必须被标记为已使用");
        verify(verificationCodeService).updateById(vc);
        verify(userService).updateById(any(User.class));
        verify(userSessionService).revokeAllActiveSessions(9L);
    }

    // ===================== 邮箱重置密码 =====================

    @Test
    @DisplayName("邮箱重置-爆破防护前置：超限直接拒绝，连验证码都不查询")
    void resetByEmail_burstGuardThrows_beforeLookup() {
        org.mockito.Mockito.doThrow(new BusinessException(429, "尝试过于频繁"))
                .when(verificationComponent).assertVerifyAttemptAllowed(eq("a@b.com"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> component.resetPasswordByEmail("a@b.com", "123456", VALID_NEW));
        assertEquals(429, ex.getCode());
        verify(verificationCodeService, never()).getOne(any());
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("邮箱重置-验证码无效拒绝且不落库")
    void resetByEmail_invalidCode_noDbWrite() {
        when(verificationCodeService.getOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> component.resetPasswordByEmail("a@b.com", "000000", VALID_NEW));
        assertTrue(ex.getMessage().contains("验证码"), "实际=" + ex.getMessage());
        verify(userService, never()).updateById(any(User.class));
        verify(userSessionService, never()).revokeAllActiveSessions(anyLong());
    }

    @Test
    @DisplayName("邮箱重置-成功：解锁登录失败计数、改密、吊销会话")
    void resetByEmail_success_resetsLock() {
        when(verificationCodeService.getOne(any())).thenReturn(usedCode());
        User locked = user(9L, "$2a$oldhash");
        locked.setFailedLoginAttempts(5);
        locked.setLockedUntil(java.time.LocalDateTime.now());
        when(userService.getOne(any())).thenReturn(locked);
        when(passwordEncoder.encode(VALID_NEW)).thenReturn("$2a$newhash");

        ApiResponse<Void> resp = component.resetPasswordByEmail("a@b.com", "123456", VALID_NEW);
        assertEquals(200, resp.getCode());
        verify(userService).updateById(org.mockito.ArgumentMatchers.argThat(u ->
                Integer.valueOf(0).equals(u.getFailedLoginAttempts())
                        && u.getLockedUntil() == null
                        && "$2a$newhash".equals(u.getPassword())));
        verify(userSessionService).revokeAllActiveSessions(9L);
    }
}
