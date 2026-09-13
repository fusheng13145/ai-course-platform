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
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AIServiceImpl 单元测试（纯 Mockito，无 Spring 上下文）。
 * <p>meterRegistry 必须用真实 SimpleMeterRegistry：源码内 Counter.builder(...).register(meterRegistry)
 * 在 mock 上会 NPE，真实注册表可正常 register/increment。</p>
 */
@ExtendWith(MockitoExtension.class)
class AIServiceImplTest {

    @Mock
    private LLMProvider llmProvider;
    @Mock
    private RAGService ragService;
    @Mock
    private PromptGuard promptGuard;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private AIServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AIServiceImpl(llmProvider, ragService, promptGuard, meterRegistry);
        // 默认桩：已配置供应商，complete/completeJson 返回合法文本，输出清洗为原样透传
        lenient().when(llmProvider.isConfigured()).thenReturn(true);
        lenient().when(llmProvider.complete(any(), any())).thenReturn("raw");
        lenient().when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"summary\":\"s\",\"items\":[]}");
        lenient().when(llmProvider.stream(any(), any())).thenReturn(Flux.just("tok1", "tok2"));
        lenient().when(promptGuard.neutralizeInjection(anyString())).thenAnswer(i -> i.getArgument(0));
        lenient().when(promptGuard.sanitizeOutput(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ===================== chat =====================
    @Test
    @DisplayName("chat(String) 正常路径")
    void chat_str_ok() {
        String r = service.chat("你好", "sys", 1L);
        assertEquals("raw", r);
        verify(llmProvider).complete(any(), eq(0.7));
    }

    @Test
    @DisplayName("chat(List) 空消息返回降级文案")
    void chat_list_empty() {
        assertEquals(AIServiceImplTest.SERVICE_ERROR_MSG(), service.chat(List.of(), 1L));
    }

    private static String SERVICE_ERROR_MSG() {
        // SERVICE_ERROR_MSG 为私有常量，通过 chat(List) 的空分支间接取得同样文案
        return "AI 服务暂时不可用，请稍后再试。";
    }

    @Test
    @DisplayName("chat(List) 非空消息走中立化+补全")
    void chat_list_ok() {
        String r = service.chat(List.of(Map.of("role", "user", "content", "hi")), 1L);
        assertEquals("raw", r);
        verify(promptGuard).neutralizeInjection("hi");
    }

    // ===================== generate* =====================
    @Test
    @DisplayName("generateQuestion 正常")
    void generateQuestion_ok() {
        assertEquals("raw", service.generateQuestion(1L, "single", "easy", 3));
        verify(llmProvider).complete(any(), eq(0.9));
    }

    @Test
    @DisplayName("generateContent correct 分支")
    void generateContent_correct() {
        String r = service.generateContent("correct", "文本");
        assertTrue(r.contains("raw"));
    }

    @Test
    @DisplayName("generateContent polish 分支")
    void generateContent_polish() {
        String r = service.generateContent("polish", "文本");
        assertTrue(r.contains("raw"));
    }

    @Test
    @DisplayName("generateContent 其它分支")
    void generateContent_else() {
        String r = service.generateContent("summary", "参数");
        assertTrue(r.contains("raw"));
    }

    @Test
    @DisplayName("generateSummary 正常")
    void generateSummary_ok() {
        assertEquals("raw", service.generateSummary("内容"));
        verify(llmProvider).complete(any(), eq(0.3));
    }

    @Test
    @DisplayName("checkPlagiarism 正常")
    void checkPlagiarism_ok() {
        assertEquals("raw", service.checkPlagiarism("a", "b"));
        verify(llmProvider).complete(any(), eq(0.2));
    }

    // ===================== ragAsk =====================
    @Test
    @DisplayName("ragAsk RAG 返回 null 降级纯 LLM")
    void ragAsk_ragNull() {
        when(ragService.ask(anyString(), anyLong(), anyInt())).thenReturn(null);
        assertEquals("raw", service.ragAsk("q", 1L));
    }

    @Test
    @DisplayName("ragAsk RAG 含 503 降级纯 LLM")
    void ragAsk_rag503() {
        when(ragService.ask(anyString(), anyLong(), anyInt())).thenReturn("{\"code\":503,\"msg\":\"x\"}");
        assertEquals("raw", service.ragAsk("q", 1L));
    }

    @Test
    @DisplayName("ragAsk RAG 正常返回答案")
    void ragAsk_ragOk() {
        when(ragService.ask(anyString(), anyLong(), anyInt())).thenReturn("{\"answer\":\"hello\"}");
        assertEquals("hello", service.ragAsk("q", 1L));
    }

    @Test
    @DisplayName("ragAsk RAG 抛异常降级")
    void ragAsk_ragThrows() {
        when(ragService.ask(anyString(), anyLong(), anyInt())).thenThrow(new RuntimeException("boom"));
        assertEquals("raw", service.ragAsk("q", 1L));
    }

    // ===================== recommend* =====================
    @Test
    @DisplayName("recommendLearningJson 已配置供应商")
    void recommendLearningJson_ok() {
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"recommendations\":[{\"resource\":\"r\",\"reason\":\"why\",\"priority\":\"HIGH\"}]}");
        String r = service.recommendLearningJson("数学");
        assertTrue(r.contains("recommendations"));
    }

    @Test
    @DisplayName("recommendLearningJson 未配置供应商返回提示")
    void recommendLearningJson_notConfigured() {
        when(llmProvider.isConfigured()).thenReturn(false);
        assertTrue(service.recommendLearningJson("数学").contains("暂未配置"));
    }

    @Test
    @DisplayName("recommendLearningWithContext 含薄弱项")
    void recommendLearningWithContext_withWeak() {
        Map<String, Object> ctx = new java.util.LinkedHashMap<>();
        ctx.put("weakPoints", List.of(Map.of("name", "函数", "mastery", 30)));
        String r = service.recommendLearningWithContext(ctx);
        assertNotNull(r);
    }

    @Test
    @DisplayName("recommendLearningWithContext 无薄弱项")
    void recommendLearningWithContext_noWeak() {
        String r = service.recommendLearningWithContext(Map.of("progress", 50));
        assertNotNull(r);
    }

    // ===================== extractKnowledgeGraphJson =====================
    @Test
    @DisplayName("extractKnowledgeGraphJson 阶段一为空降级单阶段")
    void extractGraph_stage1blank() {
        when(llmProvider.complete(any(), eq(0.3))).thenReturn("");
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"nodes\":[],\"relations\":[]}");
        String r = service.extractKnowledgeGraphJson("内容");
        assertNotNull(r);
    }

    @Test
    @DisplayName("extractKnowledgeGraphJson 两阶段")
    void extractGraph_stage1ok() {
        when(llmProvider.complete(any(), eq(0.3))).thenReturn("阅读摘要");
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"nodes\":[],\"relations\":[]}");
        String r = service.extractKnowledgeGraphJson("内容");
        assertNotNull(r);
        verify(llmProvider, times(1)).complete(any(), eq(0.3));
        verify(llmProvider).completeJson(any(), any(), any());
    }

    // ===================== parse* =====================
    @Test
    @DisplayName("parseWeakPoints null 返回空")
    void parseWeakPoints_null() {
        assertTrue(service.parseWeakPoints(null).isEmpty());
    }

    @Test
    @DisplayName("parseWeakPoints 合法 JSON")
    void parseWeakPoints_valid() {
        String json = "{\"weakPoints\":[{\"description\":\"d\",\"severity\":\"HIGH\"}]}";
        List<Map<String, String>> r = service.parseWeakPoints(json);
        assertEquals(1, r.size());
        assertEquals("HIGH", r.get(0).get("severity"));
    }

    @Test
    @DisplayName("parseWeakPoints 非法 JSON 回退单条")
    void parseWeakPoints_invalid() {
        List<Map<String, String>> r = service.parseWeakPoints("完全不是json");
        assertEquals(1, r.size());
        assertEquals("MEDIUM", r.get(0).get("severity"));
    }

    @Test
    @DisplayName("parseQuestions null 返回空")
    void parseQuestions_null() {
        assertTrue(service.parseQuestions(null, "MEDIUM").isEmpty());
    }

    @Test
    @DisplayName("parseQuestions 合法 JSON")
    void parseQuestions_valid() {
        String json = "{\"questions\":[{\"content\":\"c\",\"type\":\"single\",\"difficulty\":\"easy\"}]}";
        List<Map<String, Object>> r = service.parseQuestions(json, "MEDIUM");
        assertEquals(1, r.size());
        assertEquals("c", r.get(0).get("content"));
    }

    // ===================== analyze* =====================
    @Test
    @DisplayName("analyzeWeakPoints 正常解析")
    void analyzeWeakPoints_ok() {
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"weakPoints\":[{\"description\":\"d\",\"severity\":\"HIGH\"}]}");
        List<Map<String, String>> r = service.analyzeWeakPoints(Map.of("a", 1));
        assertEquals(1, r.size());
        assertEquals("HIGH", r.get(0).get("severity"));
    }

    @Test
    @DisplayName("analyzeWeakPoints 调用失败降级")
    void analyzeWeakPoints_fallback() {
        when(llmProvider.completeJson(any(), any(), any())).thenThrow(new RuntimeException("boom"));
        List<Map<String, String>> r = service.analyzeWeakPoints(Map.of("a", 1));
        assertEquals(1, r.size());
        assertEquals("AI分析暂时不可用，请稍后重试", r.get(0).get("description"));
    }

    @Test
    @DisplayName("generateLearningAdvice 正常解析")
    void generateLearningAdvice_ok() {
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"summary\":\"总体建议\",\"items\":[{\"title\":\"t\",\"detail\":\"d\",\"level\":\"HIGH\"}]}");
        Map<String, Object> r = service.generateLearningAdvice(Map.of("a", 1));
        assertEquals("总体建议", r.get("summary"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) r.get("items");
        assertEquals(1, items.size());
        assertEquals("HIGH", items.get(0).get("level"));
    }

    @Test
    @DisplayName("generateLearningAdvice 空响应抛 BusinessException")
    void generateLearningAdvice_emptyRaw() {
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("");
        assertThrows(BusinessException.class, () -> service.generateLearningAdvice(Map.of("a", 1)));
    }

    // ===================== 配置/降级 =====================
    @Test
    @DisplayName("isProviderConfigured 注入非空即 true")
    void isProviderConfigured_true() {
        // 源码 isProviderConfigured() 仅判断 llmProvider != null（不调用 llmProvider.isConfigured()）
        assertTrue(service.isProviderConfigured());
    }

    @Test
    @DisplayName("noProviderMessage 返回提示文案")
    void noProviderMessage() {
        assertTrue(service.noProviderMessage().contains("暂未配置"));
    }

    @Test
    @DisplayName("callLlm 返回 null 降级 SERVICE_ERROR_MSG")
    void callLlm_nullResult() {
        when(llmProvider.completeJson(any(), any(), any())).thenReturn(null);
        // recommendLearningJson 在已配置时走 callLlm；completeJson 返回 null → SERVICE_ERROR_MSG
        assertEquals("AI 服务暂时不可用，请稍后再试。", service.recommendLearningJson("数学"));
    }

    // ===================== streamChat =====================
    @Test
    @DisplayName("streamChat 未配置返回提示")
    void streamChat_notConfigured() {
        when(llmProvider.isConfigured()).thenReturn(false);
        Flux<String> f = service.streamChat("q");
        assertEquals(service.noProviderMessage(), f.blockFirst());
    }

    @Test
    @DisplayName("streamChat 已配置返回流式 token")
    void streamChat_configured() {
        when(llmProvider.isConfigured()).thenReturn(true);
        Flux<String> f = service.streamChat("q");
        assertEquals("tok1", f.blockFirst());
        assertEquals(2, f.collectList().block().size());
    }

    // ===================== completeAndSanitize 异常 =====================
    @Test
    @DisplayName("completeAndSanitize 异常抛出并记失败")
    void completeAndSanitize_exception() {
        when(llmProvider.complete(any(), any())).thenThrow(new RuntimeException("boom"));
        assertThrows(RuntimeException.class, () -> service.chat("hi", "sys", 1L));
    }
}
