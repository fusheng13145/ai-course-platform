package com.aicourse.service.component.ai;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.integration.rag.RagContract.RagRetrievalResult;
import com.aicourse.integration.rag.RagContract.RagSource;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.RAGService;
import com.aicourse.vo.QaSourceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link CourseEvidenceComponent} 单元测试（纯 Mockito，无 Spring 上下文）。
 * <p>覆盖证据召回语义（只有 mode=ENGINE 才算证据）、知识点匹配口径（名称≥2 字、归一化、
 * 重要度排序限量）、prompt 渲染（描述截断、前置知识限量与异常降级）以及出处/证据名构造。</p>
 */
@ExtendWith(MockitoExtension.class)
class CourseEvidenceComponentTest {

    @Mock
    private RAGService ragService;
    @Mock
    private KnowledgePointService knowledgePointService;

    private CourseEvidenceComponent component;

    @BeforeEach
    void setUp() {
        component = new CourseEvidenceComponent(ragService, knowledgePointService);
    }

    private static KnowledgePoint point(Long id, String name, String description, Integer importance) {
        KnowledgePoint point = new KnowledgePoint();
        point.setId(id);
        point.setName(name);
        point.setDescription(description);
        point.setImportance(importance);
        return point;
    }

    // ===================== 引擎检索 =====================

    @Test
    @DisplayName("retrieve：mode=ENGINE 且有召回 -> 原样返回召回列表")
    void retrieve_engineReadyReturnsResults() {
        RagSource source = new RagSource(9L, "Python-函数与lambda", 0.93, "lambda 是表达式，def 是语句");
        when(ragService.retrieveTyped("什么是闭包", 1L, 5))
                .thenReturn(new RagRetrievalResult(List.of(source), "ENGINE", null, null));

        assertEquals(List.of(source), component.retrieve("什么是闭包", 1L, 5));
    }

    @Test
    @DisplayName("retrieve：UNAVAILABLE / PLACEHOLDER / ENGINE+503 / 结果 null / 召回 null -> 空列表")
    void retrieve_nonEngineReturnsEmpty() {
        RagSource source = new RagSource(1L, "TCP", 0.9, "占位片段");
        when(ragService.retrieveTyped("q", 1L, 5))
                .thenReturn(new RagRetrievalResult(List.of(source), "UNAVAILABLE", "引擎不可用", 503))
                .thenReturn(new RagRetrievalResult(List.of(source), "PLACEHOLDER", null, null))
                .thenReturn(new RagRetrievalResult(List.of(source), "ENGINE", "降级", 503))
                .thenReturn(null)
                .thenReturn(new RagRetrievalResult(null, "ENGINE", null, null));

        assertTrue(component.retrieve("q", 1L, 5).isEmpty(), "UNAVAILABLE 不得作为证据");
        assertTrue(component.retrieve("q", 1L, 5).isEmpty(), "PLACEHOLDER 不得作为证据");
        assertTrue(component.retrieve("q", 1L, 5).isEmpty(), "code=503 的 ENGINE 应答属于降级，不得作为证据");
        assertTrue(component.retrieve("q", 1L, 5).isEmpty(), "检索结果为 null 应降级");
        assertTrue(component.retrieve("q", 1L, 5).isEmpty(), "召回列表为 null 应降级");
    }

    @Test
    @DisplayName("retrieve：检索抛异常 -> 异常被吞并返回空列表")
    void retrieve_exceptionSwallowed() {
        when(ragService.retrieveTyped("q", 1L, 5)).thenThrow(new RuntimeException("boom"));

        assertTrue(component.retrieve("q", 1L, 5).isEmpty());
    }

    @Test
    @DisplayName("retrieve：未指定课程或问题为空 -> 空列表且不发起检索")
    void retrieve_guards() {
        assertTrue(component.retrieve("q", null, 5).isEmpty());
        assertTrue(component.retrieve(null, 1L, 5).isEmpty());
        assertTrue(component.retrieve("   ", 1L, 5).isEmpty());
        verifyNoInteractions(ragService);
    }

    // ===================== 知识点读取 =====================

    @Test
    @DisplayName("loadPoints：课程ID为空返回空；正常读取返回原列表；读取结果为 null 返回空")
    void loadPoints_guardsAndNullable() {
        KnowledgePoint closure = point(1L, "闭包", "由函数及其环境组成", 5);
        when(knowledgePointService.listByCourse(3L)).thenReturn(List.of(closure));
        when(knowledgePointService.listByCourse(4L)).thenReturn(null);

        assertTrue(component.loadPoints(null).isEmpty());
        verify(knowledgePointService, never()).listByCourse(null);
        assertEquals(List.of(closure), component.loadPoints(3L));
        assertTrue(component.loadPoints(4L).isEmpty());
    }

    @Test
    @DisplayName("loadPoints：读取异常被吞并返回空列表")
    void loadPoints_exceptionSwallowed() {
        when(knowledgePointService.listByCourse(2L)).thenThrow(new RuntimeException("db down"));

        assertTrue(component.loadPoints(2L).isEmpty());
    }

    // ===================== 知识点匹配 =====================

    @Test
    @DisplayName("matchPoints：名称仅 1 字或空名称不命中")
    void matchPoints_requiresAtLeastTwoCharsAndName() {
        List<KnowledgePoint> points = List.of(
                point(1L, "函", "单字名称", 5),
                point(2L, "", "空名称", 4),
                point(3L, null, "null 名称", 3),
                point(4L, "闭包", "有效名称", 2));

        assertEquals(List.of(points.get(3)), component.matchPoints(points, "什么是闭包"));
    }

