package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.LearningPathMapper;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.LearningPathNodeService;
import com.aicourse.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 学习路径服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class LearningPathServiceImplTest {

    @Mock private LearningPathNodeService nodeService;
    @Mock private KnowledgePointService knowledgePointService;
    @Mock private ObjectMapper objectMapper;
    @Mock private LearningPathMapper learningPathMapper;
    @Mock private CourseChapterService courseChapterService;
    @Mock private TaskService taskService;

    @InjectMocks private LearningPathServiceImpl learningPathService;

    private LearningPath testPath;
    private KnowledgePoint testKnowledgePoint;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(learningPathService, "baseMapper", learningPathMapper);
        // generatePath 内部会调用 courseChapterService.list(...) 与 taskService.list(...)，
        // 桩化为空列表避免 NPE；仅 generatePath 相关用例使用，故用 lenient 避免严格桩检查报错。
        lenient().when(courseChapterService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        lenient().when(taskService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());

        testPath = new LearningPath();
        testPath.setId(1L);
        testPath.setStudentId(100L);
        testPath.setCourseId(10L);
        testPath.setPathName("Java学习路线");
        testPath.setDescription("系统学习Java");
        testPath.setLearningGoal("掌握Java核心知识");
        testPath.setStatus("ACTIVE");
        testPath.setCurrentStage(1);
        testPath.setTotalStages(3);
        testPath.setCompletionRate(new BigDecimal("33.33"));

        testKnowledgePoint = new KnowledgePoint();
        testKnowledgePoint.setId(1L);
        testKnowledgePoint.setName("Java基础");
        testKnowledgePoint.setCourseId(10L);
        testKnowledgePoint.setEstimatedTime(60);
        testKnowledgePoint.setPointType("STUDY");
    }

    // ==================== generatePath ====================

    @Test
    @DisplayName("生成学习路径 - 成功生成")
    void testGeneratePath_Success() throws Exception {
        when(learningPathMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(knowledgePointService.getRootPoints(10L)).thenReturn(List.of(testKnowledgePoint));
        when(knowledgePointService.getChildPoints(1L)).thenReturn(Collections.emptyList());
        when(objectMapper.writeValueAsString(any())).thenReturn("[]", "[]", "{}");
        when(nodeService.save(any(LearningPathNode.class))).thenReturn(true);
        when(learningPathMapper.insert(any(LearningPath.class))).thenReturn(1);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);

        LearningPath result = learningPathService.generatePath(100L, 10L, "Java学习路线", "掌握Java");

        assertNotNull(result);
        assertEquals("ACTIVE", result.getStatus());
        assertEquals(1, result.getCurrentStage());
        verify(learningPathMapper).insert(any(LearningPath.class));
    }

    @Test
    @DisplayName("生成学习路径 - 已有活跃路径抛出异常")
    void testGeneratePath_DuplicateActivePath() {
        when(learningPathMapper.selectOne(any(), anyBoolean())).thenReturn(testPath);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> learningPathService.generatePath(100L, 10L, "Java学习路线", "掌握Java"));
        assertTrue(exception.getMessage().contains("已有进行中的学习路径"));
    }

    @Test
    @DisplayName("生成学习路径 - 无知识点抛出异常")
    void testGeneratePath_NoKnowledgePoints() {
        when(learningPathMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(knowledgePointService.getRootPoints(10L)).thenReturn(Collections.emptyList());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> learningPathService.generatePath(100L, 10L, "Java学习路线", "掌握Java"));
        assertEquals("课程下暂无知识点，无法生成学习路径", exception.getMessage());
    }

    // ==================== updateProgress ====================

    @Test
    @DisplayName("更新进度 - 计算完成率")
    void testUpdateProgress_PartialCompletion() {
        List<LearningPathNode> nodes = new ArrayList<>();
        LearningPathNode completed = new LearningPathNode();
        completed.setStatus("COMPLETED");
        nodes.add(completed);

        LearningPathNode inProgress = new LearningPathNode();
        inProgress.setStatus("IN_PROGRESS");
        nodes.add(inProgress);

        LearningPathNode notStarted = new LearningPathNode();
        notStarted.setStatus("NOT_STARTED");
        nodes.add(notStarted);

        when(nodeService.getPathNodes(1L)).thenReturn(nodes);
        when(learningPathMapper.selectById(1L)).thenReturn(testPath);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);

        assertDoesNotThrow(() -> learningPathService.updateProgress(1L));
        assertEquals(new BigDecimal("33.33"), testPath.getCompletionRate());
    }

    @Test
    @DisplayName("更新进度 - 全部完成自动标记为已完成")
    void testUpdateProgress_AutoCompletion() {
        List<LearningPathNode> nodes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            LearningPathNode node = new LearningPathNode();
            node.setStatus("COMPLETED");
            nodes.add(node);
        }

        when(nodeService.getPathNodes(1L)).thenReturn(nodes);
        when(learningPathMapper.selectById(1L)).thenReturn(testPath);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);

        assertDoesNotThrow(() -> learningPathService.updateProgress(1L));
        assertEquals("COMPLETED", testPath.getStatus());
        assertEquals(new BigDecimal("100.00"), testPath.getCompletionRate());
    }

    // ==================== advanceToNextStage ====================

    @Test
    @DisplayName("进入下一阶段 - 阶段推进")
    void testAdvanceToNextStage_Success() {
        when(learningPathMapper.selectById(1L)).thenReturn(testPath);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);

        assertDoesNotThrow(() -> learningPathService.advanceToNextStage(1L));
        assertEquals(2, testPath.getCurrentStage());
    }

    @Test
    @DisplayName("进入下一阶段 - 最后阶段自动完成")
    void testAdvanceToNextStage_LastStage() {
        testPath.setCurrentStage(3);
        testPath.setTotalStages(3);
        when(learningPathMapper.selectById(1L)).thenReturn(testPath);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);

        assertDoesNotThrow(() -> learningPathService.advanceToNextStage(1L));
        assertEquals("COMPLETED", testPath.getStatus());
    }

    // ==================== completePath ====================

    @Test
    @DisplayName("完成学习路径 - 状态变更为已完成")
    void testCompletePath_Success() {
        when(learningPathMapper.selectById(1L)).thenReturn(testPath);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);

        assertDoesNotThrow(() -> learningPathService.completePath(1L));
        assertEquals("COMPLETED", testPath.getStatus());
        assertEquals(new BigDecimal("100.00"), testPath.getCompletionRate());
        assertNotNull(testPath.getActualCompletionDate());
    }

    @Test
    @DisplayName("完成学习路径 - 路径不存在抛出异常")
    void testCompletePath_NotFound() {
        when(learningPathMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> learningPathService.completePath(999L));
        assertTrue(exception.getMessage().contains("学习路径不存在"));
    }

    // ==================== pausePath / resumePath ====================

    @Test
    @DisplayName("暂停学习路径 - 状态变更为已暂停")
    void testPausePath_Success() {
        when(learningPathMapper.selectById(1L)).thenReturn(testPath);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);

        assertDoesNotThrow(() -> learningPathService.pausePath(1L));
        assertEquals("PAUSED", testPath.getStatus());
    }

    @Test
    @DisplayName("暂停学习路径 - 非活跃状态抛出异常")
    void testPausePath_NotActive() {
        testPath.setStatus("COMPLETED");
        when(learningPathMapper.selectById(1L)).thenReturn(testPath);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> learningPathService.pausePath(1L));
        assertEquals("只有活跃状态的路径才能暂停", exception.getMessage());
    }

    @Test
    @DisplayName("恢复学习路径 - 状态变更为活跃")
    void testResumePath_Success() {
        testPath.setStatus("PAUSED");
        when(learningPathMapper.selectById(1L)).thenReturn(testPath);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);

        assertDoesNotThrow(() -> learningPathService.resumePath(1L));
        assertEquals("ACTIVE", testPath.getStatus());
    }

    @Test
    @DisplayName("恢复学习路径 - 非暂停状态抛出异常")
    void testResumePath_NotPaused() {
        testPath.setStatus("ACTIVE");
        when(learningPathMapper.selectById(1L)).thenReturn(testPath);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> learningPathService.resumePath(1L));
        assertEquals("只有暂停状态的路径才能恢复", exception.getMessage());
    }

    // ==================== getPathDetail ====================

    @Test
    @DisplayName("获取路径详情 - 包含节点和统计")
    void testGetPathDetail_WithNodesAndStats() {
        when(learningPathMapper.selectById(1L)).thenReturn(testPath);

        List<LearningPathNode> nodes = new ArrayList<>();
        LearningPathNode node = new LearningPathNode();
        node.setId(1L);
        node.setTitle("Java变量");
        node.setStatus("COMPLETED");
        nodes.add(node);

        Map<String, Long> statusStats = new HashMap<>();
        statusStats.put("COMPLETED", 1L);
        statusStats.put("NOT_STARTED", 2L);

        when(nodeService.getPathNodes(1L)).thenReturn(nodes);
        when(nodeService.getNodeStatusStats(1L)).thenReturn(statusStats);

        Map<String, Object> detail = learningPathService.getPathDetail(1L);

        assertNotNull(detail);
        assertEquals(testPath, detail.get("path"));
        assertEquals(nodes, detail.get("nodes"));
        assertEquals(1, detail.get("totalNodes"));
        assertNotNull(detail.get("statusStats"));
    }

    @Test
    @DisplayName("获取路径详情 - 路径不存在抛出异常")
    void testGetPathDetail_NotFound() {
        when(learningPathMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> learningPathService.getPathDetail(999L));
        assertTrue(exception.getMessage().contains("学习路径不存在"));
    }

    // ==================== estimateCompletionDate ====================

    @Test
    @DisplayName("预估完成日期 - 根据平均耗时计算")
    void testEstimateCompletionDate_WithCompletedNodes() {
        when(learningPathMapper.selectById(1L)).thenReturn(testPath);

        LearningPathNode completed1 = new LearningPathNode();
        completed1.setStatus("COMPLETED");
        completed1.setActualTime(60);

        LearningPathNode completed2 = new LearningPathNode();
        completed2.setStatus("COMPLETED");
        completed2.setActualTime(120);

        LearningPathNode pending = new LearningPathNode();
        pending.setStatus("IN_PROGRESS");

        when(nodeService.getPathNodes(1L)).thenReturn(List.of(completed1, completed2, pending));
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);

        assertDoesNotThrow(() -> learningPathService.estimateCompletionDate(1L));
        assertNotNull(testPath.getEstimatedCompletionDate());
        assertTrue(testPath.getEstimatedCompletionDate().isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("预估完成日期 - 无已完成节点使用默认30天")
    void testEstimateCompletionDate_NoCompletedNodes() {
        when(learningPathMapper.selectById(1L)).thenReturn(testPath);

        LearningPathNode pending = new LearningPathNode();
        pending.setStatus("IN_PROGRESS");

        when(nodeService.getPathNodes(1L)).thenReturn(List.of(pending));
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);

        assertDoesNotThrow(() -> learningPathService.estimateCompletionDate(1L));
        assertNotNull(testPath.getEstimatedCompletionDate());
    }
}