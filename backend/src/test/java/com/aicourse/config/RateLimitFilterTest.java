package com.aicourse.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/**
 * {@link RateLimitFilter} 单元测试：覆盖 Redis 集群限流、Redis 异常降级本地限流、
 * 本地固定窗口阈值、不同接口规则匹配等核心行为。
 */
class RateLimitFilterTest {

    private StringRedisTemplate redisTemplate;
    private RateLimitFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        filter = new RateLimitFilter(provider);
        // 单元测试无 Spring 上下文，@Value 字段需显式注入默认值
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "redisEnabled", true);
        ReflectionTestUtils.setField(filter, "loginLimit", 10);
        ReflectionTestUtils.setField(filter, "loginWindowSeconds", 60);
        ReflectionTestUtils.setField(filter, "aiLimit", 20);
        ReflectionTestUtils.setField(filter, "aiWindowSeconds", 60);
        ReflectionTestUtils.setField(filter, "verifyLimit", 5);
        ReflectionTestUtils.setField(filter, "verifyWindowSeconds", 60);
        // 安全修复 B1：注册端点 IP 限流（默认 20 次 / 3600 秒）
        ReflectionTestUtils.setField(filter, "registerLimit", 20);
        ReflectionTestUtils.setField(filter, "registerWindowSeconds", 3600);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @Test
    void redisWithinLimit_shouldPass() throws Exception {
        request.setRequestURI("/api/auth/login");
        request.setRemoteAddr("192.168.1.1");
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(1L);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void redisExceededLimit_shouldBlockWith429() throws Exception {
        request.setRequestURI("/api/auth/login");
        request.setRemoteAddr("192.168.1.2");
        // 第 11 次请求，超过默认阈值 10
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(11L);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(0)).doFilter(request, response);
        assertEquals(429, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
    }

    @Test
    void aiEndpoint_usesAiLimit() throws Exception {
        request.setRequestURI("/api/ai/qa/ask");
        request.setRemoteAddr("192.168.1.3");
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(21L);

        filter.doFilterInternal(request, response, chain);

        assertEquals(429, response.getStatus());
    }

    @Test
    void verifyCodeEndpoint_usesVerifyLimit() throws Exception {
        request.setRequestURI("/api/common/verify-code");
        request.setRemoteAddr("192.168.1.4");
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(6L);

        filter.doFilterInternal(request, response, chain);

        assertEquals(429, response.getStatus());
    }

    @Test
    void registerEndpoint_usesRegisterLimit() throws Exception {
        // 安全修复 B1 回归：注册端点此前未命中任何限流规则（resolveRule 返回 null → 不限流），
        // 与「验证码可跳过」叠加后攻击者可零成本批量自建账号。本用例锁定注册端点已纳入限流。
        request.setRequestURI("/api/auth/register");
        request.setRemoteAddr("192.168.1.40");
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(21L);

        filter.doFilterInternal(request, response, chain);

        assertEquals(429, response.getStatus());
    }

    @Test
    void registerEndpoint_withinLimit_shouldPass() throws Exception {
        request.setRequestURI("/api/auth/register");
        request.setRemoteAddr("192.168.1.41");
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(1L);

        filter.doFilterInternal(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void redisException_shouldFallbackToLocalLimitAndPass() throws Exception {
        request.setRequestURI("/api/auth/login");
        request.setRemoteAddr("192.168.1.5");
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RuntimeException("redis down"));

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void optionsRequest_shouldSkipLimit() throws Exception {
        request.setMethod("OPTIONS");
        request.setRequestURI("/api/auth/login");
        request.setRemoteAddr("192.168.1.6");

        filter.doFilterInternal(request, response, chain);

        verify(redisTemplate, times(0)).execute(any(RedisScript.class), anyList(), any());
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void nonLimitedPath_shouldPassWithoutRedisCall() throws Exception {
        request.setRequestURI("/api/courses/list");
        request.setRemoteAddr("192.168.1.7");

        filter.doFilterInternal(request, response, chain);

        verify(redisTemplate, times(0)).execute(any(RedisScript.class), anyList(), any());
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void localLimiter_windowResetAllowsNewRequests() throws Exception {
        // 构造 Redis 不可用场景，验证本地窗口重置逻辑
        request.setRequestURI("/api/auth/login");
        request.setRemoteAddr("192.168.1.8");
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RuntimeException("redis down"));

        // 连续 5 次请求均未超限（默认阈值 10）
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse r = new MockHttpServletResponse();
            filter.doFilterInternal(request, r, chain);
            assertEquals(200, r.getStatus());
        }
    }

    @Test
    void forgedXffHeader_notTrustedByDefault_keyUsesRemoteAddr() throws Exception {
        request.setRequestURI("/api/auth/login");
        request.setRemoteAddr("192.168.1.9");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(1L);

        filter.doFilterInternal(request, response, chain);

        // 限流 key 必须以 TCP 对端地址计算，伪造 XFF 不应影响计数
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), any());
        String key = keysCaptor.getValue().get(0);
        assertTrue(key.startsWith("ratelimit:login:192.168.1.9:"));
        assertFalse(key.contains("1.2.3.4"));
    }

    @Test
    void trustProxyHeader_enabled_keyUsesXffFirstSegment() throws Exception {
        ReflectionTestUtils.setField(filter, "trustProxyHeader", true);
        request.setRequestURI("/api/auth/login");
        request.setRemoteAddr("192.168.1.9");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.1");
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(1L);

        filter.doFilterInternal(request, response, chain);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), any());
        String key = keysCaptor.getValue().get(0);
        assertTrue(key.startsWith("ratelimit:login:1.2.3.4:"));
    }

    /**
     * Lua 脚本引用的 ARGV / KEYS 下标必须落在调用方真实传入的个数之内。
     * <p>越界的 {@code ARGV[n]} 在 Lua 里取到 {@code false}，{@code EXPIRE key false} 会让
     * 整段脚本执行失败；而 INCR 先已写入，于是窗口键既没有限流语义也没有 TTL，
     * 在 Redis 中永久堆积，同时集群级限流静默退化为单机内存限流。
     * 既有用例把 {@code StringRedisTemplate} 整体打桩，脚本正文从未被执行，
     * 所以这一类下标错位只能靠断言脚本与实参的契约来守住。</p>
     */
    @Test
    @SuppressWarnings("unchecked")
    void luaScriptParameterIndexes_stayWithinArgsAndKeysPassed() throws Exception {
        request.setRequestURI("/api/ai/qa/ask");
        request.setRemoteAddr("192.168.1.20");
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(1L);

        filter.doFilterInternal(request, response, chain);

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object> argsCaptor = ArgumentCaptor.forClass(Object.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(), argsCaptor.capture());

        String lua = scriptCaptor.getValue().getScriptAsString();
        assertTrue(highestLuaTableIndex(lua, "ARGV") <= argsCaptor.getAllValues().size(),
                "ARGV 下标越界：脚本引用到 ARGV[" + highestLuaTableIndex(lua, "ARGV")
                        + "]，但调用方只传了 " + argsCaptor.getAllValues().size() + " 个实参");
        assertTrue(highestLuaTableIndex(lua, "KEYS") <= keysCaptor.getValue().size(),
                "KEYS 下标越界：脚本引用到 KEYS[" + highestLuaTableIndex(lua, "KEYS")
                        + "]，但调用方只传了 " + keysCaptor.getValue().size() + " 个键");
        // TTL 实参 = 窗口长度 + 1 秒边界缓冲（ai 窗口默认 60）
        assertEquals("61", String.valueOf(argsCaptor.getAllValues().get(0)));
    }

    private static int highestLuaTableIndex(String lua, String table) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(table + "\\[(\\d+)]").matcher(lua);
        int max = 0;
        while (m.find()) {
            max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        return max;
    }
}
