package com.aicourse.service.impl;

import com.aicourse.entity.Resource;
import com.aicourse.entity.ResourceVersion;
import com.aicourse.mapper.ResourceMapper;
import com.aicourse.service.ResourceVersionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 资源服务单元测试。
 * 注意：Mapper 的 updateById 等重载方法需使用类型安全的 any(Resource.class) 匹配器，
 * 避免 MyBatis-Plus 3.5.7 BaseMapper 中 updateById(T entity) / updateById(Collection<T>) 的重载歧义。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResourceServiceTest {

    @Mock private ResourceMapper mapper;
    @Mock private ResourceVersionService resourceVersionService;

    @InjectMocks private ResourceServiceImpl service;

    private Resource resource1;
    private Resource resource2;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        resource1 = new Resource();
        resource1.setId(1L);
        resource1.setFileName("lecture1.pdf");
        resource1.setFileType("pdf");
        resource1.setUrl("/uploads/lecture1.pdf");
        resource1.setCourseId(100L);
        resource1.setChapterId(10L);
        resource1.setUploaderId(1L);
        resource1.setUploadTime(LocalDateTime.now());
        resource1.setDescription("Lecture 1 notes");
        resource1.setVisibleToAll(true);

        resource2 = new Resource();
        resource2.setId(2L);
        resource2.setFileName("video1.mp4");
        resource2.setFileType("mp4");
        resource2.setUrl("/uploads/video1.mp4");
        resource2.setCourseId(100L);
        resource2.setChapterId(20L);
        resource2.setUploaderId(1L);
        resource2.setUploadTime(LocalDateTime.now());
        resource2.setDescription("Video tutorial");
        resource2.setVisibleToAll(false);
    }

    @Test
    @DisplayName("按课程ID查询资源列表-成功")
    void testGetByCourseId() {
        when(mapper.selectList(any())).thenReturn(Arrays.asList(resource1, resource2));
        List<Resource> result = service.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Resource>()
                        .eq(Resource::getCourseId, 100L));
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("lecture1.pdf", result.get(0).getFileName());
        assertEquals("video1.mp4", result.get(1).getFileName());
    }

    @Test
    @DisplayName("按课程ID查询资源列表-课程无资源返回空列表")
    void testGetByCourseId_Empty() {
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        List<Resource> result = service.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Resource>()
                        .eq(Resource::getCourseId, 999L));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("上传资源-成功")
    void testUploadResource() {
        Resource newResource = new Resource();
        newResource.setFileName("new-lecture.pptx");
        newResource.setFileType("pptx");
        newResource.setCourseId(100L);
        newResource.setUploaderId(1L);
        doAnswer(invocation -> {
            Resource r = invocation.getArgument(0);
            r.setId(3L);
            return 1;
        }).when(mapper).insert(any(Resource.class));
        boolean result = service.save(newResource);
        assertTrue(result);
        assertNotNull(newResource.getId());
        assertEquals(3L, newResource.getId());
        verify(mapper).insert(any(Resource.class));
    }

    @Test
    @DisplayName("上传资源-保存失败")
    void testUploadResource_Failed() {
        when(mapper.insert(any(Resource.class))).thenReturn(0);
        boolean result = service.save(new Resource());
        assertFalse(result);
    }

    @Test
    @DisplayName("权限校验-全员可见资源返回有权限")
    void testCheckAccessPermission_VisibleToAll() {
        when(mapper.selectById(1L)).thenReturn(resource1);
        boolean result = service.checkAccessPermission(1L, 5L);
        assertTrue(result);
    }

    @Test
    @DisplayName("权限校验-非全员可见资源默认通过")
    void testCheckAccessPermission_NotVisibleToAll() {
        when(mapper.selectById(2L)).thenReturn(resource2);
        boolean result = service.checkAccessPermission(2L, 5L);
        assertTrue(result);
    }

    @Test
    @DisplayName("权限校验-资源不存在抛出异常")
    void testCheckAccessPermission_ResourceNotFound() {
        when(mapper.selectById(999L)).thenReturn(null);
        try {
            service.checkAccessPermission(999L, 5L);
            fail("Expected BusinessException");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("资源不存在"));
        }
    }

    @Test
    @DisplayName("获取资源树-按章节分组")
    void testGetResourceTree() {
        when(mapper.selectList(any())).thenReturn(Arrays.asList(resource1, resource2));
        List<Map<String, Object>> tree = service.getResourceTree(100L);
        assertNotNull(tree);
        assertEquals(2, tree.size());
        for (Map<String, Object> node : tree) {
            assertNotNull(node.get("chapterId"));
            assertNotNull(node.get("resources"));
        }
    }

    @Test
    @DisplayName("获取资源树-课程无资源返回空列表")
    void testGetResourceTree_Empty() {
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        List<Map<String, Object>> tree = service.getResourceTree(999L);
        assertNotNull(tree);
        assertTrue(tree.isEmpty());
    }

    @Test
    @DisplayName("资源搜索-按关键词搜索成功")
    void testSearchResources() {
        Page<Resource> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Collections.singletonList(resource1));
        mockPage.setTotal(1);
        when(mapper.selectPage(any(Page.class), any())).thenReturn(mockPage);
        Page<Resource> result = service.searchResources("lecture", 100L, 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("lecture1.pdf", result.getRecords().get(0).getFileName());
    }

    @Test
    @DisplayName("切换可见性-成功")
    void testToggleVisibility() {
        when(mapper.selectById(1L)).thenReturn(resource1);
        // 使用 any(Resource.class) 精准匹配单实体重载，避免 MyBatis-Plus 3.5.7 的 updateById(Collection) 重载歧义
        when(mapper.updateById(any(Resource.class))).thenReturn(1);

        service.toggleVisibility(1L, false);

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(mapper).updateById(captor.capture());
        assertFalse(captor.getValue().isVisibleToAll());
    }

    @Test
    @DisplayName("切换可见性-资源不存在抛出异常")
    void testToggleVisibility_ResourceNotFound() {
        when(mapper.selectById(999L)).thenReturn(null);
        try {
            service.toggleVisibility(999L, true);
            fail("Expected BusinessException");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("资源不存在"));
        }
    }

    @Test
    @DisplayName("按章节获取资源-成功")
    void testGetResourcesByChapter() {
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(resource1));
        List<Resource> result = service.getResourcesByChapter(100L, 10L);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("lecture1.pdf", result.get(0).getFileName());
    }

    @Test
    @DisplayName("获取资源统计-成功（含版本文件大小）")
    void testGetResourceStats() {
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(resource1));
        ResourceVersion activeVersion = new ResourceVersion();
        activeVersion.setFileSize(1024000L);
        when(resourceVersionService.getActiveVersion(1L)).thenReturn(activeVersion);
        Map<String, Object> stats = service.getResourceStats(100L);
        assertNotNull(stats);
        assertEquals(1L, stats.get("totalCount"));
        assertEquals(1024000L, stats.get("totalSize"));
        assertNotNull(stats.get("typeDistribution"));
    }
}
