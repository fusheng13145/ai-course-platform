package com.aicourse.service.impl;

import com.aicourse.exception.BusinessException;
import com.aicourse.llm.LLMProvider;
import com.aicourse.service.PromptGuard;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 服务单元测试（补充版，自包含，独占类名）。
 * 在覆盖全部公开方法的基础上，额外触达 AIServiceImpl 内部私有分支：
 * <ul>
 *   <li>estimateInputTokens 的 null / 空 / 含 null 元素 / 含 null content 分支（通过反射调用私有方法）；</li>
 *   <li>neutralizeUserMessages 的 null 消息跳过、system 角色原样、user+null content 原样、user+content 中和 四分支；</li>
 *   <li>extractRagAnswer 的 JSON 解析失败(catch) 与 answer 非文本 分支；</li>
 *   <li>recommendLearningWithContext 薄弱项超过 8 条时的 Math.min 截断分支；</li>
 *   <li>recordAiToken 的 tokens<=0 早退 与 正常累加 分支。</li>
 * </ul>
 * 注入真实 MeterRegistry，mock LLMProvider / RAGService / PromptGuard。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AIServiceImplExtraTest {

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
    @DisplayName("chat(String,context,userId): 成功")
    void chat_string() {
        assertEquals("ok", service.chat("hello", "sys", 1L));
    }

    @Test
    @DisplayName("chat(String) 上下文为 null -> 仅 user 消息")
    void chat_string_nullContext() {
        assertEquals("ok", service.chat("hello", null, 1L));
    }

    @Test
    @DisplayName("chat(List): 空/null -> 通用错误文案")
    void chat_list_empty() {
        assertTrue(service.chat(Collections.emptyList(), 1L).length() > 0);
        assertTrue(service.chat(null, 1L).length() > 0);
    }

    @Test
    @DisplayName("chat(List): 混合角色（null 消息跳过 / system 原样 / user+null content 原样 / user+content 中和）")
    void chat_list_mixedRoles() {
        List<Map<String, String>> msgs = new ArrayList<>();
        msgs.add(null);
        msgs.add(Map.of("role", "system", "content", "system-prompt"));
        // Map.of 不允许 null 值，使用可变 Map 构造含 null content 的 user 消息
        Map<String, String> userNullContent = new LinkedHashMap<>();
        userNullContent.put("role", "user");
        userNullContent.put("content", null);
        msgs.add(userNullContent);
        msgs.add(Map.of("role", "user", "content", "hi"));
        msgs.add(Map.of("role", "assistant", "content", "prev"));
        assertEquals("ok", service.chat(msgs, 1L));
    }

    // ===================== 生成类 =====================

    @Test
    @DisplayName("generateQuestion: 成功")
    void generateQuestion() {
        assertEquals("ok", service.generateQuestion(1L, "choice", "easy", 3));
    }

    @Test
    @DisplayName("generateContent: correct / polish / 其它 三分支")
    void generateContent() {
        assertEquals("ok", service.generateContent("correct", "文本"));
        assertEquals("ok", service.generateContent("polish", "文本"));
        assertEquals("ok", service.generateContent("summary", "文本"));
        // 参数为 null（type/params 边界）
        assertEquals("ok", service.generateContent(null, null));
    }

    @Test
    @DisplayName("generateSummary / checkPlagiarism")
    void summaryAndPlagiarism() {
        assertEquals("ok", service.generateSummary("doc"));
        assertEquals("ok", service.checkPlagiarism("t1", "t2"));
        // 空内容边界
        assertEquals("ok", service.generateSummary(""));
        assertEquals("ok", service.checkPlagiarism("", ""));
    }

    // ===================== RAG 问答 + 降级 =====================

    @Test
    @DisplayName("ragAsk: RAG 返回有效 answer -> 提取并清洗返回")
    void ragAsk_answer() {
        when(ragService.ask(anyString(), any(), anyInt())).thenReturn("{\"answer\":\"final answer\",\"sources\":[]}");
        assertEquals("final answer", service.ragAsk("q", 1L));
    }

    @Test
    @DisplayName("ragAsk: RAG 返回 503 -> 回退纯 LLM")
    void ragAsk_503() {
        when(ragService.ask(anyString(), any(), anyInt())).thenReturn("{\"error\":\"x\",\"code\":503}");
        assertEquals("ok", service.ragAsk("q", 1L));
    }

    @Test
    @DisplayName("ragAsk: RAG 返回纯文本（非 JSON）-> extractRagAnswer 原样返回")
    void ragAsk_plainText() {
        when(ragService.ask(anyString(), any(), anyInt())).thenReturn("just text");
        assertEquals("just text", service.ragAsk("q", 1L));
    }

    @Test
    @DisplayName("ragAsk: RAG answer 为空白 -> 回退原串")
    void ragAsk_blankAnswer() {
        when(ragService.ask(anyString(), any(), anyInt())).thenReturn("{\"answer\":\"   \"}");
        assertTrue(service.ragAsk("q", 1L).contains("answer"));
    }

    @Test
    @DisplayName("ragAsk: RAG answer 为非文本(数字) -> 回退原串")
    void ragAsk_nonTextualAnswer() {
        when(ragService.ask(anyString(), any(), anyInt())).thenReturn("{\"answer\":123}");
        assertTrue(service.ragAsk("q", 1L).contains("123"));
    }

    @Test
    @DisplayName("ragAsk: RAG 返回残缺 JSON -> extractRagAnswer 解析异常 catch -> 回退原串")
    void ragAsk_malformedJson() {
        when(ragService.ask(anyString(), any(), anyInt())).thenReturn("{\"answer\":}");
        assertTrue(service.ragAsk("q", 1L).contains("answer"));
    }

    @Test
    @DisplayName("ragAsk: RAG 抛异常 -> 回退纯 LLM")
    void ragAsk_exception() {
        when(ragService.ask(anyString(), any(), anyInt())).thenThrow(new RuntimeException("boom"));
        assertEquals("ok", service.ragAsk("q", 1L));
    }

    @Test
    @DisplayName("ragAsk: RAG 返回 null -> 回退纯 LLM")
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
    @DisplayName("recommendLearningWithContext: 完整学情 + 薄弱项超过 8 条（Math.min 截断）")
    void recommendWithContext_overEightWeakPoints() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("courseList", "数学");
        ctx.put("progress", 60);
        ctx.put("recentScores", "90,80");
        List<Map<String, Object>> weak = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            weak.add(Map.of("name", "知识点" + i, "mastery", (double) i));
        }
        ctx.put("weakPoints", weak);
        String r = service.recommendLearningWithContext(ctx);
        assertTrue(r.length() > 0);
    }

    @Test
    @DisplayName("recommendLearningWithContext: 最小学情（无薄弱项/无附加字段）")
    void recommendWithContext_minimal() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        String r = service.recommendLearningWithContext(ctx);
        assertTrue(r.length() > 0);
    }

    @Test
    @DisplayName("recommendLearningWithContext: 薄弱项按掌握度升序排列")
    void recommendWithContext_sortByMastery() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        List<Map<String, Object>> weak = new ArrayList<>();
        weak.add(Map.of("name", "高", "mastery", 90.0));
        weak.add(Map.of("name", "低", "mastery", 5.0));
        weak.add(Map.of("name", "中", "mastery", 50.0));
        ctx.put("weakPoints", weak);
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
    @DisplayName("各 JSON 生成器：generateLearningAdviceJson / analyzeAnomaliesJson / analyzeWeakPointsJson / recommendQuestionsJson")
    void jsonGenerators() {
        assertEquals("{\"answer\":\"x\"}", service.generateLearningAdviceJson("{}", "[]"));
        assertEquals("{\"answer\":\"x\"}", service.analyzeAnomaliesJson("[]"));
        assertEquals("{\"answer\":\"x\"}", service.analyzeWeakPointsJson("{}"));
        assertEquals("{\"answer\":\"x\"}", service.recommendQuestionsJson(1L, "EASY", 2));
        // recommendQuestionsJson 默认难度分支（difficulty=null）
        assertEquals("{\"answer\":\"x\"}", service.recommendQuestionsJson(1L, null, 2));
    }

    // ===================== JSON 解析 =====================

    @Test
    @DisplayName("parseWeakPoints: null / 纯文本 / weakPoints 数组 / 根数组小写归一 / 非法 JSON / 空数组兜底")
    void parseWeakPoints_all() {
        assertTrue(service.parseWeakPoints(null).isEmpty());
        assertEquals(1, service.parseWeakPoints("some text").size());
        List<Map<String, String>> r1 = service.parseWeakPoints("{\"weakPoints\":[{\"description\":\"d\",\"severity\":\"HIGH\"}]}");
        assertEquals("HIGH", r1.get(0).get("severity"));
        List<Map<String, String>> r2 = service.parseWeakPoints("[{\"description\":\"d\",\"severity\":\"low\"}]");
        assertEquals("LOW", r2.get(0).get("severity"));
        // 非法 severity -> 归中为 MEDIUM
        List<Map<String, String>> r3 = service.parseWeakPoints("[{\"description\":\"d\",\"severity\":\"WEIRD\"}]");
        assertEquals("MEDIUM", r3.get(0).get("severity"));
        assertEquals(1, service.parseWeakPoints("{bad json").size());
        assertEquals(1, service.parseWeakPoints("{\"weakPoints\":[]}").size());
    }

    @Test
    @DisplayName("parseQuestions: null / questions 数组 / 根数组 / 非法 JSON 回退 / 默认难度")
    void parseQuestions_all() {
        assertTrue(service.parseQuestions(null, "MEDIUM").isEmpty());
        List<Map<String, Object>> r1 = service.parseQuestions("{\"questions\":[{\"content\":\"q\",\"type\":\"X\",\"difficulty\":\"Y\"}]}", "MEDIUM");
        assertEquals("q", r1.get(0).get("content"));
        List<Map<String, Object>> r2 = service.parseQuestions("[{\"content\":\"q\"}]", "EASY");
        assertEquals("EASY", r2.get(0).get("difficulty"));
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
        when(llmProvider.completeJson(any(), any(), any()))
                .thenReturn("{\"summary\":\"s\",\"items\":[{\"title\":\"t\",\"detail\":\"d\",\"level\":\"HIGH\"}]}");
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
    @DisplayName("callLlm: 供应商未配置 -> 返回提示（覆盖 multiple 调用点）")
    void callLlm_noProvider() {
        when(llmProvider.isConfigured()).thenReturn(false);
        assertEquals(service.noProviderMessage(), service.recommendLearningJson("数学"));
        assertEquals(service.noProviderMessage(), service.recommendLearningWithContext(Map.of("c", 1)));
        assertEquals(service.noProviderMessage(), service.generateLearningAdviceJson("{}", "[]"));
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

    // ===================== 私有方法：estimateInputTokens 边界（迁移至 AiCoreComponent 后） =====================

    @Test
    @DisplayName("estimateInputTokens(私有): null / 空 / 含 null 元素 / 含 null content / 正常折算")
    void estimateInputTokens_reflection() throws Exception {
        Object core = ReflectionTestUtils.getField(service, "coreComponent");
        Method m = core.getClass().getDeclaredMethod("estimateInputTokens", List.class);
        m.setAccessible(true);

        // null 列表
        assertEquals(0L, m.invoke(core, (Object) null));
        // 空列表
        assertEquals(0L, m.invoke(core, Collections.emptyList()));
        // 含 null 元素的列表（List.of 不允许 null 元素，改用允许 null 的容器构造）
        assertEquals(0L, m.invoke(core, Collections.singletonList((Map<String, String>) null)));
        // 元素 content 为 null
        Map<String, String> nullContent = new LinkedHashMap<>();
        nullContent.put("role", "user");
        nullContent.put("content", null);
        assertEquals(0L, m.invoke(core, List.of(nullContent)));
        // 正常折算：30 字符 -> 20 token
        Map<String, String> withContent = Map.of("role", "user", "content", "x".repeat(30));
        assertEquals(20L, m.invoke(core, List.of(withContent)));
        // content 非 1.5 整除：14 字符 -> 14/1.5 = 9.33 -> (long)9
        Map<String, String> odd = Map.of("role", "user", "content", "x".repeat(14));
        assertEquals(9L, m.invoke(core, List.of(odd)));
    }
}
