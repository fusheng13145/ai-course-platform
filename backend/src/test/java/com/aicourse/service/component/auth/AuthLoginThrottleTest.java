package com.aicourse.service.component.auth;

import com.aicourse.common.ClientInfo;
import com.aicourse.entity.User;
import com.aicourse.entity.UserSession;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录失败节流「按来源 IP + 账户」粒度的回归测试（P0-1，2026-09-16 上线前全检 #22）。
 *
 * <p><b>缺陷背景</b>：原 {@code AuthSessionComponent#login} 在密码错误累计达 5 次时对
 * <b>账户</b>置 {@code lockedUntil}（15 分钟）。任意知道目标用户名/邮箱的人，连发 5 次错误密码
 * 即可锁定该账号（含 admin），且可循环实施——构成可被任意用户触发的<b>账户锁定 DoS</b>。</p>
 *
 * <p><b>修复后契约</b>：失败计数按 {@code auth:login:fail:<ip>:<account>} 隔离（Redis），
 * 达阈值仅拒绝该 (IP, 账户) 组合；受害者由自身 IP 登录不受影响；窗口内正确密码亦拒绝（锁语义）；
 * 登录成功清除计数；Redis 缺失时降级放行。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthLoginThrottleTest {

    @Mock private UserService userService;
    @Mock private UserSessionService userSessionService;
    @Mock private VerificationCodeService verificationCodeService;
    @Mock private JwtUtils jwtUtils;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private LoginLogService loginLogService;
    @Mock private AuthPasswordComponent passwordComponent;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    /** 攻击者来源 IP。 */
    private static final ClientInfo ATTACKER = new ClientInfo("10.0.0.1", "A", "A", "A", "A");
    /** 受害者自身来源 IP（与攻击者不同）。 */
    private static final ClientInfo VICTIM = new ClientInfo("10.0.0.2", "V", "V", "V", "V");

    /** 直构带 Redis 的组件。 */
    private AuthSessionComponent component;

    private static User activeUser() {
        User u = new User();
        u.setId(1L);
        u.setUsername("victim");
        u.setEmail("victim@aicourse.com");
        u.setPassword("$2a$10$hashed");
        u.setRole("STUDENT");
        u.setIsActive(true);
        u.setFailedLoginAttempts(0);
        return u;
    }

    @BeforeEach
    void setUp() {
        component = new AuthSessionComponent(userService, userSessionService, verificationCodeService,
                jwtUtils, passwordEncoder, loginLogService, passwordComponent, redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ===================== 节流触发 =====================

    @Test
    @DisplayName("登录-同 IP 同账户失败达阈值：返回 429，且计数键含来源 IP（P0-1 回归）")
    void login_fifthFailureFromSameIp_returns429() {
        when(userService.getOne(any())).thenReturn(activeUser());
        when(valueOps.get(anyString())).thenReturn(null);      // 前置校验：尚未锁定
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(5L);  // 本次失败到达阈值
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> component.login("victim@aicourse.com", "wrong", false, ATTACKER));

        assertEquals(429, ex.getCode(), "达阈值应以 429 限流返回");
        // 关键：计数键必须内嵌来源 IP（按 IP 隔离，而非账户全局）
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), any());
        String key = keysCaptor.getValue().get(0);
        assertTrue(key.contains("10.0.0.1"),
                "计数键应含攻击者来源 IP，实际=" + key);
        assertTrue(key.contains("victim@aicourse.com"),
                "计数键应含账户，实际=" + key);
    }

    @Test
    @DisplayName("登录-已处节流窗口内：即便密码正确仍拒绝（429），且不进入密码校验")
    void login_alreadyThrottled_correctPasswordStillRejected() {
        when(userService.getOne(any())).thenReturn(activeUser());
        when(valueOps.get(anyString())).thenReturn("5");       // 已达阈值

        BusinessException ex = assertThrows(BusinessException.class,
                () -> component.login("victim@aicourse.com", "whatever", false, ATTACKER));

        assertEquals(429, ex.getCode());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    // ===================== 攻击者 / 受害者 IP 隔离 =====================

    @Test
    @DisplayName("登录-失败计数的键按来源 IP 分离：攻击者 IP 与受害者 IP 互不影响（DoS 消除）")
    void login_keyIsPerIpAccount_attackerIpDoesNotAffectVictimIp() {
        when(userService.getOne(any())).thenReturn(activeUser());
        when(valueOps.get(anyString())).thenReturn(null);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);   // 未达阈值，两次都正常失败
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> component.login("victim@aicourse.com", "wrong", false, ATTACKER));
        assertThrows(BusinessException.class,
                () -> component.login("victim@aicourse.com", "wrong", false, VICTIM));

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), keysCaptor.capture(), any());
        List<List<String>> keys = keysCaptor.getAllValues();
        String k0 = keys.get(0).get(0);
        String k1 = keys.get(1).get(0);
        assertNotEquals(k0, k1, "不同来源 IP 必须映射到不同计数键");
        assertTrue(k0.contains("10.0.0.1"), "攻击者键应含其 IP，实际=" + k0);
        assertTrue(k1.contains("10.0.0.2"), "受害者键应含其 IP，实际=" + k1);
    }

    // ===================== 成功后清零 =====================

    @Test
    @DisplayName("登录-成功后清除该 (IP, 账户) 的失败计数")
    void login_success_clearsCounter() {
        when(userService.getOne(any())).thenReturn(activeUser());
        when(valueOps.get(anyString())).thenReturn(null);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(userService.updateById(any(User.class))).thenReturn(true);
        when(jwtUtils.generateToken(any(), anyString(), anyString(), anyBoolean())).thenReturn("jwt-token");
        when(userSessionService.save(any(UserSession.class))).thenReturn(true);

        component.login("victim@aicourse.com", "right", false, VICTIM);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).delete(captor.capture());
        assertTrue(captor.getValue().contains("10.0.0.2"), "应清除受害者 IP 对应的计数键");
    }

    // ===================== 管理员/DB 锁定仍被尊重 =====================

    @Test
    @DisplayName("登录-DB 侧 lockedUntil 仍在未来时仍拒绝（保留管理员锁定能力）")
    void login_adminLockStillHonored() {
        User locked = activeUser();
        locked.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userService.getOne(any())).thenReturn(locked);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> component.login("victim@aicourse.com", "right", false, VICTIM));

        assertTrue(ex.getMessage().contains("锁定"), "账户级锁定应仍生效，实际=" + ex.getMessage());
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any());
    }

    // ===================== Redis 缺失降级 =====================

    @Test
    @DisplayName("登录-Redis 缺失时降级放行：错误密码返回通用 400，不做节流计数")
    void login_redisAbsent_degradesGracefully() {
        AuthSessionComponent noRedis = new AuthSessionComponent(userService, userSessionService,
                verificationCodeService, jwtUtils, passwordEncoder, loginLogService, passwordComponent, null);
        when(userService.getOne(any())).thenReturn(activeUser());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> noRedis.login("victim@aicourse.com", "wrong", false, ATTACKER));

        assertEquals(400, ex.getCode(), "无 Redis 时返回通用参数错误，而非 429");
        assertTrue(ex.getMessage().contains("用户名或密码错误"));
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any());
    }
}