    @Test
    @DisplayName("matchPoints：忽略大小写与空白后命中")
    void matchPoints_normalizesCaseAndWhitespace() {
        List<KnowledgePoint> points = List.of(point(1L, "TCP 协议", "传输层协议", 5));

        assertEquals(1, component.matchPoints(points, "什么是 tcp协议？").size());
    }

    @Test
    @DisplayName("matchPoints：命中超过 5 条时按 importance 降序截断")
    void matchPoints_limitsByImportance() {
        List<KnowledgePoint> points = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            points.add(point((long) i, "考点" + i, "描述" + i, i));
        }
        String question = points.stream().map(KnowledgePoint::getName).collect(Collectors.joining("、"));

        List<KnowledgePoint> matched = component.matchPoints(points, question);

        assertEquals(5, matched.size(), "命中数量上限为 5");
        assertEquals(List.of("考点7", "考点6", "考点5", "考点4", "考点3"),
                matched.stream().map(KnowledgePoint::getName).toList());
    }

    // ===================== prompt 渲染 =====================

    @Test
    @DisplayName("renderPoints：超过 200 字的描述截断并追加省略号")
    void renderPoints_truncatesDescription() {
        String rendered = component.renderPoints(List.of(point(1L, "闭包", "x".repeat(250), 5)));

        assertTrue(rendered.contains("x".repeat(200) + "…"), "应截断为 200 字 + 省略号");
        assertFalse(rendered.contains("x".repeat(201)), "截断后不得残留第 201 个字");
    }

    @Test
    @DisplayName("renderPoints：前置知识最多渲染 3 条")
    void renderPoints_limitsPrerequisites() {
        when(knowledgePointService.getPrerequisites(1L)).thenReturn(List.of(
                point(11L, "作用域", "", 4),
                point(12L, "变量", "", 3),
                point(13L, "内存", "", 2),
                point(14L, "编译", "", 1)));

        String rendered = component.renderPoints(List.of(point(1L, "闭包", "由函数及其环境组成", 5)));

        assertTrue(rendered.contains("前置知识：作用域、变量、内存"), "实际=" + rendered);
        assertFalse(rendered.contains("编译"), "第 4 条前置知识应被截断，实际=" + rendered);
    }

    @Test
    @DisplayName("renderPoints：前置查询抛异常时仍渲染知识点本体且不抛出")
    void renderPoints_prerequisiteFailureSwallowed() {
        when(knowledgePointService.getPrerequisites(1L)).thenThrow(new RuntimeException("kg down"));

        String rendered = component.renderPoints(List.of(point(1L, "闭包", "由函数及其环境组成", 5)));

        assertTrue(rendered.contains("- 闭包：由函数及其环境组成"), "实际=" + rendered);
        assertFalse(rendered.contains("前置知识"), "前置关系不可用时不得渲染该段");
    }

    @Test
    @DisplayName("renderSources：[序号] 文档名：片段，缺名兜底为“课程资料”，缺片段为空")
    void renderSources_formatsAndFallsBack() {
        String rendered = component.renderSources(List.of(
                new RagSource(1L, "第1章 绪论", 0.9, "算法的定义"),
                new RagSource(2L, null, 0.8, null)));

        assertEquals("[1] 第1章 绪论：算法的定义\n[2] 课程资料：\n", rendered);
    }

    @Test
    @DisplayName("scopeText：按重要度取前 6 个名称、空列表返回空串")
    void scopeText_limitsAndJoins() {
        List<KnowledgePoint> points = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            points.add(point((long) i, "点" + i, "描述" + i, i));
        }

        assertEquals("点8、点7、点6、点5、点4、点3", component.scopeText(points));
        assertEquals("", component.scopeText(List.of()));
    }

    @Test
    @DisplayName("scopeText：同名知识点去重")
    void scopeText_dedupesNames() {
        assertEquals("闭包、递归", component.scopeText(List.of(
                point(1L, "闭包", "", 5),
                point(2L, "闭包", "", 4),
                point(3L, "递归", "", 3))));
    }

    // ===================== 出处与证据名 =====================

    @Test
    @DisplayName("pointSources：文档名带图谱前缀、分数 0.9、空描述回填知识点名")
    void pointSources_shape() {
        List<QaSourceVO> sources = component.pointSources(List.of(
                point(7L, "闭包", "由函数及其环境组成", 5),
                point(8L, "递归", "  ", 4)));

        assertEquals(2, sources.size());
        assertEquals(7L, sources.get(0).getDocumentId());
        assertEquals("课程知识图谱 · 闭包", sources.get(0).getDocumentName());
        assertEquals(0.9, sources.get(0).getRelevanceScore());
        assertEquals("由函数及其环境组成", sources.get(0).getSnippet());
        assertEquals("课程知识点：递归", sources.get(1).getSnippet());
    }

    @Test
    @DisplayName("evidenceNames：文档名在前、知识点名在后，去重保序")
    void evidenceNames_dedupesPreservingOrder() {
        List<String> names = component.evidenceNames(
                List.of(new RagSource(1L, "资料一", 0.9, "片段"),
                        new RagSource(2L, null, 0.8, "片段")),
                List.of(point(1L, "闭包", "", 5),
                        point(2L, "资料一", "", 4),
                        point(3L, "闭包", "", 3)));

        assertEquals(List.of("资料一", "课程资料", "闭包"), names);
        assertTrue(component.evidenceNames(null, null).isEmpty());
    }
}
