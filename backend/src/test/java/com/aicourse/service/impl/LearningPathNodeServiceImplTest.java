package com.aicourse.service.impl;

import com.aicourse.entity.LearningPathNode;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.LearningPathNodeMapper;
import com.aicourse.service.LearningPathService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LearningPathNodeServiceImplTest {

    @Mock
    private LearningPathNodeMapper learningPathNodeMapper;

    @Mock
    private LearningPathService learningPathService;

    private LearningPathNodeServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), LearningPathNode.class);
        service = new LearningPathNodeServiceImpl(learningPathService);
        ReflectionTestUtils.setField(service, "baseMapper", learningPathNodeMapper);
    }

    private LearningPathNode node(Long id, Long pathId, String status, Integer orderIndex) {
        LearningPathNode n = new LearningPathNode();
        n.setId(id);
        n.setLearningPathId(pathId);
        n.setStatus(status);
        n.setOrderIndex(orderIndex);
        return n;
    }

    // ===== getPathNodes =====
    @Test
    @DisplayName("getPathNodes 返回列表")
    void getPathNodes_ok() {
        when(learningPathNodeMapper.selectList(any())).thenReturn(List.of(
                node(1L, 5L, "NOT_STARTED", 1), node(2L, 5L, "COMPLETED", 2)));
        List<LearningPathNode> r = service.getPathNodes(5L);
        assertEquals(2, r.size());
    }

    // ===== getCurrentNode (getOne -> selectOne(Wrapper, boolean)) =====
    @Test
    @DisplayName("getCurrentNode 命中")
    void getCurrentNode_ok() {
        LearningPathNode n = node(7L, 5L, "IN_PROGRESS", 3);
        when(learningPathNodeMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(n);
        assertSame(n, service.getCurrentNode(5L));
    }

    @Test
    @DisplayName("getCurrentNode 无进行中返回 null")
    void getCurrentNode_none() {
        when(learningPathNodeMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        assertNull(service.getCurrentNode(5L));
    }

    // ===== startNode =====
    @Test
    @DisplayName("startNode 正常")
    void start_ok() {
        LearningPathNode n = node(1L, 5L, "NOT_STARTED", 1);
        when(learningPathNodeMapper.selectById(1L)).thenReturn(n);
        when(learningPathNodeMapper.updateById(any(LearningPathNode.class))).thenReturn(1);
        service.startNode(1L);
        assertEquals("IN_PROGRESS", n.getStatus());
    }

    @Test
    @DisplayName("startNode 不存在抛异常")
    void start_notFound() {
        when(learningPathNodeMapper.selectById(2L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.startNode(2L));
    }

    @Test
    @DisplayName("startNode 已非未开始抛异常")
    void start_already() {
        LearningPathNode n = node(1L, 5L, "IN_PROGRESS", 1);
        when(learningPathNodeMapper.selectById(1L)).thenReturn(n);
        assertThrows(BusinessException.class, () -> service.startNode(1L));
    }

    // ===== completeNode =====
    @Test
    @DisplayName("completeNode 正常并触发进度更新")
    void complete_ok() {
        LearningPathNode n = node(1L, 5L, "IN_PROGRESS", 1);
        when(learningPathNodeMapper.selectById(1L)).thenReturn(n);
        when(learningPathNodeMapper.updateById(any(LearningPathNode.class))).thenReturn(1);
        service.completeNode(1L, new BigDecimal("95"), 120);
        assertEquals("COMPLETED", n.getStatus());
        assertEquals(new BigDecimal("95"), n.getScore());
        assertEquals(Integer.valueOf(120), n.getActualTime());
        assertNotNull(n.getCompletedAt());
        verify(learningPathService).updateProgress(5L);
    }

    @Test
    @DisplayName("completeNode 已完成抛异常")
    void complete_already() {
        LearningPathNode n = node(1L, 5L, "COMPLETED", 1);
        when(learningPathNodeMapper.selectById(1L)).thenReturn(n);
        assertThrows(BusinessException.class, () -> service.completeNode(1L, new BigDecimal("95"), 120));
    }

    // ===== skipNode =====
    @Test
    @DisplayName("skipNode 正常")
    void skip_ok() {
        LearningPathNode n = node(1L, 5L, "NOT_STARTED", 1);
        when(learningPathNodeMapper.selectById(1L)).thenReturn(n);
        when(learningPathNodeMapper.updateById(any(LearningPathNode.class))).thenReturn(1);
        service.skipNode(1L);
        assertEquals("SKIPPED", n.getStatus());
        verify(learningPathService).updateProgress(5L);
    }

    @Test
    @DisplayName("skipNode 已完成抛异常")
    void skip_already() {
        LearningPathNode n = node(1L, 5L, "COMPLETED", 1);
        when(learningPathNodeMapper.selectById(1L)).thenReturn(n);
        assertThrows(BusinessException.class, () -> service.skipNode(1L));
    }

    // ===== getNodeStatusStats =====
    @Test
    @DisplayName("getNodeStatusStats 聚合含 OTHER")
    void stats_ok() {
        List<LearningPathNode> nodes = List.of(
                node(1L, 5L, "NOT_STARTED", 1),
                node(2L, 5L, "IN_PROGRESS", 2),
                node(3L, 5L, "COMPLETED", 3),
                node(4L, 5L, "SKIPPED", 4),
                node(5L, 5L, "WEIRD", 5));
        when(learningPathNodeMapper.selectList(any())).thenReturn(nodes);
        Map<String, Long> s = service.getNodeStatusStats(5L);
        assertEquals(1L, s.get("NOT_STARTED"));
        assertEquals(1L, s.get("IN_PROGRESS"));
        assertEquals(1L, s.get("COMPLETED"));
        assertEquals(1L, s.get("SKIPPED"));
        assertEquals(1L, s.get("OTHER"));
    }

    // ===== updateNodeStatus =====
    @Test
    @DisplayName("updateNodeStatus 正常")
    void updateStatus_ok() {
        LearningPathNode n = node(1L, 5L, "NOT_STARTED", 1);
        when(learningPathNodeMapper.selectById(1L)).thenReturn(n);
        when(learningPathNodeMapper.updateById(any(LearningPathNode.class))).thenReturn(1);
        service.updateNodeStatus(1L, "IN_PROGRESS", 30);
        assertEquals("IN_PROGRESS", n.getStatus());
        assertEquals(Integer.valueOf(30), n.getActualTime());
        verify(learningPathService).updateProgress(5L);
    }

    @Test
    @DisplayName("updateNodeStatus 无效状态抛异常")
    void updateStatus_invalid() {
        LearningPathNode n = node(1L, 5L, "NOT_STARTED", 1);
        when(learningPathNodeMapper.selectById(1L)).thenReturn(n);
        assertThrows(BusinessException.class, () -> service.updateNodeStatus(1L, "BOGUS", 30));
    }

    @Test
    @DisplayName("updateNodeStatus 切到完成自动设完成时间")
    void updateStatus_toCompleted() {
        LearningPathNode n = node(1L, 5L, "IN_PROGRESS", 1);
        when(learningPathNodeMapper.selectById(1L)).thenReturn(n);
        when(learningPathNodeMapper.updateById(any(LearningPathNode.class))).thenReturn(1);
        service.updateNodeStatus(1L, "COMPLETED", null);
        assertEquals("COMPLETED", n.getStatus());
        assertNotNull(n.getCompletedAt());
    }

    // ===== recordScore =====
    @Test
    @DisplayName("recordScore 正常")
    void recordScore_ok() {
        LearningPathNode n = node(1L, 5L, "IN_PROGRESS", 1);
        when(learningPathNodeMapper.selectById(1L)).thenReturn(n);
        when(learningPathNodeMapper.updateById(any(LearningPathNode.class))).thenReturn(1);
        service.recordScore(1L, new BigDecimal("88"));
        assertEquals(new BigDecimal("88"), n.getScore());
    }

    @Test
    @DisplayName("recordScore 空分抛异常")
    void recordScore_null() {
        LearningPathNode n = node(1L, 5L, "IN_PROGRESS", 1);
        when(learningPathNodeMapper.selectById(1L)).thenReturn(n);
        assertThrows(BusinessException.class, () -> service.recordScore(1L, null));
    }

    @Test
    @DisplayName("recordScore 越界抛异常")
    void recordScore_outOfRange() {
        LearningPathNode n = node(1L, 5L, "IN_PROGRESS", 1);
        when(learningPathNodeMapper.selectById(1L)).thenReturn(n);
        assertThrows(BusinessException.class, () -> service.recordScore(1L, new BigDecimal("150")));
    }
}
