package com.aicourse.service.impl;

import com.aicourse.entity.UserSession;
import com.aicourse.mapper.UserSessionMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户会话校验单元测试（Redis 缓存 + DB 兜底实现，修复 P1-11）。
 * <p>构造器注入 {@code ObjectProvider<StringRedisTemplate>}，测试传 null 时自动降级为纯 DB
 * 查询（isSessionActive 缓存未命中时走 {@code baseMapper.selectList} 取首条）；
 * evictSessionCache 查询会话后置为失效并 updateById。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserSessionCacheTest {

    @Mock
    private UserSessionMapper mapper;

    private UserSessionServiceImpl userSessionService;

    @BeforeEach
    void setUp() {
        // 纯 Mockito 单测无 Spring 上下文：先初始化实体元信息，否则 lambdaUpdate 的列解析会抛
        // "can not find lambda cache for this entity"。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserSession.class);
        // 显式构造（ObjectProvider 传 null → 无 Redis，降级 DB），并注入 baseMapper
        userSessionService = new UserSessionServiceImpl(null);
        ReflectionTestUtils.setField(userSessionService, "baseMapper", mapper);
    }

    private UserSession activeSession(String token) {
        UserSession s = new UserSession();
        s.setId(10L);
        s.setSessionToken(token);
        s.setIsActive(true);
        s.setExpiresAt(LocalDateTime.now().plusHours(1));
        return s;
    }

    @Test
    void isSessionActive_activeToken_returnsTrue() {
        // ServiceImpl.getOne(wrapper, false) 内部委托 baseMapper.selectOne(wrapper, throwEx)
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(activeSession("jwt-token"));

        boolean active = userSessionService.isSessionActive("jwt-token");

        assertThat(active).isTrue();
        verify(mapper).selectOne(any(), anyBoolean());
    }

    @Test
    void isSessionActive_inactiveToken_returnsFalse() {
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(null);

        boolean active = userSessionService.isSessionActive("bad-token");

        assertThat(active).isFalse();
        verify(mapper).selectOne(any(), anyBoolean());
    }

    @Test
    void isSessionActive_nullOrBlank_returnsFalse() {
        assertThat(userSessionService.isSessionActive(null)).isFalse();
        assertThat(userSessionService.isSessionActive("")).isFalse();
    }

    @Test
    void evictSessionCache_marksSessionInactive() {
        // DR-44：吊销改为按令牌批量 UPDATE（不再有 selectOne + updateById 的单行假设）
        userSessionService.evictSessionCache("jwt-token");

        verify(mapper).update(any(), any());
        verify(mapper, never()).selectOne(any(), anyBoolean());
    }

    @Test
    void evictSessionCache_tokenNotFound_isNoOp() {
        userSessionService.evictSessionCache("  ");
        userSessionService.evictSessionCache(null);

        verify(mapper, never()).update(any(), any());
    }
}
