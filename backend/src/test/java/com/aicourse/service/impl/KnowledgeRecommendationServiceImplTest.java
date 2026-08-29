package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.mapper.KnowledgePointMapper;
import com.aicourse.mapper.KnowledgePointRelationMapper;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * KnowledgeRecommendationServiceImpl 单元测试（B 主战场 P1，缺口 710）。
 * 纯算法服务，不继承 ServiceImpl；依赖 KnowledgePointMapper / KnowledgePointRelationMapper 两个 mock；
 * 私有 lambda wrapper 涉及 KnowledgePoint / KnowledgePointRelation → 统一注册 TableInfo。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeRecommendationServiceImplTest {

    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private KnowledgePointRelationMapper relationMapper;

    private KnowledgeRecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), KnowledgePoint.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), KnowledgePointRelation.class);

        service = new KnowledgeRecommendationServiceImpl(knowledgePointMapper, relationMapper);

        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(relationMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());
    }

    private KnowledgePoint kp(Long id, String name) {
        KnowledgePoint p = new KnowledgePoint();
        p.setId(id); p.setName(name); p.setCourseId(1L);
        p.setDescription("desc-" + name); p.setDifficultyLevel("2");
        return p;
    }

    private KnowledgePointRelation rel(Long id, Long src, Long tgt, String type) {
        KnowledgePointRelation r = new KnowledgePointRelation();
        r.setId(id); r.setSourceId(src); r.setTargetId(tgt); r.setRelationType(type);
        return r;
    }

    // ===================== recommendFromWeakPoints =====================

    @Test
    @DisplayName("recommendFromWeakPoints 入参为空返回空")
    void weak_empty() {
        assertTrue(service.recommendFromWeakPoints(1L, null, 10).isEmpty());
        assertTrue(service.recommendFromWeakPoints(1L, new HashMap<>(), 10).isEmpty());
    }

    @Test
    @DisplayName("recommendFromWeakPoints 课程无知识点返回空")
    void weak_noPoints() {
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());
        assertTrue(service.recommendFromWeakPoints(1L, Map.of("函数", 30.0), 10).isEmpty());
    }

    @Test
    @DisplayName("recommendFromWeakPoints 推荐前置与关联")
    void weak_withRecommendations() {
        KnowledgePoint pA = kp(1L, "函数");
        KnowledgePoint pB = kp(2L, "代数");
        KnowledgePoint pC = kp(3L, "导数");
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(pA, pB, pC));
        // 代数(id2) 是 函数(id1) 的前置；函数(id1) 关联 导数(id3)
        when(relationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                rel(10L, 2L, 1L, "PREREQUISITE"),
                rel(11L, 1L, 3L, "INCLUDES")));

        List<Map<String, Object>> r = service.recommendFromWeakPoints(1L, Map.of("函数", 30.0), 10);
        assertEquals(2, r.size());
        // 按权重降序：前置(0.84) 先于 关联(0.7)
        assertEquals("代数", r.get(0).get("name"));
        assertEquals("前置复习", r.get(0).get("recommendType"));
        assertEquals("导数", r.get(1).get("name"));
        assertEquals("关联补充", r.get(1).get("recommendType"));
    }

    @Test
    @DisplayName("recommendFromWeakPoints 受 limit 截断")
    void weak_limit() {
        KnowledgePoint pA = kp(1L, "函数");
        KnowledgePoint pB = kp(2L, "代数");
        KnowledgePoint pC = kp(3L, "导数");
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(pA, pB, pC));
        when(relationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                rel(10L, 2L, 1L, "PREREQUISITE"), rel(11L, 1L, 3L, "INCLUDES")));
        List<Map<String, Object>> r = service.recommendFromWeakPoints(1L, Map.of("函数", 30.0), 1);
        assertEquals(1, r.size());
        assertEquals("代数", r.get(0).get("name"));
    }

    @Test
    @DisplayName("recommendFromWeakPoints 未知知识点名被跳过")
    void weak_unknownName() {
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(kp(1L, "函数")));
        assertTrue(service.recommendFromWeakPoints(1L, Map.of("不存在", 50.0), 10).isEmpty());
    }

    // ===================== recommendNextSteps =====================

    @Test
    @DisplayName("recommendNextSteps 充分掌握后推荐后继")
    void next_ok() {
        KnowledgePoint pA = kp(1L, "函数");
        KnowledgePoint pD = kp(4L, "积分");
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(pA, pD));
        // 函数(id1) 是 积分(id4) 的前置 -> 后继索引
        when(relationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(rel(20L, 1L, 4L, "PREREQUISITE")));

        List<Map<String, Object>> r = service.recommendNextSteps(1L, Map.of("函数", 80.0), 10);
        assertEquals(1, r.size());
        assertEquals("积分", r.get(0).get("name"));
        assertEquals("下一步学习", r.get(0).get("recommendType"));
    }

    @Test
    @DisplayName("recommendNextSteps 掌握度不足不推荐")
    void next_lowMastery() {
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(kp(1L, "函数")));
        assertTrue(service.recommendNextSteps(1L, Map.of("函数", 50.0), 10).isEmpty());
    }

    @Test
    @DisplayName("recommendNextSteps 已掌握的后继被跳过")
    void next_alreadyMastered() {
        KnowledgePoint pA = kp(1L, "函数");
        KnowledgePoint pD = kp(4L, "积分");
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(pA, pD));
        when(relationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(rel(20L, 1L, 4L, "PREREQUISITE")));
        // 积分 已在 masteredPoints 中 -> 应跳过
        assertTrue(service.recommendNextSteps(1L, Map.of("函数", 80.0, "积分", 80.0), 10).isEmpty());
    }

    @Test
    @DisplayName("recommendNextSteps 入参为空返回空")
    void next_empty() {
        assertTrue(service.recommendNextSteps(1L, null, 10).isEmpty());
    }

    // ===================== recommendComprehensive =====================

    @Test
    @DisplayName("recommendComprehensive 合并薄弱与前继推荐并去重")
    void comp_ok() {
        KnowledgePoint pA = kp(1L, "函数");
        KnowledgePoint pB = kp(2L, "代数");
        KnowledgePoint pD = kp(4L, "积分");
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(pA, pB, pD));
        when(relationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                rel(10L, 2L, 1L, "PREREQUISITE"),  // 代数 前置 函数
                rel(20L, 1L, 4L, "PREREQUISITE"))); // 函数 前置 积分
        // 薄弱：函数 -> 推荐前置 代数；已掌握：函数(80) -> 后继 积分(未掌握) 应被推荐
        List<Map<String, Object>> r = service.recommendComprehensive(1L, Map.of("函数", 30.0), Map.of("函数", 80.0), 10);
        // 期望：前置复习(代数) + 下一步学习(积分)
        assertEquals(2, r.size());
        Set<String> names = new HashSet<>();
        r.forEach(m -> names.add((String) m.get("name")));
        assertTrue(names.contains("代数"));
        assertTrue(names.contains("积分"));
    }

    @Test
    @DisplayName("recommendComprehensive 双空返回空")
    void comp_empty() {
        assertTrue(service.recommendComprehensive(1L, null, null, 10).isEmpty());
        assertTrue(service.recommendComprehensive(1L, new HashMap<>(), new HashMap<>(), 10).isEmpty());
    }
}
