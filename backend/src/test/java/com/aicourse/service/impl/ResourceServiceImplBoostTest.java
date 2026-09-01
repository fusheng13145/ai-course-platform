package com.aicourse.service.impl;

import com.aicourse.entity.Resource;
import com.aicourse.entity.ResourceVersion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ResourceMapper;
import com.aicourse.service.ResourceVersionService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 纯 Mockito 单元测试：补充覆盖 ResourceServiceImpl 的权限校验、资源树、搜索、统计、可见性与章节查询分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResourceServiceImplBoostTest {

    @Mock
    private ResourceMapper resourceMapper;
    @Mock
    private ResourceVersionService resourceVersionService;

    private ResourceServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Resource.class);
        service = new ResourceServiceImpl(resourceVersionService);
        ReflectionTestUtils.setField(service, "baseMapper", resourceMapper);
    }

    @Test
    @DisplayName("checkAccessPermission 资源不存在抛异常")
    void check_null() {
        when(resourceMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.checkAccessPermission(1L, 2L));
    }

    @Test
    @DisplayName("checkAccessPermission 全员可见返回 true")
    void check_visibleToAll() {
        Resource r = new Resource();
        r.setId(1L);
        r.setVisibleToAll(true);
        when(resourceMapper.selectById(any())).thenReturn(r);
        assertTrue(service.checkAccessPermission(1L, 2L));
    }

    @Test
    @DisplayName("checkAccessPermission 非全员可见默认 true")
    void check_defaultTrue() {
        Resource r = new Resource();
        r.setId(1L);
        r.setVisibleToAll(false);
        when(resourceMapper.selectById(any())).thenReturn(r);
        assertTrue(service.checkAccessPermission(1L, 2L));
    }

    @Test
    @DisplayName("getResourceTree 按章节分组")
    void tree_ok() {
        Resource r1 = new Resource();
        r1.setId(1L);
        r1.setChapterId(1L);
        Resource r2 = new Resource();
        r2.setId(2L);
        r2.setChapterId(2L);
        when(resourceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(r1, r2));
        assertEquals(2, service.getResourceTree(1L).size());
    }

    @Test
    @DisplayName("searchResources 分页搜索")
    void search_ok() {
        Page<Resource> page = new Page<>(1, 10);
        page.setRecords(List.of(new Resource()));
        when(resourceMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        assertEquals(1, service.searchResources("k", 1L, 1, 10).getRecords().size());
    }

    @Test
    @DisplayName("getResourceStats 空资源")
    void stats_empty() {
        when(resourceMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        com.aicourse.vo.ResourceStatsVO r = service.getResourceStats(1L);
        assertEquals(0L, r.getTotalCount());
        assertEquals(0L, r.getTotalSize());
    }

    @Test
    @DisplayName("getResourceStats 含有效版本大小与类型分布")
    void stats_full() {
        Resource r1 = new Resource();
        r1.setId(1L);
        r1.setFileType("pdf");
        Resource r2 = new Resource();
        r2.setId(2L);
        r2.setFileType(null); // 归为 unknown
        when(resourceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(r1, r2));

        ResourceVersion v1 = new ResourceVersion();
        v1.setFileSize(100L);
        when(resourceVersionService.getActiveVersion(1L)).thenReturn(v1);
        when(resourceVersionService.getActiveVersion(2L)).thenReturn(null);

        com.aicourse.vo.ResourceStatsVO r = service.getResourceStats(1L);
        assertEquals(100L, r.getTotalSize());
        Map<String, Long> dist = r.getTypeDistribution();
        assertEquals(1L, dist.get("pdf"));
        assertEquals(1L, dist.get("unknown"));
    }

    @Test
    @DisplayName("toggleVisibility 资源不存在抛异常")
    void toggle_null() {
        when(resourceMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.toggleVisibility(1L, true));
    }

    @Test
    @DisplayName("toggleVisibility 成功")
    void toggle_ok() {
        Resource r = new Resource();
        r.setId(1L);
        when(resourceMapper.selectById(any())).thenReturn(r);
        when(resourceMapper.updateById(any(Resource.class))).thenReturn(1);
        service.toggleVisibility(1L, false);
        assertFalse(r.getVisibleToAll());
    }

    @Test
    @DisplayName("getResourcesByChapter 返回列表")
    void byChapter_ok() {
        when(resourceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new Resource()));
        assertEquals(1, service.getResourcesByChapter(1L, 2L).size());
    }
}
