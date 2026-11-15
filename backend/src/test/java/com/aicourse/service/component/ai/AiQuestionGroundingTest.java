package com.aicourse.service.component.ai;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.integration.rag.RagContract.RagRetrievalResult;
import com.aicourse.integration.rag.RagContract.RagSource;
import com.aicourse.llm.LLMProvider;
import com.aicourse.service.AiCallLogService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.RAGService;
import com.aicourse.service.impl.PromptGuard;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 出题接地测试：验证真实 AI 出题把「课程资料召回 + 知识图谱知识点」拼进提示词，
 * 并验证 {@code knowledgePoint} 字段在结构化推荐 schema 与解析结果中的透传。
 * <p>打桩原则：发往 LLM 的 messages/schema 是被测逻辑的产物，一律捕获后断言内容，
 * 不用 any() 放过 prompt 是否真的带了证据。</p>
 */
@ExtendWith(MockitoExtension.class)
class AiQuestionGroundingTest {

    @Mock
    private LLMProvider llmProvider;
    @Mock
    private PromptGuard promptGuard;
    @Mock
    private AiCallLogService aiCallLogService;
    @Mock
    private RAGService ragService;
    @Mock
    private KnowledgePointService knowledgePointService;

    private AiDialogueComponent dialogue;
    private AiStructuredComponent structured;

    @BeforeEach
    void setUp() {
        AiCoreComponent core =
                new AiCoreComponent(llmProvider, promptGuard, new SimpleMeterRegistry(), aiCallLogService);
        CourseEvidenceComponent evidence = new CourseEvidenceComponent(ragService, knowledgePointService);
        dialogue = new AiDialogueComponent(core, ragService, evidence);
        structured = new AiStructuredComponent(core, evidence);
        lenient().when(llmProvider.isConfigured()).thenReturn(true);
        lenient().when(promptGuard.neutralizeInjection(anyString())).thenAnswer(i -> i.getArgument(0));
        lenient().when(promptGuard.sanitizeOutput(anyString())).thenAnswer(i -> i.getArgument(0));
    }

    private static KnowledgePoint point(Long id, String name, String description, int importance) {
        KnowledgePoint point = new KnowledgePoint();
        point.setId(id);
        point.setName(name);
        point.setDescription(description);
        point.setImportance(importance);
        return point;
    }

    private static RagRetrievalResult engineResult(RagSource... sources) {
        return new RagRetrievalResult(List.of(sources), "ENGINE", null, null);
    }

    private static String promptOf(List<Map<String, String>> messages) {
        return messages.stream()
                .filter(m -> "user".equals(m.get("role")))
                .map(m -> String.valueOf(m.get("content")))
                .collect(Collectors.joining("\n"));
    }

    @SuppressWarnings("unchecked")
    private String captureQuestionPrompt() {
        ArgumentCaptor<List<Map<String, String>>> messages = ArgumentCaptor.forClass(List.class);
        verify(llmProvider).complete(messages.capture(), org.mockito.ArgumentMatchers.eq(0.9));
        return promptOf(messages.getValue());
    }

    @Test
    @DisplayName("generateQuestion：召回资料与知识图谱知识点同时进入 prompt，且题目字段约束不变")
    void generateQuestion_injectsCourseEvidence() {
        when(knowledgePointService.listByCourse(11L))
                .thenReturn(List.of(point(3L, "闭包", "闭包是函数及其引用环境组合而成的实体", 5)));
        when(ragService.retrieveTyped("闭包", 11L, 5))
                .thenReturn(engineResult(new RagSource(1L, "讲义A", 0.9, "片段X")));
        when(llmProvider.complete(any(), any(Double.class))).thenReturn("{\"content\":\"题目\"}");

        String result = dialogue.generateQuestion(11L, "SINGLE_CHOICE", "MEDIUM", 2);

        assertEquals("{\"content\":\"题目\"}", result);
        String prompt = captureQuestionPrompt();
        assertTrue(prompt.contains("【课程资料摘录】"), "召回资料段缺失，实际=" + prompt);
        assertTrue(prompt.contains("【课程知识点（知识图谱）】"), "知识图谱段缺失，实际=" + prompt);
        assertTrue(prompt.contains("讲义A"), "召回文档名必须进入 prompt，实际=" + prompt);
        assertTrue(prompt.contains("片段X"), "召回片段必须进入 prompt，实际=" + prompt);
        assertTrue(prompt.contains("闭包"), "命中知识点必须进入 prompt，实际=" + prompt);
        assertTrue(prompt.contains("不得超纲"), "必须声明课程范围约束，实际=" + prompt);
        // 键位约束是下游落库的唯一保障，不能被接地改造顺手丢掉
        for (String key : List.of("content", "options", "correctAnswers", "explanation", "score")) {
            assertTrue(prompt.contains(key), "题目键位约束缺失 " + key + "，实际=" + prompt);
        }
    }

