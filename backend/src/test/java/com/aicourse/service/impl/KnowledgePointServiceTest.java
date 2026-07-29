package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.mapper.KnowledgePointMapper;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.WrongQuestionService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
 * 知识点服务单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgePointServiceTest {

    @Mock private KnowledgePointMapper mapper;
    @Mock private KnowledgePointRelationService relationService;
    @Mock private QuestionService questionService;
    @Mock private WrongQuestionService wrongQuestionService;

    @InjectMocks private KnowledgePointServiceImpl service;

    private KnowledgePoint rootPoint;
    private KnowledgePoint childPoint;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        rootPoint = new KnowledgePoint();
        rootPoint.setId(1L);
        rootPoint.setName("Root Point");
        rootPoint.setCourseId(100L);
        rootPoint.setParentId(null);
        rootPoint.setDifficultyLevel("EASY");
        rootPoint.setImportance(5);
        rootPoint.setOrderIndex(1);
        rootPoint.setPointType("CONCEPT");

        childPoint = new KnowledgePoint();
        childPoint.setId(2L);
        childPoint.setName("Child Point");
        childPoint.setCourseId(100L);
        childPoint.setParentId(1L);
        childPoint.setDifficultyLevel("MEDIUM");
        childPoint.setImportance(3);
        childPoint.setOrderIndex(2);
        childPoint.setPointType("SKILL");
    }

    @Test
    @DisplayName("根据ID查询-成功")
    void testGetById() {
        when(mapper.selectById(1L)).thenReturn(rootPoint);
        KnowledgePoint result = service.getById(1L);
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Root Point", result.getName());
        verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("根据ID查询-不存在返回null")
    void testGetById_NotFound() {
        when(mapper.selectById(999L)).thenReturn(null);
        KnowledgePoint result = service.getById(999L);
        assertNull(result);
    }

    @Test
    @DisplayName("获取知识树-成功构建层级结构")
    void testGetKnowledgeTree() {
        List<KnowledgePoint> allPoints = Arrays.asList(rootPoint, childPoint);
        when(mapper.selectList(any())).thenReturn(allPoints);
        List<Map<String, Object>> tree = service.getKnowledgeTree(100L);
        assertNotNull(tree);
        assertEquals(1, tree.size());
        Map<String, Object> rootNode = tree.get(0);
        assertEquals(1L, rootNode.get("id"));
        assertEquals("Root Point", rootNode.get("name"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) rootNode.get("children");
        assertNotNull(children);
        assertEquals(1, children.size());
        assertEquals(2L, children.get(0).get("id"));
    }

    @Test
    @DisplayName("获取知识树-课程无知识点返回空列表")
    void testGetKnowledgeTree_Empty() {
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        List<Map<String, Object>> tree = service.getKnowledgeTree(999L);
        assertNotNull(tree);
        assertTrue(tree.isEmpty());
    }

    @Test
    @DisplayName("获取子知识点-成功")
    void testGetChildPoints() {
        List<KnowledgePoint> children = Collections.singletonList(childPoint);
        when(mapper.selectList(any())).thenReturn(children);
        List<KnowledgePoint> result = service.getChildPoints(1L);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Child Point", result.get(0).getName());
    }

    @Test
    @DisplayName("获取子知识点-无子节点返回空列表")
    void testGetChildPoints_Empty() {
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        List<KnowledgePoint> result = service.getChildPoints(999L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("获取前置依赖-成功")
    void testGetPrerequisites() {
        KnowledgePointRelation relation = new KnowledgePointRelation();
        relation.setSourceId(1L);
        relation.setTargetId(2L);
        relation.setRelationType("PREREQUISITE");
        when(relationService.list(any(Wrapper.class))).thenReturn(Collections.singletonList(relation));
        when(mapper.selectBatchIds(anyList())).thenReturn(Collections.singletonList(rootPoint));
        List<KnowledgePoint> result = service.getPrerequisites(2L);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Root Point", result.get(0).getName());
    }

    @Test
    @DisplayName("获取前置依赖-无依赖返回空列表")
    void testGetPrerequisites_Empty() {
        when(relationService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        List<KnowledgePoint> result = service.getPrerequisites(999L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("获取后续知识点-成功")
    void testGetSubsequentPoints() {
        KnowledgePointRelation relation = new KnowledgePointRelation();
        relation.setSourceId(1L);
        relation.setTargetId(2L);
        relation.setRelationType("PREREQUISITE");
        when(relationService.list(any(Wrapper.class))).thenReturn(Collections.singletonList(relation));
        when(mapper.selectBatchIds(anyList())).thenReturn(Collections.singletonList(childPoint));
        List<KnowledgePoint> result = service.getSubsequentPoints(1L);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Child Point", result.get(0).getName());
    }

    @Test
    @DisplayName("创建知识点-校验通过成功创建")
    void testCreateWithValidation_Success() {
        KnowledgePoint input = new KnowledgePoint();
        input.setName("New Point");
        input.setCourseId(100L);
        input.setDifficultyLevel("HARD");
        input.setImportance(4);
        doAnswer(invocation -> {
            KnowledgePoint p = invocation.getArgument(0);
            p.setId(3L);
            return 1;
        }).when(mapper).insert(any(KnowledgePoint.class));
        KnowledgePoint result = service.createWithValidation(input);
        assertNotNull(result);
        assertEquals("New Point", result.getName());
        assertEquals("HARD", result.getDifficultyLevel());
        assertEquals(Integer.valueOf(4), result.getImportance());
    }

    @Test
    @DisplayName("创建知识点-名称为空抛出异常")
    void testCreateWithValidation_EmptyName() {
        KnowledgePoint input = new KnowledgePoint();
        input.setName("");
        input.setCourseId(100L);
        try {
            service.createWithValidation(input);
            fail("Expected BusinessException");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("知识点名称不能为空"));
        }
    }

    @Test
    @DisplayName("创建知识点-未指定课程抛出异常")
    void testCreateWithValidation_NullCourseId() {
        KnowledgePoint input = new KnowledgePoint();
        input.setName("Test Point");
        try {
            service.createWithValidation(input);
            fail("Expected BusinessException");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("必须指定所属课程"));
        }
    }

    @Test
    @DisplayName("创建知识点-无效难度等级抛出异常")
    void testCreateWithValidation_InvalidDifficulty() {
        KnowledgePoint input = new KnowledgePoint();
        input.setName("Test Point");
        input.setCourseId(100L);
        input.setDifficultyLevel("IMPOSSIBLE");
        try {
            service.createWithValidation(input);
            fail("Expected BusinessException");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("难度等级"));
        }
    }

    @Test
    @DisplayName("获取根级知识点-成功")
    void testGetRootPoints() {
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(rootPoint));
        List<KnowledgePoint> result = service.getRootPoints(100L);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Root Point", result.get(0).getName());
        assertNull(result.get(0).getParentId());
    }

    @Test
    @DisplayName("按条件筛选知识点-默认排序")
    void testGetListWithFilters_Default() {
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(rootPoint));
        List<KnowledgePoint> result = service.getListWithFilters(100L, null, null, null, null, true);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("按条件筛选知识点-按重要度降序")
    void testGetListWithFilters_ByImportance() {
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(rootPoint));
        List<KnowledgePoint> result = service.getListWithFilters(100L, "EASY", 1, 5, "importance", false);
        assertNotNull(result);
        assertEquals(1, result.size());
    }
}
