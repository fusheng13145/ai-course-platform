package com.aicourse.service.impl;

import com.aicourse.entity.UserSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.UserSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 用户会话服务单元测试（自包含版）：最大化 INSTRUCTION 覆盖。
 * 通过反射注入 mock 的 baseMapper，并对 Redis 分支分别使用「无 Redis」与「有 Redis」两种构造。
 * 关键：纯 Mockito 单测无 Spring 上下文，需 TableInfoHelper 初始化 UserSession 元信息，
 * 否则生产代码中 new LambdaQueryWrapper&lt;UserSession&gt;() 会因 lambda 列解析失败而抛异常。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserSessionServiceImplTest {

    @Mock
    private UserSessionMapper baseMapper;
    @Mock
    private ObjectProvider<StringRedisTemplate> redisProvider;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private UserSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserSession.class);
        // 让构造期通过 redisProvider.getIfAvailable() 拿到真实 mock，使 Redis 分支默认可达
        lenient().when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        service = new UserSessionServiceImpl(redisProvider);
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
    }

    private UserSession sampleSession(Long id, Long userId, String ip, boolean active, LocalDateTime expiresAt) {
        UserSession s = new UserSession();
        s.setId(id);
        s.setUserId(userId);
        s.setSessionToken("tok-" + id);
        s.setIpAddress(ip);
        s.setIsActive(active);
        s.setExpiresAt(expiresAt);
        return s;
    }

    // ===================== getCurrentSession =====================
    @Test
    @DisplayName("getCurrentSession: 命中会话")
    void getCurrentSession_found() {
        UserSession s = sampleSession(1L, 10L, "1.1.1.1", true, LocalDateTime.now().plusDays(1));
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(s);
        assertEquals(s, service.getCurrentSession(10L));
    }

    @Test
    @DisplayName("getCurrentSession: 未命中返回 null")
    void getCurrentSession_null() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);
        assertNull(service.getCurrentSession(10L));
    }

    // ===================== getSessionHistory =====================
    @Test
    @DisplayName("getSessionHistory: 返回列表")
    void getSessionHistory() {
        List<UserSession> list = Arrays.asList(sampleSession(1L, 10L, "1.1.1.1", true, null));
        when(baseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(list);
        assertEquals(list, service.getSessionHistory(10L));
    }

    // ===================== forceLogout =====================
    @Test
    @DisplayName("forceLogout: 会话不存在抛异常")
    void forceLogout_notFound() {
        when(baseMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.forceLogout(1L));
    }

    @Test
    @DisplayName("forceLogout: 成功下线（无 Redis）")
    void forceLogout_successNoRedis() {
        // 构造期已捕获 redisTemplate（非 null），这里显式置空以模拟无 Redis 分支
        ReflectionTestUtils.setField(service, "redisTemplate", null);
        UserSession s = sampleSession(1L, 10L, "1.1.1.1", true, null);
        when(baseMapper.selectById(any())).thenReturn(s);
        when(baseMapper.updateById(any(UserSession.class))).thenReturn(1);
        service.forceLogout(1L);
        assertFalse(s.getIsActive());
        verify(baseMapper).updateById(s);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("forceLogout: 成功下线（有 Redis，失效缓存）")
    void forceLogout_successWithRedis() {
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        UserSession s = sampleSession(1L, 10L, "1.1.1.1", true, null);
        when(baseMapper.selectById(any())).thenReturn(s);
        when(baseMapper.updateById(any(UserSession.class))).thenReturn(1);
        service.forceLogout(1L);
        verify(redisTemplate).delete(anyString());
    }

    // ===================== cleanExpiredSessions =====================
    @Test
    @DisplayName("cleanExpiredSessions: 无过期记录返回 0")
    void cleanExpired_none() {
        when(baseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        assertEquals(0, service.cleanExpiredSessions());
        verify(baseMapper, never()).delete(any());
    }

    @Test
    @DisplayName("cleanExpiredSessions: 有记录且 remove 成功")
    void cleanExpired_removed() {
        when(baseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L, 0L);
        when(baseMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        assertEquals(0, service.cleanExpiredSessions());
    }

    @Test
    @DisplayName("cleanExpiredSessions: 有记录且 remove 返回 false")
    void cleanExpired_removeFalse() {
        when(baseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        when(baseMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
        assertEquals(0, service.cleanExpiredSessions());
    }

    // ===================== getOnlineUserCount =====================
    @Test
    @DisplayName("getOnlineUserCount: 去重统计不同用户")
    void getOnlineUserCount() {
        List<UserSession> list = Arrays.asList(
                sampleSession(1L, 10L, "1.1.1.1", true, LocalDateTime.now().plusDays(1)),
                sampleSession(2L, 10L, "1.1.1.1", true, LocalDateTime.now().plusDays(1)),
                sampleSession(3L, 20L, "2.2.2.2", true, LocalDateTime.now().plusDays(1)));
        when(baseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(list);
        assertEquals(2L, service.getOnlineUserCount());
    }

    @Test
    @DisplayName("getOnlineUserCount: 空列表")
    void getOnlineUserCount_empty() {
        when(baseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        assertEquals(0L, service.getOnlineUserCount());
    }

    // ===================== detectAbnormalSessions =====================
    @Test
    @DisplayName("detectAbnormalSessions: 仅一个活跃会话 -> 空")
    void detect_single() {
        when(baseMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(sampleSession(1L, 10L, "1.1.1.1", true, null)));
        assertTrue(service.detectAbnormalSessions(10L).isEmpty());
    }

    @Test
    @DisplayName("detectAbnormalSessions: 多会话同 IP -> 空")
    void detect_sameIp() {
        when(baseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                sampleSession(1L, 10L, "1.1.1.1", true, null),
                sampleSession(2L, 10L, "1.1.1.1", true, null)));
        assertTrue(service.detectAbnormalSessions(10L).isEmpty());
    }

    @Test
    @DisplayName("detectAbnormalSessions: 多 IP -> 返回异常会话")
    void detect_multiIp() {
        UserSession a = sampleSession(1L, 10L, "1.1.1.1", true, null);
        UserSession b = sampleSession(2L, 10L, "2.2.2.2", true, null);
        UserSession c = sampleSession(3L, 10L, "3.3.3.3", true, null);
        when(baseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(a, b, c));
        List<UserSession> abnormal = service.detectAbnormalSessions(10L);
        assertEquals(2, abnormal.size());
        assertTrue(abnormal.contains(b));
        assertTrue(abnormal.contains(c));
    }

    @Test
    @DisplayName("detectAbnormalSessions: 含 null IP 归入 unknown 组")
    void detect_nullIp() {
        UserSession a = sampleSession(1L, 10L, null, true, null);
        UserSession b = sampleSession(2L, 10L, "2.2.2.2", true, null);
        when(baseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(a, b));
        List<UserSession> abnormal = service.detectAbnormalSessions(10L);
        assertEquals(1, abnormal.size());
        assertEquals(b, abnormal.get(0));
    }

    // ===================== isSessionActive =====================
    @Test
    @DisplayName("isSessionActive: token 为空/blank -> false")
    void isSessionActive_empty() {
        assertFalse(service.isSessionActive(null));
        assertFalse(service.isSessionActive(""));
    }

    @Test
    @DisplayName("isSessionActive: 无 Redis，查库命中 -> true")
    void isSessionActive_noRedis_hit() {
        // 构造期已捕获 redisTemplate（非 null），显式置空以模拟无 Redis 分支
        ReflectionTestUtils.setField(service, "redisTemplate", null);
        UserSession s = sampleSession(1L, 10L, "1.1.1.1", true, LocalDateTime.now().plusDays(1));
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(s);
        assertTrue(service.isSessionActive("tok"));
    }

    @Test
    @DisplayName("isSessionActive: 无 Redis，查库未命中 -> false")
    void isSessionActive_noRedis_miss() {
        // 构造期已捕获 redisTemplate（非 null），显式置空以模拟无 Redis 分支
        ReflectionTestUtils.setField(service, "redisTemplate", null);
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);
        assertFalse(service.isSessionActive("tok"));
    }

    @Test
    @DisplayName("isSessionActive: 有 Redis 缓存命中(1) -> true")
    void isSessionActive_redisHit() {
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("1");
        assertTrue(service.isSessionActive("tok"));
        verify(baseMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("isSessionActive: 有 Redis 缓存标记失效(0) -> false")
    void isSessionActive_redisDead() {
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("0");
        assertFalse(service.isSessionActive("tok"));
        verify(baseMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("isSessionActive: 有 Redis 缓存未命中，查库命中并回填（带 TTL）")
    void isSessionActive_redisMissThenDb() {
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        UserSession s = sampleSession(1L, 10L, "1.1.1.1", true, LocalDateTime.now().plusSeconds(120));
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(s);
        assertTrue(service.isSessionActive("tok"));
        verify(valueOps).set(anyString(), eq("1"), any(Duration.class));
    }

    @Test
    @DisplayName("isSessionActive: 有 Redis 缓存未命中，查库未命中 -> false")
    void isSessionActive_redisMissDbNull() {
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);
        assertFalse(service.isSessionActive("tok"));
    }

    // ===================== evictSessionCache =====================
    @Test
    @DisplayName("evictSessionCache: 无 Redis，会话存在 -> 置失效")
    void evict_noRedis_found() {
        // 构造期已捕获 redisTemplate（非 null），显式置空以模拟无 Redis 分支
        ReflectionTestUtils.setField(service, "redisTemplate", null);
        UserSession s = sampleSession(1L, 10L, "1.1.1.1", true, null);
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(s);
        when(baseMapper.updateById(any(UserSession.class))).thenReturn(1);
        service.evictSessionCache("tok");
        assertFalse(s.getIsActive());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("evictSessionCache: 有 Redis，会话存在 -> delete + 置失效")
    void evict_withRedis_found() {
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        UserSession s = sampleSession(1L, 10L, "1.1.1.1", true, null);
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(s);
        when(baseMapper.updateById(any(UserSession.class))).thenReturn(1);
        service.evictSessionCache("tok");
        verify(redisTemplate).delete(anyString());
        verify(baseMapper).updateById(s);
    }

    @Test
    @DisplayName("evictSessionCache: 有 Redis，会话不存在 -> 仅 delete")
    void evict_withRedis_notFound() {
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);
        service.evictSessionCache("tok");
        verify(redisTemplate).delete(anyString());
        verify(baseMapper, never()).updateById(any(UserSession.class));
    }
}
