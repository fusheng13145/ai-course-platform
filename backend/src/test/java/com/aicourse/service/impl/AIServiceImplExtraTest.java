package com.aicourse.service.impl;

import com.aicourse.exception.BusinessException;
import com.aicourse.llm.LLMProvider;
import com.aicourse.service.RAGService;
import com.aicourse.service.component.ai.AiCoreComponent;
import com.aicourse.service.component.ai.AiDialogueComponent;
import com.aicourse.service.component.ai.AiRagComponent;
import com.aicourse.service.component.ai.AiStructuredComponent;
import com.aicourse.vo.QaAnswerVO;
import com.aicourse.vo.QaSourceVO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import java.util.stream.Collectors;

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
 *   <li>recordAiToken 的 tokens&lt;=0 早退 与 正常累加 分支。</li>
 * </ul>
 * 注入真实 MeterRegistry，mock LLMProvider / RAGService / PromptGuard。
 * <p>打桩原则：发往供应商的 messages / temperature / jsonSchema 以及 RAG 的 (问题, 课程ID, topK)
 * 都是被测逻辑的输出，一律用 ArgumentCaptor 捕获后断言结构内容；只有「异常注入」「null 注入」
 * 与 setUp 的兜底桩保留 any()（其参数不承载被测逻辑的映射结果）。</p>
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
        // 原兼容构造器已移除：此处内联其组件组装逻辑，再走主构造器（4 个组件）
        AiCoreComponent coreComponent = new AiCoreComponent(llmProvider, promptGuard, meterRegistry);
        AiDialogueComponent dialogueComponent = new AiDialogueComponent(coreComponent);
        AiRagComponent ragComponent = new AiRagComponent(ragService, promptGuard, coreComponent);
        AiStructuredComponent structuredComponent = new AiStructuredComponent(coreComponent);
        service = new AIServiceImpl(dialogueComponent, ragComponent, structuredComponent, coreComponent);
        // 兜底桩（any() 合理）：只保证链路跑通，具体参数由各用例捕获断言
        when(promptGuard.neutralizeInjection(anyString())).thenAnswer(i -> i.getArgument(0));
        when(promptGuard.sanitizeOutput(anyString())).thenAnswer(i -> i.getArgument(0));
        when(llmProvider.isConfigured()).thenReturn(true);
        when(llmProvider.complete(any(), any())).thenReturn("ok");
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"answer\":\"x\"}");
        when(llmProvider.name()).thenReturn("deepseek");
        when(llmProvider.stream(any(), any())).thenReturn(reactor.core.publisher.Flux.just("a", "b"));
    }

    // ===================== 请求载荷捕获辅助 =====================

    /** 一次 complete 调用载荷：messages 与温度都是被测逻辑产物。 */
    private record Completion(List<Map<String, String>> messages, Double temperature) {
        String prompt() {
            return messages.stream()
                    .filter(m -> "user".equals(m.get("role")))
                    .map(m -> String.valueOf(m.get("content")))
                    .collect(Collectors.joining("\n"));
        }
        String flattened() {
            return messages.stream().map(m -> m.get("role") + "=" + m.get("content"))
                    .collect(Collectors.joining("|"));
        }
    }

    /** 一次 completeJson 调用载荷：额外含 JSON Schema。 */
    private record JsonCall(List<Map<String, String>> messages, Double temperature, String schema) {
        String prompt() {
            return messages.stream()
                    .filter(m -> "user".equals(m.get("role")))
                    .map(m -> String.valueOf(m.get("content")))
                    .collect(Collectors.joining("\n"));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Completion> captureCompletions(int expectedCalls) {
        ArgumentCaptor<List<Map<String, String>>> msgs = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Double> temp = ArgumentCaptor.forClass(Double.class);
        verify(llmProvider, times(expectedCalls)).complete(msgs.capture(), temp.capture());
        List<Completion> out = new ArrayList<>();
        for (int i = 0; i < msgs.getAllValues().size(); i++) {
            out.add(new Completion(msgs.getAllValues().get(i), temp.getAllValues().get(i)));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<JsonCall> captureJsonCalls(int expectedCalls) {
        ArgumentCaptor<List<Map<String, String>>> msgs = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Double> temp = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<String> schema = ArgumentCaptor.forClass(String.class);
        verify(llmProvider, times(expectedCalls)).completeJson(msgs.capture(), temp.capture(), schema.capture());
        List<JsonCall> out = new ArrayList<>();
        for (int i = 0; i < msgs.getAllValues().size(); i++) {
            out.add(new JsonCall(msgs.getAllValues().get(i), temp.getAllValues().get(i), schema.getAllValues().get(i)));
        }
        return out;
    }

    /** 单次 complete 调用的便捷读取。 */
    private Completion singleCompletion() {
        List<Completion> all = captureCompletions(1);
        return all.get(0);
    }

    // ===================== 基础对话 =====================

    @Test
    @DisplayName("chat(String,context,userId): 上下文以 system、输入以 user 两条消息下发，温度 0.7")
    void chat_string() {
        assertEquals("ok", service.chat("hello", "sys", 1L));
        // 关键路径收紧：不满足于 any() 打桩下的 "ok"，而是捕获真正发给 LLM 的 messages，
        // 并按角色断言——业务上下文必须是 system 角色，用户输入必须是 user 角色。
        Completion c = singleCompletion();
        assertEquals(0.7, c.temperature());
        assertEquals(2, c.messages().size(), "实际下发=" + c.flattened());
        assertEquals("system", c.messages().get(0).get("role"), "上下文必须以 system 角色下发，实际=" + c.flattened());
        assertEquals("sys", c.messages().get(0).get("content"));
        assertEquals("user", c.messages().get(1).get("role"));
        assertEquals("hello", c.messages().get(1).get("content"));
    }

    @Test
    @DisplayName("chat(String) 上下文为 null -> 仅 user 消息")
    void chat_string_nullContext() {
        assertEquals("ok", service.chat("hello", null, 1L));
        Completion c = singleCompletion();
        // 空上下文不应产出 role=system 且 content=null 的垃圾消息（部分供应商会因此报错）
        assertEquals(1, c.messages().size(), "实际下发=" + c.flattened());
        assertEquals("user", c.messages().get(0).get("role"));
        assertEquals("hello", c.messages().get(0).get("content"));
    }

    @Test
    @DisplayName("chat(List): 空/null -> 通用错误文案")
    void chat_list_empty() {
        assertTrue(service.chat(Collections.emptyList(), 1L).length() > 0);
        assertTrue(service.chat(null, 1L).length() > 0);
        // 空历史必须短路，不得带着空 messages 去计费调用
        verify(llmProvider, never()).complete(any(), any());
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

        // 逐条断言转发给供应商的历史：护栏只应改写 user 且 content 非空的那条，
        // system/assistant 属服务端可信构造，被改写就等于篡改系统指令。
        Completion c = singleCompletion();
        assertEquals(4, c.messages().size(), "null 消息应被跳过，其余 4 条原样保留，实际=" + c.flattened());
        assertEquals("system", c.messages().get(0).get("role"));
        assertEquals("system-prompt", c.messages().get(0).get("content"));
        assertEquals("user", c.messages().get(1).get("role"));
        assertNull(c.messages().get(1).get("content"), "content 为 null 的 user 消息应原样透传");
        assertEquals("hi", c.messages().get(2).get("content"));
        assertEquals("prev", c.messages().get(3).get("content"));
        verify(promptGuard).neutralizeInjection("hi");
        verify(promptGuard, never()).neutralizeInjection("system-prompt");
        verify(promptGuard, never()).neutralizeInjection("prev");
    }

    // ===================== 生成类 =====================

    @Test
    @DisplayName("generateQuestion: 成功，课程/题量/难度/题型均进入 prompt")
    void generateQuestion() {
        assertEquals("ok", service.generateQuestion(4721L, "choice", "easy", 3));
        Completion c = singleCompletion();
        assertEquals(0.9, c.temperature());
        assertTrue(c.prompt().contains("3道"), "题量必须进入 prompt，实际=" + c.prompt());
        assertTrue(c.prompt().contains("easy难度"), "难度必须进入 prompt，实际=" + c.prompt());
        assertTrue(c.prompt().contains("类型为choice"), "题型必须进入 prompt，实际=" + c.prompt());
        // 回归：courseId 必须参与 prompt，否则跨课程出题时模型不知道题目归属哪门课
        assertTrue(c.prompt().contains("4721"), "courseId 必须进入 prompt，实际=" + c.prompt());
    }

    @Test
    @DisplayName("generateQuestion: courseId 为空时省略课程限定语，难度/题型缺省兜底")
    void generateQuestion_nullCourseAndBlankDifficulty() {
        assertEquals("ok", service.generateQuestion(null, null, null, 2));
        String prompt = singleCompletion().prompt();
        assertFalse(prompt.contains("courseId"), "无课程上下文时不应拼出课程限定语，实际=" + prompt);
        assertTrue(prompt.contains("MEDIUM难度"), "难度为空应兜底 MEDIUM，实际=" + prompt);
        assertTrue(prompt.contains("类型为SINGLE_CHOICE"), "题型为空应兜底单选，实际=" + prompt);
        // 字面量 "null" 一旦进入 prompt，既压低生成质量又按污染后的长度照常计费
        assertFalse(prompt.contains("null"), "prompt 不得出现字面量 null，实际=" + prompt);
    }

    @Test
    @DisplayName("generateContent: correct / polish / 其它 / null 入参 四分支模板互不混用")
    void generateContent() {
        assertEquals("ok", service.generateContent("correct", "文本"));
        assertEquals("ok", service.generateContent("polish", "文本"));
        assertEquals("ok", service.generateContent("summary", "文本"));
        // 参数为 null（type/params 边界）
        assertEquals("ok", service.generateContent(null, null));

        List<Completion> calls = captureCompletions(4);
        assertEquals(4, calls.size());
        // 每个分支的温度都是被测逻辑约定的一部分（批改要稳、生成要有创造性）
        calls.forEach(c -> assertEquals(0.7, c.temperature()));
        assertTrue(calls.get(0).prompt().contains("批改老师"), "correct 应使用批改模板，实际=" + calls.get(0).prompt());
        assertTrue(calls.get(0).prompt().contains("文本内容：\n文本"), "correct 应拼入原文，实际=" + calls.get(0).prompt());
        assertTrue(calls.get(1).prompt().contains("润色"), "polish 应使用润色模板，实际=" + calls.get(1).prompt());
        assertFalse(calls.get(1).prompt().contains("批改老师"), "polish 不得复用批改模板");
        assertTrue(calls.get(1).prompt().contains("Markdown"), "polish 应要求保留 Markdown 结构");
        assertEquals("请生成summary内容，文本", calls.get(2).prompt(), "默认分支应直接拼接 type+params");
        // 回归：type/params 为 null 时兜底为空串，不得把字面量 "null" 拼进 prompt
        assertEquals("请生成内容，", calls.get(3).prompt(), "null 入参应兜底为空，实际=" + calls.get(3).prompt());
    }

    @Test
    @DisplayName("generateSummary / checkPlagiarism：正文与两段待比对文本分别带标签进入 prompt")
    void summaryAndPlagiarism() {
        assertEquals("ok", service.generateSummary("doc"));
        assertEquals("ok", service.checkPlagiarism("t1", "t2"));
        // 空内容边界
        assertEquals("ok", service.generateSummary(""));
        assertEquals("ok", service.checkPlagiarism("", ""));

        List<Completion> calls = captureCompletions(4);
        assertEquals(0.3, calls.get(0).temperature(), "摘要温度应为 0.3");
        assertTrue(calls.get(0).prompt().contains("请总结以下内容：\ndoc"), "摘要应拼入正文，实际=" + calls.get(0).prompt());
        assertEquals(0.2, calls.get(1).temperature(), "查重温度应为 0.2");
        assertTrue(calls.get(1).prompt().contains("文本1：t1"), "第一段应带标签，实际=" + calls.get(1).prompt());
        assertTrue(calls.get(1).prompt().contains("文本2：t2"), "第二段应带标签，实际=" + calls.get(1).prompt());
        // 当前行为：空内容不做前置校验，仍会发起一次带空正文的计费调用
        assertTrue(calls.get(2).prompt().endsWith("请总结以下内容：\n"), "当前行为：空摘要仍发起调用，实际=" + calls.get(2).prompt());
        assertTrue(calls.get(3).prompt().contains("文本1：\n文本2："), "当前行为：两段空文本仍被拼接下发，实际=" + calls.get(3).prompt());
    }

    // ===================== RAG 问答 + 降级 =====================

    @Test
    @DisplayName("ragAsk: RAG 返回有效 answer -> 提取并清洗返回，检索参数为(问题, 课程ID, topK=5)")
    void ragAsk_answer() {
        when(ragService.ask("q", 1L, 5)).thenReturn("{\"answer\":\"final answer\",\"sources\":[]}");
        QaAnswerVO vo = service.ragAsk("q", 1L);
        assertEquals("final answer", vo.getAnswer());
        verify(ragService).ask("q", 1L, 5);
        verify(promptGuard).sanitizeOutput("final answer");
        // 命中 RAG 时不应再触发纯 LLM 补全
        verify(llmProvider, never()).complete(any(), any());
    }

    @Test
    @DisplayName("ragAsk: RAG 返回 sources -> 溯源一并带回，answer 与 sources 互不吞掉")
    void ragAsk_carriesSources() {
        when(ragService.ask("q", 1L, 5)).thenReturn("{\"answer\":\"三次握手同步序号\","
                + "\"sources\":[{\"documentId\":9,\"documentName\":\"计算机网络-第6章-传输层TCP与UDP\","
                + "\"relevanceScore\":0.9937,\"snippet\":\"TCP 三次握手的主要目的是同步双方的初始序号\"}],"
                + "\"mode\":\"ENGINE\"}");
        QaAnswerVO vo = service.ragAsk("q", 1L);
        assertEquals("三次握手同步序号", vo.getAnswer());
        assertEquals(1, vo.getSources().size());
        QaSourceVO source = vo.getSources().get(0);
        assertEquals(9L, source.getDocumentId());
        assertEquals("计算机网络-第6章-传输层TCP与UDP", source.getDocumentName());
        assertEquals(0.9937, source.getRelevanceScore());
        assertTrue(source.getSnippet().contains("同步双方的初始序号"), "片段须原样带回，实际=" + source.getSnippet());
    }

    @Test
    @DisplayName("ragAsk: RAG 返回 503 -> 回退纯 LLM，且降级 prompt 保留课程语境")
    void ragAsk_503() {
        when(ragService.ask("q", 1L, 5)).thenReturn("{\"error\":\"x\",\"code\":503}");
        QaAnswerVO vo = service.ragAsk("q", 1L);
        assertEquals("ok", vo.getAnswer());
        assertNotNull(vo.getSources(), "降级路径的 sources 必须是空列表而非 null，前端据此判空");
        assertTrue(vo.getSources().isEmpty(), "纯 LLM 直答无检索出处");
        Completion c = singleCompletion();
        assertEquals(0.5, c.temperature());
        assertTrue(c.flattened().contains("请基于课程知识回答问题"), "降级应带课程语境 system，实际=" + c.flattened());
        assertEquals("q", c.prompt(), "降级须透传原始问题，实际=" + c.prompt());
    }

    @Test
    @DisplayName("ragAsk: RAG 返回纯文本（非 JSON）-> extractRagAnswer 原样返回")
    void ragAsk_plainText() {
        when(ragService.ask("q", 1L, 5)).thenReturn("just text");
        assertEquals("just text", service.ragAsk("q", 1L).getAnswer());
        verify(ragService).ask("q", 1L, 5);
    }

    @Test
    @DisplayName("ragAsk: RAG answer 为空白 -> 回退原串")
    void ragAsk_blankAnswer() {
        when(ragService.ask("q", 1L, 5)).thenReturn("{\"answer\":\"   \"}");
        assertTrue(service.ragAsk("q", 1L).getAnswer().contains("answer"));
    }

    @Test
    @DisplayName("ragAsk: RAG answer 为非文本(数字) -> 回退原串")
    void ragAsk_nonTextualAnswer() {
        when(ragService.ask("q", 1L, 5)).thenReturn("{\"answer\":123}");
        assertTrue(service.ragAsk("q", 1L).getAnswer().contains("123"));
    }

    @Test
    @DisplayName("ragAsk: RAG 返回残缺 JSON -> extractRagAnswer 解析异常 catch -> 回退原串")
    void ragAsk_malformedJson() {
        when(ragService.ask("q", 1L, 5)).thenReturn("{\"answer\":}");
        assertTrue(service.ragAsk("q", 1L).getAnswer().contains("answer"));
    }

    @Test
    @DisplayName("ragAsk: RAG 抛异常 -> 回退纯 LLM")
    void ragAsk_exception() {
        when(ragService.ask("q", 1L, 5)).thenThrow(new RuntimeException("boom"));
        assertEquals("ok", service.ragAsk("q", 1L).getAnswer());
        Completion fallback = singleCompletion();
        assertEquals(0.5, fallback.temperature());
        // 回退路径丢了提问原文就会变成「无题作答」，故问题与课程语境都要落在消息内容里
        assertTrue(fallback.prompt().contains("q"), "回退 prompt 须保留提问原文，实际=" + fallback.flattened());
        assertTrue(fallback.flattened().contains("请基于课程知识回答问题"),
                "回退应带课程语境 system，实际=" + fallback.flattened());
    }

    @Test
    @DisplayName("ragAsk: RAG 返回 null -> 回退纯 LLM")
    void ragAsk_null() {
        when(ragService.ask("q", 1L, 5)).thenReturn(null);
        assertEquals("ok", service.ragAsk("q", 1L).getAnswer());
        Completion fallback = singleCompletion();
        assertEquals(0.5, fallback.temperature());
        assertTrue(fallback.prompt().contains("q"), "回退 prompt 须保留提问原文，实际=" + fallback.flattened());
        assertTrue(fallback.flattened().contains("请基于课程知识回答问题"),
                "回退应带课程语境 system，实际=" + fallback.flattened());
    }

    // ===================== 结构化推荐 / 分析 =====================

    @Test
    @DisplayName("recommendLearningJson: 课程列表进入 prompt，schema 约束 recommendations")
    void recommendLearningJson() {
        assertEquals("{\"answer\":\"x\"}", service.recommendLearningJson("数学,英语"));
        List<JsonCall> calls = captureJsonCalls(1);
        assertEquals(0.7, calls.get(0).temperature());
        assertTrue(calls.get(0).prompt().contains("我目前在学习以下课程：数学,英语"),
                "课程列表必须拼入 prompt，实际=" + calls.get(0).prompt());
        assertTrue(calls.get(0).schema().contains("recommendations"), "schema 应约束 recommendations");
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

        List<JsonCall> calls = captureJsonCalls(1);
        assertEquals(0.6, calls.get(0).temperature());
        String prompt = calls.get(0).prompt();
        assertTrue(prompt.contains("已选课程：数学"), "课程列表应带标签下发，实际=" + prompt);
        assertTrue(prompt.contains("课程平均进度：60%"), "进度应带百分号下发，实际=" + prompt);
        assertTrue(prompt.contains("近期成绩：90,80"), "近期成绩应下发，实际=" + prompt);
        // 截断分支：只取掌握度最低的前 8 条，第 9 条起不得进入 prompt（防 prompt 爆炸与计费膨胀）
        for (int i = 0; i < 8; i++) {
            assertTrue(prompt.contains("知识点" + i + "（掌握度："),
                    "前 8 条薄弱项应带掌握度进入 prompt，缺失知识点" + i);
        }
        for (int i = 8; i < 12; i++) {
            assertFalse(prompt.contains("知识点" + i), "超过 8 条的薄弱项应被截断，实际含知识点" + i);
        }
        assertEquals(8, prompt.lines().filter(l -> l.contains("（掌握度：")).count(),
                "prompt 中薄弱项条目应恰好 8 条（Math.min 截断）");
    }

    @Test
    @DisplayName("recommendLearningWithContext: 最小学情（无薄弱项/无附加字段）")
    void recommendWithContext_minimal() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        String r = service.recommendLearningWithContext(ctx);
        assertTrue(r.length() > 0);
        String prompt = captureJsonCalls(1).get(0).prompt();
        assertTrue(prompt.contains("【学生学情画像】"), "应保留画像标题，实际=" + prompt);
        assertTrue(prompt.contains("暂无薄弱项数据"), "无薄弱项时应给出说明，实际=" + prompt);
        // 字段缺失时不得输出「已选课程：null」这类垃圾标签（containsKey 守卫必须生效）
        assertFalse(prompt.contains("已选课程"), "缺字段不应输出课程标签，实际=" + prompt);
        assertFalse(prompt.contains("课程平均进度"), "缺字段不应输出进度标签，实际=" + prompt);
        assertFalse(prompt.contains("近期成绩"), "缺字段不应输出成绩标签，实际=" + prompt);
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

        String prompt = captureJsonCalls(1).get(0).prompt();
        int low = prompt.indexOf("低（掌握度：5.0");
        int mid = prompt.indexOf("中（掌握度：50.0");
        int high = prompt.indexOf("高（掌握度：90.0");
        // 排序是「最弱的在前」这一业务语义的唯一保障：乱序会让推荐把力气花在已掌握项上
        assertTrue(low >= 0 && mid >= 0 && high >= 0, "三条薄弱项都应带掌握度下发，实际=" + prompt);
        assertTrue(low < mid && mid < high, "薄弱项必须按掌握度升序排列，实际=" + prompt);
    }

    @Test
    @DisplayName("extractKnowledgeGraphJson: 阶段一成功 -> 阶段二 prompt 嵌入阶段一产出")
    void extractGraph_stage1Ok() {
        when(llmProvider.complete(any(), any())).thenReturn("reading summary");
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"nodes\":[],\"relations\":[]}");
        assertEquals("{\"nodes\":[],\"relations\":[]}", service.extractKnowledgeGraphJson("content"));

        Completion stage1 = singleCompletion();
        assertEquals(0.3, stage1.temperature());
        assertTrue(stage1.prompt().contains("你是一位正在学习以下课程内容的学生"), "阶段一应为学习者视角，实际=" + stage1.prompt());
        assertTrue(stage1.prompt().contains("content"), "阶段一须携带原文，实际=" + stage1.prompt());

        JsonCall stage2 = captureJsonCalls(1).get(0);
        assertEquals(0.3, stage2.temperature());
        assertTrue(stage2.prompt().contains("领域专家"), "阶段二应为专家视角，实际=" + stage2.prompt());
        assertTrue(stage2.prompt().contains("reading summary"), "阶段一产出必须喂给阶段二，实际=" + stage2.prompt());
        assertTrue(stage2.prompt().contains("INCLUDES"), "阶段二应内联 schema 约束关系类型，实际=" + stage2.prompt());
    }

    @Test
    @DisplayName("extractKnowledgeGraphJson: 阶段一空白 -> 单阶段降级")
    void extractGraph_stage1Blank() {
        when(llmProvider.complete(any(), any())).thenReturn("");
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"nodes\":[],\"relations\":[]}");
        assertEquals("{\"nodes\":[],\"relations\":[]}", service.extractKnowledgeGraphJson("content"));
        // 空白阶段一不得把空摘要喂给阶段二，必须走单阶段模板
        JsonCall call = captureJsonCalls(1).get(0);
        assertTrue(call.prompt().contains("抽取知识结构"), "应走单阶段抽取模板，实际=" + call.prompt());
        assertTrue(call.prompt().contains("content"), "单阶段仍须携带原文，实际=" + call.prompt());
        assertTrue(call.schema().contains("nodes") && call.schema().contains("relations"), "抽取 schema 不完整");
    }

    @Test
    @DisplayName("extractKnowledgeGraphJson: 阶段一抛异常 -> 单阶段降级")
    void extractGraph_stage1Throw() {
        when(llmProvider.complete(any(), any())).thenThrow(new RuntimeException("boom"));
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"nodes\":[],\"relations\":[]}");
        assertEquals("{\"nodes\":[],\"relations\":[]}", service.extractKnowledgeGraphJson("content"));
        JsonCall call = captureJsonCalls(1).get(0);
        assertEquals(0.3, call.temperature());
        assertTrue(call.prompt().contains("抽取知识结构"), "异常后应降级为单阶段抽取，实际=" + call.prompt());
    }

    @Test
    @DisplayName("各 JSON 生成器：入参拼接与 schema 默认值逐项校验")
    void jsonGenerators() {
        assertEquals("{\"answer\":\"x\"}", service.generateLearningAdviceJson("{}", "[]"));
        assertEquals("{\"answer\":\"x\"}", service.analyzeAnomaliesJson("[]"));
        assertEquals("{\"answer\":\"x\"}", service.analyzeWeakPointsJson("{}"));
        assertEquals("{\"answer\":\"x\"}", service.recommendQuestionsJson(7L, "EASY", 2));
        // recommendQuestionsJson 默认难度分支（difficulty=null）
        assertEquals("{\"answer\":\"x\"}", service.recommendQuestionsJson(7L, null, 2));

        List<JsonCall> calls = captureJsonCalls(5);
        // 1) 学习建议：学情统计与薄弱项两段都必须拼入
        assertEquals(0.6, calls.get(0).temperature());
        assertTrue(calls.get(0).prompt().contains("学情统计：{}"), "应拼入学情统计，实际=" + calls.get(0).prompt());
        assertTrue(calls.get(0).prompt().contains("薄弱项：[]"), "应拼入薄弱项结果，实际=" + calls.get(0).prompt());
        assertTrue(calls.get(0).schema().contains("summary"), "建议 schema 应约束 summary");
        // 2) 异常监控：日志原文拼入，schema 约束 alerts
        assertEquals(0.3, calls.get(1).temperature());
        assertTrue(calls.get(1).prompt().contains("登录与操作日志：\n[]"), "应拼入日志原文，实际=" + calls.get(1).prompt());
        assertTrue(calls.get(1).schema().contains("alerts"), "监控 schema 应约束 alerts");
        // 3) 薄弱项分析：统计拼入，schema 约束 weakPoints
        assertEquals(0.4, calls.get(2).temperature());
        assertTrue(calls.get(2).prompt().contains("学习统计数据，分析学习者的知识薄弱项：{}"), "应拼入统计数据，实际=" + calls.get(2).prompt());
        assertTrue(calls.get(2).schema().contains("weakPoints"), "薄弱项 schema 不完整");
        // 4) 推荐题目：courseId 必须拼入（该路径与 generateQuestion 不同，课程归属保留）
        assertEquals(0.8, calls.get(3).temperature());
        assertTrue(calls.get(3).prompt().contains("courseId=7"), "推荐题目须带课程归属，实际=" + calls.get(3).prompt());
        assertTrue(calls.get(3).prompt().contains("2道EASY难度"), "应带数量与难度，实际=" + calls.get(3).prompt());
        assertTrue(calls.get(3).schema().contains("\"difficulty\": \"EASY\""), "schema 应回显难度，实际=" + calls.get(3).schema());
        // 5) 回归：difficulty=null 时 prompt 与 schema 必须同步兜底为 MEDIUM，不得出现字面量 null
        assertEquals(0.8, calls.get(4).temperature());
        assertTrue(calls.get(4).schema().contains("\"difficulty\": \"MEDIUM\""), "schema 缺省难度应为 MEDIUM");
        assertTrue(calls.get(4).prompt().contains("2道MEDIUM难度"),
                "prompt 缺省难度应与 schema 一致，实际=" + calls.get(4).prompt());
        assertFalse(calls.get(4).prompt().contains("null"), "prompt 不得出现字面量 null，实际=" + calls.get(4).prompt());
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
    @DisplayName("analyzeWeakPoints(Map): 成功，学情统计序列化后进入 prompt")
    void analyzeWeakPoints_map() {
        when(llmProvider.completeJson(any(), any(), any()))
                .thenReturn("{\"weakPoints\":[{\"description\":\"d\",\"severity\":\"HIGH\"}]}");
        List<Map<String, String>> r = service.analyzeWeakPoints(Map.of("x", 1));
        assertEquals(1, r.size());
        JsonCall call = captureJsonCalls(1).get(0);
        assertEquals(0.4, call.temperature());
        assertTrue(call.prompt().contains("\"x\":1"), "Map 入参须序列化为 JSON 后下发，实际=" + call.prompt());
    }

    @Test
    @DisplayName("analyzeWeakPoints(Map): 异常 -> 降级提示")
    void analyzeWeakPoints_mapException() {
        // 异常注入桩：any() 合理，只关心「抛异常」这一事件
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
        // 两次调用：先薄弱项抽取(0.4)，再建议生成(0.6)，温度写错说明链路串错
        List<JsonCall> calls = captureJsonCalls(2);
        assertEquals(0.4, calls.get(0).temperature());
        assertEquals(0.6, calls.get(1).temperature());
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
        // 未配置供应商时三次调用点都应短路，一条计费请求都不许发出
        verify(llmProvider, never()).completeJson(any(), any(), any());
    }

    @Test
    @DisplayName("callLlm: 返回 null -> 通用错误文案 + 失败埋点")
    void callLlm_nullResult() {
        // null 注入桩：any() 合理，此处断言降级文案与失败埋点
        when(llmProvider.completeJson(any(), any(), any())).thenReturn(null);
        String r = service.recommendLearningJson("数学");
        assertTrue(r.contains("不可用") || r.contains("暂未配置"));
        assertEquals(1.0, meterRegistry.counter("aicourse_ai_calls_failed_total", "type", "recommend").count(),
                "空响应应计入 recommend 分桶的失败埋点");
    }

    @Test
    @DisplayName("completeAndSanitize: 调用异常 -> 失败埋点并重抛")
    void completeAndSanitize_failure() {
        when(llmProvider.complete(any(), any())).thenThrow(new RuntimeException("boom"));
        assertThrows(RuntimeException.class, () -> service.chat("hello", "sys", 1L));
        assertEquals(1.0, meterRegistry.counter("aicourse_ai_calls_failed_total", "type", "chat").count());
    }

    @Test
    @DisplayName("callLlm: completeJson 抛异常 -> 失败埋点并重抛")
    void callLlm_exception() {
        when(llmProvider.completeJson(any(), any(), any())).thenThrow(new RuntimeException("boom"));
        assertThrows(RuntimeException.class, () -> service.recommendLearningJson("数学"));
        assertEquals(1.0, meterRegistry.counter("aicourse_ai_calls_failed_total", "type", "recommend").count());
    }

    @Test
    @DisplayName("streamChat: 未配置 -> 直接返回提示 Flux")
    void streamChat_noProvider() {
        when(llmProvider.isConfigured()).thenReturn(false);
        reactor.core.publisher.Flux<String> f = service.streamChat("q");
        assertEquals(service.noProviderMessage(), f.blockFirst());
        verify(llmProvider, never()).stream(any(), any());
    }

    @Test
    @DisplayName("streamChat: 已配置 -> 流式 + 护栏，问题以 user 角色、温度 0.7 下发")
    void streamChat_configured() {
        reactor.core.publisher.Flux<String> f = service.streamChat("q");
        assertEquals("a", f.blockFirst());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> cap = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Double> tempCap = ArgumentCaptor.forClass(Double.class);
        verify(llmProvider).stream(cap.capture(), tempCap.capture());
        assertEquals(0.7, tempCap.getValue(), "流式温度应为 0.7");
        assertEquals(1, cap.getValue().size());
        assertEquals("user", cap.getValue().get(0).get("role"));
        assertEquals("q", cap.getValue().get(0).get("content"), "问题必须经中和后原样下发");
        verify(promptGuard).neutralizeInjection("q");
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
