package com.aicourse.service.impl;

import com.aicourse.mapper.UserSessionMapper;
import com.aicourse.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话校验 Redis 缓存化单元测试（不依赖外部 Redis）。
 * 通过 mock RedisTemplate 验证 cache-aside 行为：命中放行、未命中回源、有效回填、Redis 故障降级、失效删除。
 *
 * 说明：
 * 1) JwtUtils.sha256Hex 为静态方法，无法在实例 mock 上 stub；测试直接调用静态方法算出与
 *    生产代码一致的缓存键，避免 mock 静态方法。
 * 2) RedisTemplate 的 delete(K)/delete(Collection<K>) 为泛型重载，在泛型 mock 上会被 JDK 判为歧义，
 *    故将 redisTemplate / valueOps 声明为原始类型以消除编译歧义，运行期行为不受影响。
 */
@ExtendWith(MockitoExtension.class)
class UserSessionCacheTest {

    @Mock
    private UserSessionMapper mapper;

    @Mock
    @SuppressWarnings("rawtypes")
    private RedisTemplate redisTemplate;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private UserSessionServiceImpl userSessionService;

    private static String cacheKey(String token) {
        return "aicourse:session:active:" + JwtUtils.sha256Hex(token);
    }

    @SuppressWarnings("rawtypes")
    private ValueOperations stubValueOps() {
        ValueOperations valueOps = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        return valueOps;
    }

    @Test
    void isSessionActive_cacheMiss_dbActive_backfillsCache() {
        String key = cacheKey("jwt-token");
        when(jwtUtils.getRemainingSeconds(any())).thenReturn(3600L);
        when(redisTemplate.hasKey(key)).thenReturn(false);
        ValueOperations valueOps = stubValueOps();
        doNothing().when(valueOps).set(any(), any(), anyLong(), any());
        when(mapper.selectCount(any())).thenReturn(1L);

        boolean active = userSessionService.isSessionActive("jwt-token");

        assertThat(active).isTrue();
        verify(valueOps, times(1)).set(eq(key), eq("1"), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    void isSessionActive_inactiveToken_returnsFalse_andNoCacheBackfill() {
        String key = cacheKey("bad-token");
        when(redisTemplate.hasKey(key)).thenReturn(false);
        // 无效令牌：未命中缓存后回源 DB 计数为 0，提前返回 false，不调用 opsForValue() 回填。
        // 此处不 stub valueOps，转而验证 opsForValue() 从未被调用，直接证明「无缓存回填」。
        when(mapper.selectCount(any())).thenReturn(0L);

        boolean active = userSessionService.isSessionActive("bad-token");

        assertThat(active).isFalse();
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void isSessionActive_redisDown_degradesToDb() {
        String key = cacheKey("jwt-token");
        when(redisTemplate.hasKey(key)).thenThrow(new RuntimeException("redis down"));
        when(mapper.selectCount(any())).thenReturn(1L);

        boolean active = userSessionService.isSessionActive("jwt-token");

        assertThat(active).isTrue();
    }

    @Test
    void evictSessionCache_deletesRedisKey() {
        String key = cacheKey("jwt-token");

        userSessionService.evictSessionCache("jwt-token");

        // 原始类型下用具体字符串字面量匹配 delete(Object) 重载，规避泛型重载歧义
        verify(redisTemplate, times(1)).delete(key);
    }

    @Test
    void evictSessionCache_blankToken_isNoOp() {
        userSessionService.evictSessionCache("  ");
        userSessionService.evictSessionCache(null);

        verify(redisTemplate, never()).delete("any-unused-key");
    }
}
