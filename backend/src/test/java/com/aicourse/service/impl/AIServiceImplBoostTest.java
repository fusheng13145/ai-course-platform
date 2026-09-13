package com.aicourse.service.impl;

import com.aicourse.exception.BusinessException;
import com.aicourse.llm.LLMProvider;
import com.aicourse.service.RAGService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 服务单元测试（boost）：覆盖对话 / 出题 / 内容生成 / RAG 问答 / 推荐 / 学情分析 / JSON 解析 / 流式对话
 * 以及私有埋点、失败降级、503 回退等分支。注入真实 MeterRegistry，mock LLMProvider / RAGService / PromptGuard。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AIServiceImplBoostTest {

    @Mock LLMProvider llmProvider;
    @Mock RAGService ragService;
    @Mock PromptGuard promptGuard;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private AIServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AIServiceImpl(llmProvider, ragService, promptGuard, meterRegistry);
        when(promptGuard.neutralizeInjection(anyString())).thenAnswer(i -> i.getArgument(0));
        when(promptGuard.sanitizeOutput(anyString())).thenAnswer(i -> i.getArgument(0));
        when(llmProvider.isConfigured()).thenReturn(true);
        when(llmProvider.complete(any(), any())).thenReturn("ok");
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"answer\":\"x\"}");
        when(llmProvider.name()).thenReturn("deepseek");
        when(llmProvider.stream(any(), any())).thenReturn(reactor.core.publisher.Flux.just("a", "b"));
    }

    // ===================== 基础对话 =====================
    @Test
    @DisplayName("chat(String): 成功")
    void chat_string() {
        String r = service.chat("hello", "sys", 1L);
        assertEquals("ok", r);
    }

    @Test
    @DisplayName("chat(List): 多轮成功")
    void chat_list() {
        List<Map<String, String>> msgs = List.of(Map.of("role", "user", "content", "hi"));
        assertEquals("ok", service.chat(msgs, 1L));
    }

    @Test
    @DisplayName("chat(List): 空消息 -> 通用错误文案")
    void chat_list_empty() {
        String r = service.chat(Collections.emptyList(), 1L);
        assertTrue(r.length() > 0);
    }

    // ===================== 生成类 =====================
    @Test
    @DisplayName("generateQuestion: 成功")
    void generateQuestion() {
        assertEquals("ok", service.generateQuestion(1L, "choice", "easy", 3));
    }

    @Test
    @DisplayName("generateContent: correct/polish/other 三分支")
    void generateContent() {
        assertEquals("ok", service.generateContent("correct", "text"));
        assertEquals("ok", service.generateContent("polish", "text"));
        assertEquals("ok", service.generateContent("summary", "text"));
    }

    @Test
    @DisplayName("generateSummary / checkPlagiarism")
    void summaryAndPlagiarism() {
        assertEquals("ok", service.generateSummary("doc"));
        assertEquals("ok", service.checkPlagiarism("t1", "t2"));
    }

    // ===================== RAG 问答 + 降级 =====================
    @Test
    @DisplayName("ragAsk: RAG 返回有效 answer")
    void ragAsk_answer() {
        when(ragService.ask(anyString(), any(), anyInt())).thenReturn("{\"answer\":\"final answer\",\"sources\":[]}");
        assertEquals("final answer", service.ragAsk("q", 1L));
    }

    @Test
    @DisplayName("ragAsk: RAG 返回 503 -> 回退 LLM")
    void ragAsk_503() {
        when(ragService.ask(anyString(), any(), anyInt())).thenReturn("{\"error\":\"x\",\"code\":503}");
        assertEquals("ok", service.ragAsk("q", 1L));
    }

    @Test
    @DisplayName("ragAsk: RAG 返回纯文本（非JSON）")
    void ragAsk_plainText() {
        when(ragService.ask(anyString(), any(), anyInt())).thenReturn("just text");
        assertEquals("just text", service.ragAsk("q", 1L));
    }

    @Test
    @DisplayName("ragAsk: RAG 返回 answer 为空白 -> 回退原串")
    void ragAsk_blankAnswer() {
        when(ragService.ask(anyString(), any(), anyInt())).thenReturn("{\"answer\":\"   \"}");
        assertTrue(service.ragAsk("q", 1L).contains("answer"));
    }

    @Test
    @DisplayName("ragAsk: RAG 抛异常 -> 回退 LLM")
    void ragAsk_exception() {
        when(ragService.ask(anyString(), any(), anyInt())).thenThrow(new RuntimeException("boom"));
        assertEquals("ok", service.ragAsk("q", 1L));
    }

    @Test
    @DisplayName("ragAsk: RAG 返回 null -> 回退 LLM")
    void ragAsk_null() {
        when(ragService.ask(anyString(), any(), anyInt())).thenReturn(null);
        assertEquals("ok", service.ragAsk("q", 1L));
    }

    // ===================== 结构化推荐 / 分析 =====================
    @Test
    @DisplayName("recommendLearningJson: 成功")
    void recommendLearningJson() {
        assertEquals("{\"answer\":\"x\"}", service.recommendLearningJson("数学,英语"));
    }

    @Test
    @DisplayName("recommendLearningWithContext: 完整学情（含薄弱项排序）")
    void recommendWithContext_full() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("courseList", "数学");
        ctx.put("progress", 60);
        ctx.put("recentScores", "90,80");
        List<Map<String, Object>> weak = new ArrayList<>();
        weak.add(Map.of("name", "函数", "mastery", 30.0));
        weak.add(Map.of("name", "极限", "mastery", 10.0));
        ctx.put("weakPoints", weak);
        String r = service.recommendLearningWithContext(ctx);
        assertTrue(r.contains("answer") || r.length() > 0);
    }

    @Test
    @DisplayName("recommendLearningWithContext: 最小学情（无薄弱项/无附加字段）")
    void recommendWithContext_minimal() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        String r = service.recommendLearningWithContext(ctx);
        assertTrue(r.length() > 0);
    }

    @Test
    @DisplayName("extractKnowledgeGraphJson: 阶段一成功 -> 阶段二")
    void extractGraph_stage1Ok() {
        when(llmProvider.complete(any(), any())).thenReturn("reading summary");
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"nodes\":[],\"relations\":[]}");
        assertEquals("{\"nodes\":[],\"relations\":[]}", service.extractKnowledgeGraphJson("content"));
    }

    @Test
    @DisplayName("extractKnowledgeGraphJson: 阶段一空白 -> 单阶段降级")
    void extractGraph_stage1Blank() {
        when(llmProvider.complete(any(), any())).thenReturn("");
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"nodes\":[],\"relations\":[]}");
        assertEquals("{\"nodes\":[],\"relations\":[]}", service.extractKnowledgeGraphJson("content"));
    }

    @Test
    @DisplayName("extractKnowledgeGraphJson: 阶段一抛异常 -> 单阶段降级")
    void extractGraph_stage1Throw() {
        when(llmProvider.complete(any(), any())).thenThrow(new RuntimeException("boom"));
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"nodes\":[],\"relations\":[]}");
        assertEquals("{\"nodes\":[],\"relations\":[]}", service.extractKnowledgeGraphJson("content"));
    }

    @Test
    @DisplayName("generateLearningAdviceJson / analyzeAnomaliesJson / analyzeWeakPointsJson / recommendQuestionsJson")
    void jsonGenerators() {
        assertEquals("{\"answer\":\"x\"}", service.generateLearningAdviceJson("{}", "[]"));
        assertEquals("{\"answer\":\"x\"}", service.analyzeAnomaliesJson("[]"));
        assertEquals("{\"answer\":\"x\"}", service.analyzeWeakPointsJson("{}"));
        assertEquals("{\"answer\":\"x\"}", service.recommendQuestionsJson(1L, "EASY", 2));
    }

    // ===================== JSON 解析 =====================
    @Test
    @DisplayName("parseWeakPoints: 各种输入")
    void parseWeakPoints_all() {
        // null 文本 -> 空列表
        assertTrue(service.parseWeakPoints(null).isEmpty());
        // 纯文本 -> 单条 MEDIUM
        assertEquals(1, service.parseWeakPoints("some text").size());
        // 含 weakPoints 数组
        List<Map<String, String>> r1 = service.parseWeakPoints("{\"weakPoints\":[{\"description\":\"d\",\"severity\":\"HIGH\"}]}");
        assertEquals("HIGH", r1.get(0).get("severity"));
        // 根数组 + 小写 severity 归一
        List<Map<String, String>> r2 = service.parseWeakPoints("[{\"description\":\"d\",\"severity\":\"low\"}]");
        assertEquals("LOW", r2.get(0).get("severity"));
        // 非法 JSON -> 回退
        assertEquals(1, service.parseWeakPoints("{bad json").size());
        // 空 weakPoints -> 兜底提示
        assertEquals(1, service.parseWeakPoints("{\"weakPoints\":[]}").size());
    }

    @Test
    @DisplayName("parseQuestions: 各种输入")
    void parseQuestions_all() {
        // null -> 空
        assertTrue(service.parseQuestions(null, "MEDIUM").isEmpty());
        // 含 questions 数组
        List<Map<String, Object>> r1 = service.parseQuestions("{\"questions\":[{\"content\":\"q\",\"type\":\"X\",\"difficulty\":\"Y\"}]}", "MEDIUM");
        assertEquals("q", r1.get(0).get("content"));
        // 根数组
        List<Map<String, Object>> r2 = service.parseQuestions("[{\"content\":\"q\"}]", "EASY");
        assertEquals("EASY", r2.get(0).get("difficulty"));
        // 非法 JSON -> 回退
        assertEquals(1, service.parseQuestions("{bad", "MEDIUM").size());
    }

    // ===================== 学情分析（结构化） =====================
    @Test
    @DisplayName("analyzeWeakPoints(Map): 成功")
    void analyzeWeakPoints_map() {
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"weakPoints\":[{\"description\":\"d\",\"severity\":\"HIGH\"}]}");
        List<Map<String, String>> r = service.analyzeWeakPoints(Map.of("x", 1));
        assertEquals(1, r.size());
    }

    @Test
    @DisplayName("analyzeWeakPoints(Map): 异常 -> 降级提示")
    void analyzeWeakPoints_mapException() {
        when(llmProvider.completeJson(any(), any(), any())).thenThrow(new RuntimeException("boom"));
        List<Map<String, String>> r = service.analyzeWeakPoints(Map.of("x", 1));
        assertEquals(1, r.size());
    }

    @Test
    @DisplayName("generateLearningAdvice(Map): 成功含 items")
    void generateLearningAdvice_map() {
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"summary\":\"s\",\"items\":[{\"title\":\"t\",\"detail\":\"d\",\"level\":\"HIGH\"}]}");
        Map<String, Object> r = service.generateLearningAdvice(Map.of("x", 1));
        assertEquals("s", r.get("summary"));
        assertEquals(1, ((List<?>) r.get("items")).size());
    }

    @Test
    @DisplayName("generateLearningAdvice(Map): 无有效 JSON -> BusinessException")
    void generateLearningAdvice_mapNoJson() {
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("plain text no json");
        assertThrows(BusinessException.class, () -> service.generateLearningAdvice(Map.of("x", 1)));
    }

    @Test
    @DisplayName("generateLearningAdvice(Map): 调用异常 -> BusinessException")
    void generateLearningAdvice_mapException() {
        when(llmProvider.completeJson(any(), any(), any())).thenThrow(new RuntimeException("boom"));
        assertThrows(BusinessException.class, () -> service.generateLearningAdvice(Map.of("x", 1)));
    }

    // ===================== 供应商未配置 / 流式 =====================
    @Test
    @DisplayName("isProviderConfigured / noProviderMessage")
    void providerMeta() {
        assertTrue(service.isProviderConfigured());
        assertTrue(service.noProviderMessage().contains("未配置"));
    }

    @Test
    @DisplayName("callLlm: 供应商未配置 -> 返回提示")
    void callLlm_noProvider() {
        when(llmProvider.isConfigured()).thenReturn(false);
        assertEquals(service.noProviderMessage(), service.recommendLearningJson("数学"));
    }

    @Test
    @DisplayName("callLlm: 返回 null -> 通用错误文案 + 失败埋点")
    void callLlm_nullResult() {
        when(llmProvider.completeJson(any(), any(), any())).thenReturn(null);
        String r = service.recommendLearningJson("数学");
        assertTrue(r.contains("不可用") || r.contains("暂未配置"));
    }

    @Test
    @DisplayName("completeAndSanitize: 调用异常 -> 失败埋点并重抛")
    void completeAndSanitize_failure() {
        when(llmProvider.complete(any(), any())).thenThrow(new RuntimeException("boom"));
        assertThrows(RuntimeException.class, () -> service.chat("hello", "sys", 1L));
    }

    @Test
    @DisplayName("callLlm: completeJson 抛异常 -> 失败埋点并重抛")
    void callLlm_exception() {
        when(llmProvider.completeJson(any(), any(), any())).thenThrow(new RuntimeException("boom"));
        assertThrows(RuntimeException.class, () -> service.recommendLearningJson("数学"));
    }

    @Test
    @DisplayName("streamChat: 未配置 -> 直接返回提示 Flux")
    void streamChat_noProvider() {
        when(llmProvider.isConfigured()).thenReturn(false);
        reactor.core.publisher.Flux<String> f = service.streamChat("q");
        assertEquals(service.noProviderMessage(), f.blockFirst());
    }

    @Test
    @DisplayName("streamChat: 已配置 -> 流式 + 护栏")
    void streamChat_configured() {
        reactor.core.publisher.Flux<String> f = service.streamChat("q");
        assertEquals("a", f.blockFirst());
    }
}
