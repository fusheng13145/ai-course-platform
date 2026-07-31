package com.aicourse.service.impl;

import com.aicourse.entity.UserSession;
import com.aicourse.mapper.UserSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户会话校验单元测试（纯 DB 实现，无 Redis 缓存层）。
 * <p>当前 {@link UserSessionServiceImpl} 为直算模式：isSessionActive 直接按
 * sessionToken + isActive + 未过期条件 count 数据库；evictSessionCache 查询会话后
 * 置为失效并 updateById。本测试 mock UserSessionMapper（注入为 baseMapper），
 * 验证命中放行、未命中拒绝、失效下线、未找到不动作等行为。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserSessionCacheTest {

    @Mock
    private UserSessionMapper mapper;

    @InjectMocks
    private UserSessionServiceImpl userSessionService;

    @BeforeEach
    void setUp() {
        // 显式注入 baseMapper，使 MyBatis-Plus ServiceImpl 的内部方法可用
        ReflectionTestUtils.setField(userSessionService, "baseMapper", mapper);
    }

    @Test
    void isSessionActive_activeToken_returnsTrue() {
        when(mapper.selectCount(any())).thenReturn(1L);

        boolean active = userSessionService.isSessionActive("jwt-token");

        assertThat(active).isTrue();
        verify(mapper).selectCount(any());
    }

    @Test
    void isSessionActive_inactiveToken_returnsFalse() {
        when(mapper.selectCount(any())).thenReturn(0L);

        boolean active = userSessionService.isSessionActive("bad-token");

        assertThat(active).isFalse();
        verify(mapper).selectCount(any());
    }

    @Test
    void isSessionActive_queriesDbDirectly() {
        // 纯 DB 实现不依赖任何缓存中间件：count > 0 即视为活跃
        when(mapper.selectCount(any())).thenReturn(1L);

        boolean active = userSessionService.isSessionActive("jwt-token");

        assertThat(active).isTrue();
    }

    @Test
    void evictSessionCache_marksSessionInactive() {
        UserSession session = new UserSession();
        session.setId(10L);
        session.setSessionToken("jwt-token");
        session.setIsActive(true);
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(session);

        userSessionService.evictSessionCache("jwt-token");

        assertThat(session.getIsActive()).isFalse();
        verify(mapper).updateById(session);
    }

    @Test
    void evictSessionCache_tokenNotFound_isNoOp() {
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(null);

        userSessionService.evictSessionCache("  ");
        userSessionService.evictSessionCache(null);

        verify(mapper, never()).updateById(any(UserSession.class));
    }
}
