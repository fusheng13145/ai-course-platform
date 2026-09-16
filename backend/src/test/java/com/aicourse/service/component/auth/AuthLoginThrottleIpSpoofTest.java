package com.aicourse.service.component.auth;

import com.aicourse.common.ClientInfo;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.LoginLogService;
import com.aicourse.service.UserService;
import com.aicourse.service.UserSessionService;
import com.aicourse.service.VerificationCodeService;
import com.aicourse.util.ClientInfoResolver;
import com.aicourse.util.IpUtil;
import com.aicourse.util.JwtUtils;
import com.aicourse.util.TrustedClientIpResolver;
import com.aicourse.util.UserAgentUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 登录失败节流「伪造请求头绕过」的<b>回归测试</b>（P0-1 后继，2026-09-16）。
 *
 * <p><b>被测真实链路</b>：{@code AuthController.login} → {@code ClientInfoResolver.resolve(request)}
 * → {@code TrustedClientIpResolver.resolve(request)} → {@code AuthSessionComponent.loginFailKey(...)}。
 * 本测试<b>不</b>直接构造 {@link ClientInfo} 塞 IP，而是喂 {@link MockHttpServletRequest} 走完整解析链，
 * 以证明取值逻辑本身不可被伪造头污染，而非测试桩造出的假象。</p>
 *
 * <p><b>修复前的缺陷（已实测复现，证据存档）</b>：IP 取自 {@link IpUtil#getClientIp}，它优先读
 * {@code X-Forwarded-For} 并取 {@code split(",")[0]}（最左端、完全客户端可控）；而 nginx 用
 * {@code $proxy_add_x_forwarded_for}（追加语义）把客户端自带的 XFF 拼在最左端。实测：同账户连发 12 次、
 * 每次换一个 XFF → {@code observed429=0}、{@code distinctKeys=12}、每键计数=1，限流形同虚设，
 * 可无限爆破。对照（固定 XFF）则第 5 次正确 429。</p>
 *
 * <p><b>修复后的口径</b>：安全决策用的 IP 改由 {@link TrustedClientIpResolver} 解析——受信反代场景
 * （{@code trust-proxy-header=true}，生产默认）取反代以 {@code $remote_addr} <b>覆写</b>的
 * {@code X-Real-IP}；非受信场景取 TCP 对端地址。两者<b>均不受客户端伪造的 XFF 影响</b>，
 * 故伪造头换头不再能让计数键漂移。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthLoginThrottleIpSpoofTest {

    /** 模拟「真实 TCP 对端」地址（对照用）。 */
    private static final String REAL_PEER = "203.0.113.7";

    /** 模拟「受信 nginx 以 $remote_addr 覆写」得到的真实客户端地址。 */
    private static final String TRUSTED_CLIENT_IP = "198.51.100.24";

    @Mock private UserService userService;
    @Mock private UserSessionService userSessionService;
    @Mock private VerificationCodeService verificationCodeService;
    @Mock private JwtUtils jwtUtils;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private LoginLogService loginLogService;
    @Mock private AuthPasswordComponent passwordComponent;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    /** 真实可信 IP 解析器（生产代码实例，非 mock）。 */
    private TrustedClientIpResolver trustedIpResolver;

    /** 真实解析链（生产代码实例，非 mock）。 */
    private ClientInfoResolver resolver;

    private AuthSessionComponent component;

    /** 模拟「每键独立计数」的内存 Redis：忠实还原 INCR 的自增语义（含 Lua 脚本路径）。 */
    private final Map<String, Long> redis = new ConcurrentHashMap<>();

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

    private void prepare(boolean trustProxyHeader) {
        redis.clear();
        trustedIpResolver = new TrustedClientIpResolver();
        ReflectionTestUtils.setField(trustedIpResolver, "trustProxyHeader", trustProxyHeader);
        resolver = new ClientInfoResolver(trustedIpResolver, new UserAgentUtil());
        component = new AuthSessionComponent(userService, userSessionService, verificationCodeService,
                jwtUtils, passwordEncoder, loginLogService, passwordComponent, redisTemplate);
    }

    @BeforeEach
    void setUp() {
        prepare(true); // 默认按生产口径：置于受信反代之后
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenAnswer(inv -> {
            Long v = redis.get(inv.getArgument(0, String.class));
            return v == null ? null : String.valueOf(v);
        });
        // P0-1 原子性修复后，计数走 Lua 脚本（INCR + EXPIRE 原子化）而非 valueOps.increment
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenAnswer(inv -> {
            List<?> keys = inv.getArgument(1, List.class);
            return redis.merge(String.valueOf(keys.get(0)), 1L, Long::sum);
        });
        when(userService.getOne(any())).thenReturn(activeUser());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
    }

    /** 构造请求；remoteAddr 固定为「真实 TCP 对端」，并按需伪造 XFF / X-Real-IP。 */
    private static MockHttpServletRequest req(String xff) {
        return req(xff, null);
    }

    private static MockHttpServletRequest req(String xff, String realIp) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(REAL_PEER);
        if (xff != null) {
            r.addHeader("X-Forwarded-For", xff);
        }
        if (realIp != null) {
            r.addHeader("X-Real-IP", realIp);
        }
        return r;
    }

    /** 走真实解析链完成一次登录，返回响应码（429 表示被限流，400 表示普通失败）。 */
    private int attemptLogin(String account, HttpServletRequest request) {
        ClientInfo info = resolver.resolve(request); // ← 真实 TrustedClientIpResolver/ClientInfoResolver 链
        try {
            component.login(account, "wrong-password", false, info);
            return 200;
        } catch (BusinessException e) {
            return e.getCode();
        }
    }

    // ===================== 用例 1：核心回归 —— 换头绕过已被封堵 =====================

    @Test
    @DisplayName("回归：受信反代下每次伪造不同 X-Forwarded-For → 仍按 X-Real-IP 计数并触发 429（不可绕过）")
    void spoofPerRequest_shouldStillBeThrottled() {
        int attempts = 12; // 远超阈值 5
        int observed429 = 0;
        for (int i = 0; i < attempts; i++) {
            // 每次换一个 XFF（修复前正是靠这个让键漂移）；X-Real-IP 由受信反代覆写、保持恒定。
            int code = attemptLogin("victim@aicourse.com", req("10.0.0." + (100 + i), TRUSTED_CLIENT_IP));
            if (code == 429) {
                observed429++;
            }
        }
        System.out.println("[REGRESSION-1] attempts=" + attempts + " observed429=" + observed429
                + " distinctKeys=" + redis.size() + " keys=" + redis.keySet());

        assertEquals(1, redis.size(), "伪造 XFF 不得改变计数键：应始终只有 1 个键，实际=" + redis.keySet());
        assertTrue(redis.containsKey("auth:login:fail:" + TRUSTED_CLIENT_IP + ":victim@aicourse.com"),
                "键应以受信 X-Real-IP 计算，实际=" + redis.keySet());
        // 第 5 次达阈值后，后续请求被前置校验拦截（不再自增），故计数停在阈值 5
        assertEquals(5L, redis.values().iterator().next(), "计数应达阈值后停止自增");
        assertEquals(attempts - 4, observed429, "除前 4 次外其余请求均应 429 —— 限流不可被换头绕过");
    }

    // ===================== 用例 2：对照 —— 固定 IP 时序正确 =====================

    @Test
    @DisplayName("对照：固定来源连续失败 → 前 4 次 400、第 5 次起 429")
    void fixedSource_fifthFailureTriggers429() {
        int[] codes = new int[5];
        for (int i = 0; i < 5; i++) {
            codes[i] = attemptLogin("victim@aicourse.com", req("10.0.0.1", TRUSTED_CLIENT_IP));
        }
        System.out.println("[REGRESSION-2] codes(1..5)=" + Arrays.toString(codes) + " keys=" + redis.keySet());

        assertEquals(400, codes[3], "第 4 次仍未达阈值，不应限流");
        assertEquals(429, codes[4], "第 5 次应触发 429");
        assertEquals(1, redis.size(), "固定来源只产生 1 个键");
    }

    // ===================== 用例 3：非受信场景 XFF 与 X-Real-IP 均不得生效 =====================

    @Test
    @DisplayName("非受信反代（trust-proxy-header=false）：伪造 XFF 与 X-Real-IP 均被忽略，键落 TCP 对端")
    void untrusted_forgedHeadersIgnored_keyUsesRemoteAddr() {
        prepare(false);

        attemptLogin("victim@aicourse.com", req("1.2.3.4", "9.9.9.9"));

        System.out.println("[REGRESSION-3] keys=" + redis.keySet());
        assertEquals(1, redis.size());
        assertTrue(redis.containsKey("auth:login:fail:" + REAL_PEER + ":victim@aicourse.com"),
                "非受信场景应落 remoteAddr 且忽略全部伪造头，实际=" + redis.keySet());
    }

    // ===================== 用例 4：XFF 对键取值完全无影响 =====================

    @Test
    @DisplayName("受信反代下：XFF 取值变化不改变键（同 X-Real-IP 两请求命中同一键）")
    void trusted_xffValueHasNoEffectOnKey() {
        attemptLogin("victim@aicourse.com", req("10.0.0.1", TRUSTED_CLIENT_IP));
        attemptLogin("victim@aicourse.com", req("172.16.0.9", TRUSTED_CLIENT_IP));

        assertEquals(1, redis.size(), "两次不同 XFF 应命中同一键，实际=" + redis.keySet());
        assertEquals(2L, redis.values().iterator().next(), "同一键应累加到 2");
    }

    // ===================== 平台行为存档：ForwardedHeaderFilter 下 getRemoteAddr() =====================

    @Test
    @DisplayName("存档实测：forward-headers-strategy=framework 下 getRemoteAddr() 为 XFF 最左端（故不可作为安全依据）")
    void forwardedHeaderFilter_remoteAddrIsLeftmostXff() throws Exception {
        MockHttpServletRequest raw = new MockHttpServletRequest();
        raw.setRemoteAddr(REAL_PEER); // 真实 TCP 对端
        raw.addHeader("X-Forwarded-For", "1.2.3.4, 9.9.9.9");

        ForwardedHeaderFilter filter = new ForwardedHeaderFilter();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(raw, new MockHttpServletResponse(), chain);
        HttpServletRequest wrapped = (HttpServletRequest) chain.getRequest();

        String remoteAddr = wrapped.getRemoteAddr();
        String xffAfter = wrapped.getHeader("X-Forwarded-For");
        System.out.println("[ARCHIVE-1] wrapped.getRemoteAddr()=" + remoteAddr + ", wrapped.getHeader(XFF)=" + xffAfter);

        // 这正是「不能依赖 getRemoteAddr() 做安全决策」的实证依据：
        // 它被覆写为客户端可伪造的最左端，且 XFF 头本身已被移除。
        assertEquals("1.2.3.4", remoteAddr, "framework 策略下 getRemoteAddr() 应为 XFF 最左端（可伪造）");
        assertEquals(null, xffAfter, "XFF 头应已被 ForwardedHeaderFilter 移除");
    }

    @Test
    @DisplayName("存档实测：整条旧解析链（IpUtil）在 ForwardedHeaderFilter 生效下取到伪造 IP")
    void forwardedHeaderFilter_legacyIpUtilChainResolvesForgedIp() throws Exception {
        MockHttpServletRequest raw = new MockHttpServletRequest();
        raw.setRemoteAddr(REAL_PEER);
        raw.addHeader("X-Forwarded-For", "1.2.3.4");

        ForwardedHeaderFilter filter = new ForwardedHeaderFilter();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(raw, new MockHttpServletResponse(), chain);
        HttpServletRequest wrapped = (HttpServletRequest) chain.getRequest();

        String resolvedIp = new IpUtil().getClientIp(wrapped);
        System.out.println("[ARCHIVE-2] 旧链 IpUtil 解析结果=" + resolvedIp);

        assertEquals("1.2.3.4", resolvedIp,
                "记录旧链可被伪造头劫持的事实（故 ClientInfoResolver 已改用 TrustedClientIpResolver）");
    }
}