    @Test
    @DisplayName("generateQuestion：证据为空时仍正常生成，prompt 不出现证据段标题")
    void generateQuestion_withoutEvidenceKeepsOriginalPrompt() {
        when(knowledgePointService.listByCourse(12L)).thenReturn(List.of());
        when(ragService.retrieveTyped("SINGLE_CHOICE MEDIUM", 12L, 5))
                .thenReturn(new RagRetrievalResult(null, "UNAVAILABLE", "引擎不可用", 503));
        when(llmProvider.complete(any(), any(Double.class))).thenReturn("{\"content\":\"题目\"}");

        String result = dialogue.generateQuestion(12L, "SINGLE_CHOICE", "MEDIUM", 2);

        assertEquals("{\"content\":\"题目\"}", result);
        String prompt = captureQuestionPrompt();
        assertFalse(prompt.contains("【课程资料摘录】"), "无证据不得拼资料段，实际=" + prompt);
        assertFalse(prompt.contains("【课程知识点（知识图谱）】"), "无证据不得拼图谱段，实际=" + prompt);
        assertTrue(prompt.contains("2道"), "原有题量约束必须保留，实际=" + prompt);
        assertTrue(prompt.contains("content") && prompt.contains("score"), "原有键位约束必须保留，实际=" + prompt);
    }

    @Test
    @DisplayName("recommendQuestionsJson：schema 增加 knowledgePoint，prompt 带课程证据段")
    void recommendQuestionsJson_groundsAndDeclaresKnowledgePoint() {
        when(knowledgePointService.listByCourse(21L))
                .thenReturn(List.of(point(5L, "递归", "函数调用自身", 4)));
        when(ragService.retrieveTyped("递归", 21L, 5))
                .thenReturn(engineResult(new RagSource(2L, "讲义B", 0.8, "片段Y")));
        when(llmProvider.completeJson(any(), any(Double.class), anyString()))
                .thenReturn("{\"questions\":[]}");

        structured.recommendQuestionsJson(21L, "HARD", 3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> messages = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> schema = ArgumentCaptor.forClass(String.class);
        verify(llmProvider).completeJson(messages.capture(),
                org.mockito.ArgumentMatchers.eq(0.8), schema.capture());

        String prompt = promptOf(messages.getValue());
        assertTrue(prompt.contains("【课程资料摘录】"), "召回资料段缺失，实际=" + prompt);
        assertTrue(prompt.contains("【课程知识点（知识图谱）】"), "知识图谱段缺失，实际=" + prompt);
        assertTrue(prompt.contains("讲义B") && prompt.contains("片段Y"), "召回内容必须进入 prompt，实际=" + prompt);
        assertTrue(prompt.contains("递归"), "命中知识点必须进入 prompt，实际=" + prompt);
        assertTrue(schema.getValue().contains("knowledgePoint"), "schema 必须声明 knowledgePoint 字段");
        // 既有字段不得被新增字段挤掉
        assertTrue(schema.getValue().contains("content") && schema.getValue().contains("difficulty"),
                "schema 既有字段缺失，实际=" + schema.getValue());
    }

    @Test
    @DisplayName("parseQuestions：knowledgePoint 有值则透传，缺失/null 不落键且不抛异常")
    void parseQuestions_passesThroughKnowledgePoint() {
        List<Map<String, Object>> withPoint = structured.parseQuestions(
                "{\"questions\":[{\"content\":\"c\",\"type\":\"SINGLE_CHOICE\",\"difficulty\":\"EASY\","
                        + "\"knowledgePoint\":\"闭包\"}]}", "MEDIUM");
        assertEquals(1, withPoint.size());
        assertEquals("闭包", withPoint.get(0).get("knowledgePoint"));
        assertEquals("content", new ArrayList<>(withPoint.get(0).keySet()).get(0), "content 必须仍是首个键");

        List<Map<String, Object>> withoutPoint = structured.parseQuestions(
                "{\"questions\":[{\"content\":\"c\"}]}", "MEDIUM");
        assertEquals(1, withoutPoint.size());
        assertFalse(withoutPoint.get(0).containsKey("knowledgePoint"), "缺失时不得凭空补键");
        assertEquals("c", withoutPoint.get(0).get("content"));

        List<Map<String, Object>> nullPoint = structured.parseQuestions(
                "{\"questions\":[{\"content\":\"c\",\"knowledgePoint\":null}]}", "MEDIUM");
        assertFalse(nullPoint.get(0).containsKey("knowledgePoint"), "null 值不得落键");
    }
}
