package com.aicourse.service.component.auth;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.ClientInfo;
import com.aicourse.entity.User;
import com.aicourse.entity.UserSession;
import com.aicourse.entity.VerificationCode;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.LoginLogService;
import com.aicourse.service.UserService;
import com.aicourse.service.UserSessionService;
import com.aicourse.service.VerificationCodeService;
import com.aicourse.util.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 注册路径「验证码强制校验」回归测试。
 *
 * <p><b>缺陷背景（2026-09-16 上线前全检阻塞项 B1）</b>：{@code AuthSessionComponent#register}
 * 原实现为 {@code if (code != null)} 的<b>可选</b>分支——请求体省略 {@code code} 即可跳过整段
 * 验证码校验，直接建号并下发立即生效的认证 Cookie，攻击者可零成本批量自建已认证账号。
 * 该缺陷已在运行时实测复现（带 {@code email}、不含 {@code code} 的 {@code POST /auth/register}
 * 返回 200「注册成功」且新账号可立即登录）。</p>
 *
 * <p><b>本测试锁定修复后的行为</b>：缺失 / 空白 / 无效验证码一律拒绝，且<b>不得产生任何用户或会话副作用</b>；
 * 末尾保留一条正向用例，确保修复未过度收紧以致合法注册被误拒。</p>
 *
 * <p>修复对应改动：{@code AuthSessionComponent#register}（判空反转为「缺失即拒绝」）+
 * {@code UserRegisterDTO#code}（补 {@code @NotBlank}，第一道拦截）。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthSessionRegisterTest {

    @Mock
    private UserService userService;
    @Mock
    private UserSessionService userSessionService;
    @Mock
    private VerificationCodeService verificationCodeService;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LoginLogService loginLogService;
    @Mock
    private AuthPasswordComponent passwordComponent;

    private AuthSessionComponent component;

    /** 固定客户端上下文（不可变值对象）。 */
    private static final ClientInfo CLIENT =
            new ClientInfo("127.0.0.1", "JUnit-Agent", "JUnit", "JUnit", "JUnit");

    @BeforeEach
    void setUp() {
        component = new AuthSessionComponent(userService, userSessionService, verificationCodeService,
                jwtUtils, passwordEncoder, loginLogService, passwordComponent);
    }

    // ===================== 负向：验证码缺失 / 空白 / 无效必须拒绝 =====================

    @Test
    @DisplayName("注册-缺失验证码必须拒绝，且不得落库、不得签发会话（B1 回归）")
    void register_codeMissing_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> component.register("newbie@aicourse.com", "newbie", "Password123",
                        "新同学", "STUDENT", "13800001111", null, CLIENT),
                "省略 code 必须被拒绝（此前会跳过校验并注册成功）");

        assertEquals(400, ex.getCode(), "应以 400 参数错误返回");
        assertTrue(ex.getMessage().contains("验证码"),
                "异常信息应指向验证码缺失，实际=" + ex.getMessage());
        // 核心断言：拒绝必须发生在建号之前，不得留下任何副作用
        verify(userService, never()).save(any(User.class));
        verify(userSessionService, never()).save(any(UserSession.class));
        verify(jwtUtils, never()).generateToken(any(), anyString(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("注册-验证码为空白串同样拒绝（防 isBlank 绕过）")
    void register_codeBlank_rejected() {
        assertThrows(BusinessException.class,
                () -> component.register("newbie@aicourse.com", "newbie", "Password123",
                        "新同学", "STUDENT", "13800001111", "   ", CLIENT),
                "纯空白 code 必须被拒绝");

        verify(userService, never()).save(any(User.class));
        verify(userSessionService, never()).save(any(UserSession.class));
    }

    @Test
    @DisplayName("注册-验证码无效或已过期必须拒绝（查无有效记录）")
    void register_codeInvalid_rejected() {
        when(verificationCodeService.getOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> component.register("newbie@aicourse.com", "newbie", "Password123",
                        "新同学", "STUDENT", "13800001111", "000000", CLIENT),
                "无效验证码必须被拒绝");

        assertTrue(ex.getMessage().contains("验证码"),
                "异常信息应指向验证码无效，实际=" + ex.getMessage());
        verify(userService, never()).save(any(User.class));
    }

    // ===================== 正向：有效验证码下注册仍应正常（防过度收紧） =====================

    @Test
    @DisplayName("注册-提供有效验证码时应正常建号并标记验证码已用（防过度收紧）")
    void register_validCode_succeeds() {
        VerificationCode validCode = new VerificationCode();
        validCode.setId(1L);
        validCode.setEmail("newbie@aicourse.com");
        validCode.setCode("123456");
        when(verificationCodeService.getOne(any())).thenReturn(validCode);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encoded");
        when(userService.save(any(User.class))).thenReturn(true);
        when(jwtUtils.generateToken(any(), anyString(), anyString(), anyBoolean())).thenReturn("jwt-token");
        // 注意：JwtUtils#sha256Hex 是 **static** 方法（JwtUtils.java:181），无法被 Mockito 打桩；
        // 对该静态方法的 when(...) 打桩会留下悬空 ArgumentMatcher，抛 InvalidUseOfMatchersException。
        // 生产代码经实例引用调用该静态方法时仍执行真实实现，故此处无需也不可以打桩。

        ApiResponse<Map<String, Object>> result = component.register("newbie@aicourse.com", "newbie",
                "Password123", "新同学", "STUDENT", "13800001111", "123456", CLIENT);

        assertNotNull(result);
        assertEquals("注册成功", result.getMessage());
        verify(userService).save(any(User.class));
        // 验证码一次性：成功消费后必须置为已用，防止同一码被重复注册
        assertTrue(Boolean.TRUE.equals(validCode.getIsUsed()), "验证码应被标记为已使用");
        verify(verificationCodeService).updateById(validCode);
    }
}
