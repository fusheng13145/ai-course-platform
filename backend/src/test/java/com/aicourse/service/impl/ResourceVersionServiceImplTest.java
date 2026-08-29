package com.aicourse.service.impl;

import com.aicourse.entity.ResourceVersion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ResourceVersionMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 纯 Mockito 单元测试：覆盖 ResourceVersionServiceImpl 版本创建/历史/回滚/当前版本逻辑及错误分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResourceVersionServiceImplTest {

    @Mock
    private ResourceVersionMapper resourceVersionMapper;

    private ResourceVersionServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ResourceVersion.class);
        service = new ResourceVersionServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", resourceVersionMapper);
    }

    @Test
    @DisplayName("createNewVersion 无历史版本，从版本 1 开始")
    void createNewVersion_noHistory() {
        when(resourceVersionMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(resourceVersionMapper.update(any(), any())).thenReturn(1);
        when(resourceVersionMapper.insert(any(ResourceVersion.class))).thenReturn(1);

        ResourceVersion r = service.createNewVersion(1L, "a.pdf", "pdf", "url", "desc", 7L, 100L);
        assertEquals(1, r.getVersionNumber());
        assertTrue(r.getIsActive());
        verify(resourceVersionMapper).insert(any(ResourceVersion.class));
    }

    @Test
    @DisplayName("createNewVersion 历史版本存在则递增")
    void createNewVersion_withHistory() {
        ResourceVersion latest = new ResourceVersion();
        latest.setVersionNumber(3);
        when(resourceVersionMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(latest);
        when(resourceVersionMapper.update(any(), any())).thenReturn(1);
        when(resourceVersionMapper.insert(any(ResourceVersion.class))).thenReturn(1);

        ResourceVersion r = service.createNewVersion(1L, "a.pdf", "pdf", "url", "desc", 7L, 100L);
        assertEquals(4, r.getVersionNumber());
    }

    @Test
    @DisplayName("createNewVersion 历史版本序号为 null 则从 1 开始")
    void createNewVersion_historyOrderNull() {
        ResourceVersion latest = new ResourceVersion();
        latest.setVersionNumber(null);
        when(resourceVersionMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(latest);
        when(resourceVersionMapper.update(any(), any())).thenReturn(1);
        when(resourceVersionMapper.insert(any(ResourceVersion.class))).thenReturn(1);

        ResourceVersion r = service.createNewVersion(1L, "a.pdf", "pdf", "url", "desc", 7L, 100L);
        assertEquals(1, r.getVersionNumber());
    }

    @Test
    @DisplayName("getVersionHistory 返回列表")
    void getVersionHistory_ok() {
        ResourceVersion v = new ResourceVersion();
        when(resourceVersionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(v));
        List<ResourceVersion> r = service.getVersionHistory(1L);
        assertEquals(1, r.size());
    }

    @Test
    @DisplayName("getVersionHistory 空列表")
    void getVersionHistory_empty() {
        when(resourceVersionMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        assertTrue(service.getVersionHistory(1L).isEmpty());
    }

    @Test
    @DisplayName("rollbackToVersion 目标版本不存在抛异常")
    void rollbackToVersion_notFound() {
        when(resourceVersionMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.rollbackToVersion(1L, 2));
    }

    @Test
    @DisplayName("rollbackToVersion 成功回滚")
    void rollbackToVersion_ok() {
        ResourceVersion target = new ResourceVersion();
        target.setResourceId(1L);
        when(resourceVersionMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(target);
        when(resourceVersionMapper.update(any(), any())).thenReturn(1);
        when(resourceVersionMapper.updateById(any(ResourceVersion.class))).thenReturn(1);

        service.rollbackToVersion(1L, 2);
        assertTrue(target.getIsActive());
        verify(resourceVersionMapper).updateById(any(ResourceVersion.class));
    }

    @Test
    @DisplayName("getActiveVersion 返回版本")
    void getActiveVersion_ok() {
        ResourceVersion v = new ResourceVersion();
        when(resourceVersionMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(v);
        assertEquals(v, service.getActiveVersion(1L));
    }

    @Test
    @DisplayName("getActiveVersion 返回 null")
    void getActiveVersion_null() {
        when(resourceVersionMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        assertNull(service.getActiveVersion(1L));
    }
}
