package com.aicourse.service;

import com.aicourse.common.ApiCode;
import com.aicourse.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AiQuotaService} 单元测试。
 *
 * <p>验证「把没人会恶意刷 AI 的乐观假设变成保证」的四条硬约束：
 * <ol>
 *   <li>Redis 不可用时降级本地计数，配额依旧生效（绝不因 Redis 缺失而门户大开）；</li>
 *   <li>请求次数超限熔断为 429；</li>
 *   <li>token 预算耗尽熔断为 429；</li>
 *   <li>Redis 抛异常时降级本地，同样不失守；且 token 字符折算正确。</li>
 * </ol>
 */
class AiQuotaServiceTest {

    private static final Long USER = 42L;

    @SuppressWarnings("unchecked")
    private ObjectProvider<StringRedisTemplate> providerReturning(StringRedisTemplate template) {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(template);
        return provider;
    }

    // ==================== 1. Redis 不可用 → 本地降级，配额仍生效 ====================

    @Test
    void requestQuota_localFallback_blocksAfterLimit() {
        AiQuotaService service = new AiQuotaService(providerReturning(null));
        service.configureForTest(true, 3, 1_000_000L);

        // 前 3 次放行
        assertDoesNotThrow(() -> service.assertRequestQuota(USER));
        assertDoesNotThrow(() -> service.assertRequestQuota(USER));
        assertDoesNotThrow(() -> service.assertRequestQuota(USER));

        // 第 4 次超限 → 429
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertRequestQuota(USER));
        assertEquals(ApiCode.TOO_MANY_REQUESTS, ex.getCode());
    }

    // ==================== 2. token 预算耗尽 → 429 ====================

    @Test
    void tokenBudget_localFallback_blocksWhenExhausted() {
        AiQuotaService service = new AiQuotaService(providerReturning(null));
        service.configureForTest(true, 1_000_000, 100L);

        // 预算未耗尽时放行
        assertDoesNotThrow(() -> service.assertTokenBudget(USER));

        // 记账超过预算后，后续请求被拦
        service.recordTokenUsage(USER, 150L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertTokenBudget(USER));
        assertEquals(ApiCode.TOO_MANY_REQUESTS, ex.getCode());
    }

    // ==================== 3. 关闭开关 / 匿名用户不计费 ====================

    @Test
    void disabled_or_anonymous_neverBlocks() {
        AiQuotaService disabled = new AiQuotaService(providerReturning(null));
        disabled.configureForTest(false, 1, 1L);
        // 开关关闭：即便超限也放行
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> disabled.assertRequestQuota(USER));
        }

        AiQuotaService enabled = new AiQuotaService(providerReturning(null));
        enabled.configureForTest(true, 1, 1L);
        // 匿名用户（userId=null）不计费
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> enabled.assertRequestQuota(null));
        }
    }

    // ==================== 4. Redis 原子路径：按脚本返回值判定 ====================

    @Test
    @SuppressWarnings("unchecked")
    void requestQuota_redisPath_usesScriptResult() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AtomicLong counter = new AtomicLong(0);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenAnswer(inv -> counter.incrementAndGet());

        AiQuotaService service = new AiQuotaService(providerReturning(redis));
        service.configureForTest(true, 2, 1_000_000L);

        assertDoesNotThrow(() -> service.assertRequestQuota(USER)); // 1
        assertDoesNotThrow(() -> service.assertRequestQuota(USER)); // 2
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertRequestQuota(USER));            // 3 → 超限
        assertEquals(ApiCode.TOO_MANY_REQUESTS, ex.getCode());
    }

    // ==================== 5. Redis 抛异常 → 降级本地，不失守 ====================

    @Test
    @SuppressWarnings("unchecked")
    void requestQuota_redisFailure_fallsBackToLocal() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("redis down"));

        AiQuotaService service = new AiQuotaService(providerReturning(redis));
        service.configureForTest(true, 2, 1_000_000L);

        // Redis 故障不应导致「无限放行」，本地计数依旧在第 3 次熔断
        assertDoesNotThrow(() -> service.assertRequestQuota(USER));
        assertDoesNotThrow(() -> service.assertRequestQuota(USER));
        assertThrows(BusinessException.class, () -> service.assertRequestQuota(USER));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tokenBudget_redisReadFailure_fallsBackToLocal() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenThrow(new RuntimeException("redis down"));
        // 写路径也走本地
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("redis down"));

        AiQuotaService service = new AiQuotaService(providerReturning(redis));
        service.configureForTest(true, 1_000_000, 100L);

        service.recordTokenUsage(USER, 150L);
        assertThrows(BusinessException.class, () -> service.assertTokenBudget(USER));
    }

    // ==================== 6. token 折算 ====================

    @Test
    void estimateTokens_folding() {
        AiQuotaService service = new AiQuotaService(providerReturning(null));
        // 15 字符 / 1.5 = 10
        assertEquals(10L, service.estimateTokens("123456789012345"));
        // 多段拼接：9 + 6 = 15 字符 → 10
        assertEquals(10L, service.estimateTokens("123456789", "123456"));
        // 空/ null 归零
        assertEquals(0L, service.estimateTokens((String[]) null));
        assertEquals(0L, service.estimateTokensFromChars(0));
        // 3 字符 → ceil(2) = 2
        assertEquals(2L, service.estimateTokensFromChars(3));
    }
}
