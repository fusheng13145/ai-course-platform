package com.aicourse.service.component.ai;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.exception.BusinessException;
import com.aicourse.exception.LLMUnavailableException;
import com.aicourse.integration.rag.RagContract.RagSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AiStructuredComponent 覆盖率补测（I-04 第七批）。
 *
 * <p>覆盖薄弱点/题目解析的数组、根对象、非法 JSON、空值与字段缺省分支；
 * 学情推荐上下文注入；出题证据接地；学习建议 503 与业务异常路径。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiStructuredComponentCoverageTest {

    @Mock private AiCoreComponent core;
    @Mock private CourseEvidenceComponent evidence;

    private AiStructuredComponent component;

    @BeforeEach
    void setUp() {
        component = new AiStructuredComponent(core, evidence);
        when(core.buildMessages(anyString(), isNull())).thenReturn(List.of());
        when(core.callLlm(any(), any(), any(), anyString())).thenReturn("{}");
    }

    @Test
    @DisplayName("parseWeakPoints：数组、weakPoints 字段、根对象、文本节点、严重度归一")
    void weakPointsShapes() {
        assertTrue(component.parseWeakPoints(null).isEmpty());

        List<Map<String, String>> array = component.parseWeakPoints(
                "[{\"description\":\"d1\",\"severity\":\"high\"},{\"description\":\"d2\",\"severity\":\"BAD\"}]");
        assertEquals("d1", array.get(0).get("description"));
        assertEquals("HIGH", array.get(0).get("severity"));
        assertEquals("MEDIUM", array.get(1).get("severity"));

        List<Map<String, String>> wrapped = component.parseWeakPoints(
                "{\"weakPoints\":[\"纯文本\"]}");
        assertEquals("纯文本", wrapped.get(0).get("description"));
        assertEquals("MEDIUM", wrapped.get(0).get("severity"));

        List<Map<String, String>> object = component.parseWeakPoints("{\"description\":\"根描述\"}");
        assertEquals("根描述", object.get(0).get("description"));
        assertEquals("MEDIUM", object.get(0).get("severity"));

        List<Map<String, String>> noDesc = component.parseWeakPoints("{\"foo\":1}");
        assertEquals("{\"foo\":1}", noDesc.get(0).get("description"));

        List<Map<String, String>> empty = component.parseWeakPoints("[]");
        assertEquals("AI 暂未返回具体分析，请稍后重试", empty.get(0).get("description"));
        assertEquals("LOW", empty.get(0).get("severity"));
    }

    @Test
    @DisplayName("parseWeakPoints：非法 JSON 回退原文，空白回退通用提示")
    void weakPointsFallback() {
        List<Map<String, String>> invalid = component.parseWeakPoints("not-json");
        assertEquals("not-json", invalid.get(0).get("description"));
        assertEquals("MEDIUM", invalid.get(0).get("severity"));

        List<Map<String, String>> blank = component.parseWeakPoints("   ");
        assertEquals("AI 暂未返回具体分析，请稍后重试", blank.get(0).get("description"));
    }

    @Test
    @DisplayName("parseQuestions：数组、questions 字段、根对象、字段缺省与 knowledgePoint 透传")
    void questionShapes() {
        assertTrue(component.parseQuestions(null, "EASY").isEmpty());

        List<Map<String, Object>> array = component.parseQuestions(
                "[{\"content\":\"c1\",\"type\":\"MULTI\",\"difficulty\":\"HARD\",\"knowledgePoint\":\"KP\"},"
                        + "{\"content\":\"c2\",\"knowledgePoint\":\"  \"}]", "EASY");
        assertEquals("MULTI", array.get(0).get("type"));
        assertEquals("HARD", array.get(0).get("difficulty"));
        assertEquals("KP", array.get(0).get("knowledgePoint"));
        assertEquals("SINGLE_CHOICE", array.get(1).get("type"));
        assertEquals("EASY", array.get(1).get("difficulty"));
        assertFalse(array.get(1).containsKey("knowledgePoint"));

        List<Map<String, Object>> wrapped = component.parseQuestions(
                "{\"questions\":[\"文本题\"]}", null);
        assertEquals("文本题", wrapped.get(0).get("content"));
        assertEquals("MEDIUM", wrapped.get(0).get("difficulty"));

        List<Map<String, Object>> object = component.parseQuestions(
                "{\"content\":\"根题\",\"knowledgePoint\":\"KP2\"}", "EASY");
        assertEquals("根题", object.get(0).get("content"));
        assertEquals("KP2", object.get(0).get("knowledgePoint"));

        assertTrue(component.parseQuestions("{}", "EASY").isEmpty());
    }

    @Test
    @DisplayName("parseQuestions：非法 JSON 回退单条原文")
    void questionFallback() {
        List<Map<String, Object>> list = component.parseQuestions("not-json", null);
        assertEquals(1, list.size());
        assertEquals("not-json", list.get(0).get("content"));
        assertEquals("SINGLE_CHOICE", list.get(0).get("type"));
        assertEquals("MEDIUM", list.get(0).get("difficulty"));
    }

    @Test
    @DisplayName("recommendLearningWithContext：课程/进度/薄弱项排序截断/成绩与空薄弱项")
    void recommendContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("courseList", "计算机网络");
        context.put("progress", 60);
        List<Map<String, Object>> weak = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", "kp" + i);
            m.put("mastery", i);
            weak.add(m);
        }
        weak.add(Map.of("name", "noMastery"));
        context.put("weakPoints", weak);
        context.put("recentScores", List.of(90, 80));

        assertNotNull(component.recommendLearningWithContext(context));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(core).buildMessages(prompt.capture(), isNull());
        assertTrue(prompt.getValue().contains("kp0"));
        assertTrue(prompt.getValue().contains("暂无薄弱项") == false);

        Map<String, Object> empty = new LinkedHashMap<>();
        assertNotNull(component.recommendLearningWithContext(empty));
        ArgumentCaptor<String> emptyPrompt = ArgumentCaptor.forClass(String.class);
        verify(core, times(2)).buildMessages(emptyPrompt.capture(), isNull());
        assertTrue(emptyPrompt.getAllValues().get(1).contains("暂无薄弱项数据"));
    }

    @Test
    @DisplayName("recommendQuestionsJson：课程/难度缺省、无证据不接地、有证据追加约束段")
    void recommendQuestions() {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setName("TCP");
        when(evidence.loadPoints(any())).thenReturn(List.of(kp));
        when(evidence.retrieve(anyString(), any(), anyInt())).thenReturn(List.of());
        when(evidence.matchPoints(any(), anyString())).thenReturn(List.of());
        when(evidence.renderSources(any())).thenReturn("sources");
        when(evidence.renderPoints(any())).thenReturn("points");

        assertNotNull(component.recommendQuestionsJson(null, null, 3));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(core).buildMessages(prompt.capture(), isNull());
        assertTrue(prompt.getValue().contains("请生成3道MEDIUM难度"));

        when(evidence.retrieve(anyString(), any(), anyInt())).thenReturn(List.of(mock(RagSource.class)));
        when(evidence.matchPoints(any(), anyString())).thenReturn(List.of(kp));
        assertNotNull(component.recommendQuestionsJson(1L, "HARD", 2));
        ArgumentCaptor<String> grounded = ArgumentCaptor.forClass(String.class);
        verify(core, times(2)).buildMessages(grounded.capture(), isNull());
        assertTrue(grounded.getAllValues().get(1).contains("课程资料摘录"));
        assertTrue(grounded.getAllValues().get(1).contains("课程知识点（知识图谱）"));
        assertTrue(grounded.getAllValues().get(1).contains("不得超纲"));
    }

    @Test
    @DisplayName("extractKnowledgeGraphJson：阶段一空响应降级、异常降级、成功走两阶段")
    void extractGraph() {
        doReturn(null).when(core).completeAndSanitize(any(), anyDouble(), anyString());
        assertNotNull(component.extractKnowledgeGraphJson("content"));

        doThrow(new RuntimeException("stage1")).when(core).completeAndSanitize(any(), anyDouble(), anyString());
        assertNotNull(component.extractKnowledgeGraphJson("content"));

        doReturn("reading").when(core).completeAndSanitize(any(), anyDouble(), anyString());
        assertNotNull(component.extractKnowledgeGraphJson("content"));
        verify(core, times(3)).completeAndSanitize(any(), anyDouble(), anyString());
    }

    @Test
    @DisplayName("analyzeWeakPoints：成功解析、LLM 503 透传、其他异常回退低风险提示")
    void analyzeWeakPoints() {
        doReturn("{\"weakPoints\":[{\"description\":\"d\",\"severity\":\"HIGH\"}]}")
                .when(core).callLlm(any(), any(), any(), eq("analyze"));
        assertEquals("HIGH", component.analyzeWeakPoints(Map.of()).get(0).get("severity"));

        doThrow(new LLMUnavailableException(LLMUnavailableException.Kind.EMPTY_RESPONSE, "down"))
                .when(core).callLlm(any(), any(), any(), eq("analyze"));
        assertThrows(LLMUnavailableException.class, () -> component.analyzeWeakPoints(Map.of()));

        doThrow(new RuntimeException("boom")).when(core).callLlm(any(), any(), any(), eq("analyze"));
        List<Map<String, String>> fallback = component.analyzeWeakPoints(Map.of());
        assertEquals("LOW", fallback.get(0).get("severity"));
    }

    @Test
    @DisplayName("generateLearningAdvice：正常解析、无 items、无 JSON、解析坏、LLM 503、其他异常")
    void learningAdvice() {
        doReturn("{\"weakPoints\":[]}").when(core).callLlm(any(), any(), any(), eq("analyze"));
        doReturn("{\"summary\":\"s\",\"items\":[{\"title\":\"t\",\"detail\":\"d\",\"level\":\"high\"},"
                + "{\"title\":\"t2\",\"level\":\"BAD\"}]}")
                .when(core).callLlm(any(), any(), any(), eq("recommend"));
        Map<String, Object> result = component.generateLearningAdvice(Map.of());
        assertEquals("s", result.get("summary"));
        assertEquals(2, ((List<?>) result.get("items")).size());

        doReturn("{\"summary\":\"s\"}").when(core).callLlm(any(), any(), any(), eq("recommend"));
        assertEquals(List.of(), component.generateLearningAdvice(Map.of()).get("items"));

        doReturn("no-json").when(core).callLlm(any(), any(), any(), eq("recommend"));
        assertThrows(LLMUnavailableException.class, () -> component.generateLearningAdvice(Map.of()));

        doReturn("{\"bad\"").when(core).callLlm(any(), any(), any(), eq("recommend"));
        assertThrows(LLMUnavailableException.class, () -> component.generateLearningAdvice(Map.of()));

        doThrow(new LLMUnavailableException(LLMUnavailableException.Kind.EMPTY_RESPONSE, "down"))
                .when(core).callLlm(any(), any(), any(), eq("analyze"));
        assertThrows(LLMUnavailableException.class, () -> component.generateLearningAdvice(Map.of()));

        doThrow(new RuntimeException("boom")).when(core).callLlm(any(), any(), any(), eq("analyze"));
        assertThrows(BusinessException.class, () -> component.generateLearningAdvice(Map.of()));
    }
}
