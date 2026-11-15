package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.mapper.KnowledgePointMapper;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.WrongQuestionService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link KnowledgePointServiceImpl#extractAndSaveGraph} 逐分支测试（2026-11-14 P2-3）。
 *
 * <p>原 8 个场景随逻辑从 KnowledgeController 内联体下沉而来（控制器只保留 AI 调用与
 * 委托），本类在服务层以真实实现逐分支断言：解析、幂等去重、类型白名单、关系名解析、
 * 逐条容错、剥壳入口。任何行为变化先改本类再改实现。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AI 抽取落库（extractAndSaveGraph）：解析/幂等/容错")
class KnowledgePointServiceImplExtractGraphTest {

    @Mock private KnowledgePointMapper kpMapper;
    @Mock private KnowledgePointRelationService relationService;
    @Mock private QuestionService questionService;
    @Mock private WrongQuestionService wrongQuestionService;

    private KnowledgePointServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), KnowledgePoint.class);
        service = new KnowledgePointServiceImpl(relationService, questionService, wrongQuestionService);
        ReflectionTestUtils.setField(service, "baseMapper", kpMapper);
    }

    private static KnowledgePoint point(Long id, String name) {
        KnowledgePoint p = new KnowledgePoint();
        p.setId(id);
        p.setName(name);
        p.setCourseId(1L);
        return p;
    }

    private static KnowledgePointRelation relation(Long id, Long sourceId, Long targetId) {
        KnowledgePointRelation r = new KnowledgePointRelation();
        r.setId(id);
        r.setSourceId(sourceId);
        r.setTargetId(targetId);
        r.setRelationType("PREREQUISITE");
        return r;
    }

    private void stubSelectReturning(List<KnowledgePoint> existing) {
        when(kpMapper.selectList(any())).thenReturn(existing);
    }

    private void stubSaveAssigningIds(long firstId) {
        // 与原控制器时代 stub 同款预自增语义：holder 起于 firstId，首个落库点 ID=firstId+1
        // （firstId=900 → 栈=901、新点=902、队列=903，与「已存在知识点=500」不冲突）
        AtomicLong holder = new AtomicLong(firstId);
        when(kpMapper.insert(any(KnowledgePoint.class))).thenAnswer(inv -> {
            KnowledgePoint p = inv.getArgument(0);
            p.setId(holder.incrementAndGet());
            return 1;
        });
    }

    @SuppressWarnings("unchecked")
    private List<KnowledgePoint> savedPoints() {
        org.mockito.ArgumentCaptor<KnowledgePoint> cap = org.mockito.ArgumentCaptor.forClass(KnowledgePoint.class);
        verify(kpMapper, atLeast(0)).insert(cap.capture());
        return cap.getAllValues();
    }

    @Test
    @DisplayName("完整结构：跳过空白名/无名/已存在，未知题型回落 CONCEPT，关系按名称解析成边")
    void fullStructure_createsNodesAndEdges() {
        stubSelectReturning(List.of(point(500L, "已存在知识点"), point(501L, null)));
        stubSaveAssigningIds(900L);
        when(relationService.createRelation(any(), any(), any(), isNull(), isNull()))
                .thenAnswer(inv -> relation(700L, inv.getArgument(0), inv.getArgument(1)));

        String aiJson = "{\"nodes\":["
                + "{\"name\":\" 栈 \",\"content\":\"LIFO 结构\",\"type\":\"principle\"},"
                + "{\"name\":\"   \",\"content\":\"空白名应跳过\"},"
                + "{\"content\":\"没有 name 应跳过\"},"
                + "{\"name\":\"已存在知识点\"},"
                + "{\"name\":\"新点\",\"type\":\"weird-type\"},"
                + "{\"name\":\"队列\"}"
                + "],\"relations\":["
                + "{\"source\":\"栈\",\"target\":\"队列\",\"relationType\":\"prerequisite\"},"
                + "{\"target\":\"队列\"},"
                + "{\"source\":\"栈\",\"target\":\"栈\"},"
                + "{\"source\":\"查无此点\",\"target\":\"队列\"},"
                + "{\"source\":\"队列\"}"
                + "]}";

        Map<String, Object> result = service.extractAndSaveGraph(1L, aiJson);

        assertEquals(3, result.get("nodeCount"), "空白名/无名/已存在三支跳过，应建 3 点");
        List<KnowledgePoint> saved = savedPoints();
        assertEquals(3, saved.size());
        // 原控制器时代 KnowledgeTypeAssertions 等价搬入：名称 trim、类型大小写归一、未知类型回落
        assertEquals("栈", saved.get(0).getName(), "名称应 trim");
        assertEquals("PRINCIPLE", saved.get(0).getPointType(), "principle 应大写归一");
        assertEquals("CONCEPT", saved.get(1).getPointType(), "未知题型 weird-type 应回落 CONCEPT");
        assertEquals("CONCEPT", saved.get(2).getPointType(), "缺省类型回落 CONCEPT");
        assertEquals(1, result.get("relationCount"), "5 条候选只成 1 条（缺 source/自环/查无/缺 target 跳过）");
        verify(relationService).createRelation(901L, 903L, "PREREQUISITE", null, null);
        verify(relationService, times(1)).createRelation(any(), any(), any(), isNull(), isNull());
        assertFalse(result.containsKey("parseError"));
    }

    @Test
    @DisplayName("大模型返回无 JSON 结构的纯文本：parseError=「AI 未返回有效结构」，不落任何点")
    void noJsonObject_reportsParseError() {
        stubSelectReturning(List.of());
        Map<String, Object> result = service.extractAndSaveGraph(1L, "模型说它无法抽取");
        assertEquals("AI 未返回有效结构", result.get("parseError"));
        assertEquals(0, result.get("nodeCount"));
        verify(kpMapper, never()).insert(any(KnowledgePoint.class));
    }

    @Test
    @DisplayName("有花括号但 JSON 语法坏：整体解析失败降级为 parseError，不抛 500")
    void malformedJson_reportsParseFailure() {
        stubSelectReturning(List.of());
        Map<String, Object> result = service.extractAndSaveGraph(1L, "{nodes: [坏掉的 JSON}");
        assertEquals("AI 返回结构解析失败，请稍后重试", result.get("parseError"));
        assertEquals(0, result.get("nodeCount"));
    }

    @Test
    @DisplayName("nodes 是对象而非数组：不进入节点循环，也不误建知识点")
    void nodesNotArray_createsNothing() {
        stubSelectReturning(List.of());
        Map<String, Object> result = service.extractAndSaveGraph(1L, "{\"nodes\":{\"a\":1},\"relations\":[]}");
        assertEquals(0, result.get("nodeCount"));
        assertEquals(0, result.get("relationCount"));
        verify(kpMapper, never()).insert(any(KnowledgePoint.class));
    }

    @Test
    @DisplayName("relations 是对象而非数组：跳过连边但节点照常写入")
    void relationsNotArray_stillCreatesNodes() {
        stubSelectReturning(List.of());
        stubSaveAssigningIds(950L);
        Map<String, Object> result = service.extractAndSaveGraph(1L, "{\"nodes\":[{\"name\":\"甲\"}],\"relations\":{\"x\":1}}");
        assertEquals(1, result.get("nodeCount"));
        assertEquals(0, result.get("relationCount"));
        verify(relationService, never()).createRelation(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("单条关系建边失败：内部吞掉，其余节点与响应不受影响")
    void singleRelationFailure_swallowedAndContinues() {
        stubSelectReturning(List.of());
        stubSaveAssigningIds(980L);
        when(relationService.createRelation(any(), any(), any(), isNull(), isNull()))
                .thenThrow(new IllegalStateException("关系类型不合法"));
        Map<String, Object> result = service.extractAndSaveGraph(1L,
                "{\"nodes\":[{\"name\":\"甲\"},{\"name\":\"乙\"}],"
                        + "\"relations\":[{\"source\":\"甲\",\"target\":\"乙\"}]}");
        assertEquals(2, result.get("nodeCount"));
        assertEquals(0, result.get("relationCount"));
        assertFalse(result.containsKey("parseError"));
    }

    @Test
    @DisplayName("大模型返回空对象：既无 nodes 也无 relations，计数全 0")
    void emptyObject_returnsZeroCounts() {
        stubSelectReturning(List.of());
        Map<String, Object> result = service.extractAndSaveGraph(1L, "{}");
        assertEquals(0, result.get("nodeCount"));
        assertEquals(0, result.get("relationCount"));
        assertFalse(result.containsKey("parseError"));
    }

    @Test
    @DisplayName("```json 代码块包裹：extractJsonObject 先剥壳再解析")
    void codeBlockWrappedJson_parsed() {
        stubSelectReturning(List.of());
        stubSaveAssigningIds(990L);
        Map<String, Object> result = service.extractAndSaveGraph(1L,
                "这是模型的废话前缀\n```json\n{\"nodes\":[{\"name\":\"归一化\"}]}\n```");
        assertEquals(1, result.get("nodeCount"));
        List<KnowledgePoint> saved = savedPoints();
        assertEquals("归一化", saved.get(0).getName());
    }
}
