package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.mapper.KnowledgePointMapper;
import com.aicourse.mapper.KnowledgePointRelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 纯 Mockito 单元测试：补充覆盖 KnowledgeRecommendationServiceImpl 的薄弱点/下一步/综合推荐分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeRecommendationServiceImplBoostTest {

    @Mock
    private KnowledgePointMapper knowledgePointMapper;
    @Mock
    private KnowledgePointRelationMapper relationMapper;

    private KnowledgeRecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeRecommendationServiceImpl(knowledgePointMapper, relationMapper);
    }

    private KnowledgePoint kp(Long id, String name) {
        KnowledgePoint p = new KnowledgePoint();
        p.setId(id);
        p.setName(name);
        p.setCourseId(1L);
        return p;
    }

    private KnowledgePointRelation rel(Long src, Long tgt, String type) {
        KnowledgePointRelation r = new KnowledgePointRelation();
        r.setSourceId(src);
        r.setTargetId(tgt);
        r.setRelationType(type);
        return r;
    }

    private void stubGraph(List<KnowledgePoint> points, List<KnowledgePointRelation> relations) {
        when(knowledgePointMapper.selectList(any())).thenReturn(points);
        when(relationMapper.selectList(any())).thenReturn(relations);
    }

    // ===== recommendFromWeakPoints =====

    @Test
    @DisplayName("recommendFromWeakPoints 入参为 null 返回空")
    void weak_null() {
        assertTrue(service.recommendFromWeakPoints(1L, null, 10).isEmpty());
    }

    @Test
    @DisplayName("recommendFromWeakPoints 空 map 返回空")
    void weak_empty() {
        assertTrue(service.recommendFromWeakPoints(1L, Map.of(), 10).isEmpty());
    }

    @Test
    @DisplayName("recommendFromWeakPoints 课程无知识点返回空")
    void weak_noPoints() {
        stubGraph(List.of(), List.of());
        assertTrue(service.recommendFromWeakPoints(1L, Map.of("代数", 30.0), 10).isEmpty());
    }

    @Test
    @DisplayName("recommendFromWeakPoints 前置+关联推荐")
    void weak_full() {
        List<KnowledgePoint> points = List.of(kp(1L, "代数"), kp(2L, "函数"));
        List<KnowledgePointRelation> relations = List.of(
                rel(1L, 2L, "PREREQUISITE"),
                rel(2L, 1L, "INCLUDES"));
        stubGraph(points, relations);
        List<Map<String, Object>> r = service.recommendFromWeakPoints(1L, Map.of("函数", 30.0), 10);
        assertEquals(1, r.size());
    }

    @Test
    @DisplayName("recommendFromWeakPoints 限制数量截断")
    void weak_limit() {
        List<KnowledgePoint> points = List.of(kp(1L, "代数"), kp(2L, "函数"));
        List<KnowledgePointRelation> relations = List.of(
                rel(1L, 2L, "PREREQUISITE"),
                rel(2L, 1L, "INCLUDES"));
        stubGraph(points, relations);
        List<Map<String, Object>> r = service.recommendFromWeakPoints(1L, Map.of("函数", 30.0), 1);
        assertEquals(1, r.size());
    }

    @Test
    @DisplayName("recommendFromWeakPoints 弱项名不匹配则跳过")
    void weak_nameMiss() {
        List<KnowledgePoint> points = List.of(kp(1L, "代数"), kp(2L, "函数"));
        List<KnowledgePointRelation> relations = List.of(
                rel(1L, 2L, "PREREQUISITE"),
                rel(2L, 1L, "INCLUDES"));
        stubGraph(points, relations);
        Map<String, Double> weak = new LinkedHashMap<>();
        weak.put("函数", 30.0);
        weak.put("不存在的知识点", 10.0);
        List<Map<String, Object>> r = service.recommendFromWeakPoints(1L, weak, 10);
        assertEquals(1, r.size());
    }

    // ===== recommendNextSteps =====

    @Test
    @DisplayName("recommendNextSteps 入参为 null 返回空")
    void next_null() {
        assertTrue(service.recommendNextSteps(1L, null, 10).isEmpty());
    }

    @Test
    @DisplayName("recommendNextSteps 课程无知识点返回空")
    void next_noPoints() {
        stubGraph(List.of(), List.of());
        assertTrue(service.recommendNextSteps(1L, Map.of("代数", 80.0), 10).isEmpty());
    }

    @Test
    @DisplayName("recommendNextSteps 掌握度不足跳过")
    void next_lowMastery() {
        List<KnowledgePoint> points = List.of(kp(1L, "代数"), kp(2L, "函数"));
        List<KnowledgePointRelation> relations = List.of(rel(1L, 2L, "PREREQUISITE"));
        stubGraph(points, relations);
        assertTrue(service.recommendNextSteps(1L, Map.of("代数", 50.0), 10).isEmpty());
    }

    @Test
    @DisplayName("recommendNextSteps 推荐后继知识点")
    void next_ok() {
        List<KnowledgePoint> points = List.of(kp(1L, "代数"), kp(2L, "函数"));
        List<KnowledgePointRelation> relations = List.of(rel(1L, 2L, "PREREQUISITE"));
        stubGraph(points, relations);
        List<Map<String, Object>> r = service.recommendNextSteps(1L, Map.of("代数", 80.0), 10);
        assertEquals(1, r.size());
    }

    @Test
    @DisplayName("recommendNextSteps 已掌握的后继跳过")
    void next_alreadyMastered() {
        List<KnowledgePoint> points = List.of(kp(1L, "代数"), kp(2L, "函数"));
        List<KnowledgePointRelation> relations = List.of(
                rel(1L, 2L, "PREREQUISITE"),
                rel(2L, 1L, "PREREQUISITE"));
        stubGraph(points, relations);
        Map<String, Double> mastered = new LinkedHashMap<>();
        mastered.put("代数", 80.0);
        mastered.put("函数", 80.0);
        assertTrue(service.recommendNextSteps(1L, mastered, 10).isEmpty());
    }

    // ===== recommendComprehensive =====

    @Test
    @DisplayName("recommendComprehensive 仅薄弱点")
    void comprehensive_weakOnly() {
        List<KnowledgePoint> points = List.of(kp(1L, "代数"), kp(2L, "函数"));
        List<KnowledgePointRelation> relations = List.of(
                rel(1L, 2L, "PREREQUISITE"),
                rel(2L, 1L, "INCLUDES"));
        stubGraph(points, relations);
        List<Map<String, Object>> r = service.recommendComprehensive(
                1L, Map.of("函数", 30.0), null, 10);
        assertEquals(1, r.size());
    }

    @Test
    @DisplayName("recommendComprehensive 仅掌握点")
    void comprehensive_masteredOnly() {
        List<KnowledgePoint> points = List.of(kp(1L, "代数"), kp(2L, "函数"));
        List<KnowledgePointRelation> relations = List.of(rel(1L, 2L, "PREREQUISITE"));
        stubGraph(points, relations);
        List<Map<String, Object>> r = service.recommendComprehensive(
                1L, null, Map.of("代数", 80.0), 10);
        assertEquals(1, r.size());
    }

    @Test
    @DisplayName("recommendComprehensive 综合去重")
    void comprehensive_dedup() {
        List<KnowledgePoint> points = List.of(kp(1L, "代数"), kp(2L, "函数"));
        List<KnowledgePointRelation> relations = List.of(
                rel(1L, 2L, "PREREQUISITE"),
                rel(2L, 1L, "INCLUDES"),
                rel(2L, 1L, "PREREQUISITE"));
        stubGraph(points, relations);
        List<Map<String, Object>> r = service.recommendComprehensive(
                1L, Map.of("函数", 30.0), Map.of("函数", 80.0), 10);
        // 薄弱点"函数"经前置/关联回溯都指向同一 id(代数)，去重后仅 1 条；掌握点后继为"代数"再次被去重
        assertEquals(1, r.size());
    }
}
