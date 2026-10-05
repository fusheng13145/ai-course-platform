package com.aicourse.service.impl;

import com.aicourse.exception.BusinessException;
import com.aicourse.llm.LLMProvider;
import com.aicourse.service.RAGService;
import com.aicourse.service.component.ai.AiCoreComponent;
import com.aicourse.service.component.ai.AiDialogueComponent;
import com.aicourse.service.component.ai.AiRagComponent;
import com.aicourse.service.component.ai.AiStructuredComponent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AIServiceImpl 单元测试（纯 Mockito，无 Spring 上下文）。
 * <p>meterRegistry 必须用真实 SimpleMeterRegistry：源码内 Counter.builder(...).register(meterRegistry)
 * 在 mock 上会 NPE，真实注册表可正常 register/increment。</p>
 * <p>打桩原则：{@code llmProvider.complete/completeJson/stream} 收到的 messages/temperature/jsonSchema
 * 都是被测逻辑的产物（prompt 拼装、角色分配、埋点分桶），因此用 ArgumentCaptor 捕获后断言其结构内容，
 * 不再用 any() 放过「prompt 丢业务上下文 / 温度写错 / 埋点类型标错」这类缺陷。
 * 仅默认桩与异常注入桩保留 any()（那里参数不承载被测逻辑输出）。</p>
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
        // 原兼容构造器已移除：此处内联其组件组装逻辑，再走主构造器（4 个组件）
        AiCoreComponent coreComponent = new AiCoreComponent(llmProvider, promptGuard, meterRegistry);
        AiDialogueComponent dialogueComponent = new AiDialogueComponent(coreComponent);
        AiRagComponent ragComponent = new AiRagComponent(ragService, promptGuard, coreComponent);
        AiStructuredComponent structuredComponent = new AiStructuredComponent(coreComponent);
        service = new AIServiceImpl(dialogueComponent, ragComponent, structuredComponent, coreComponent);
        // 默认桩：已配置供应商，complete/completeJson 返回合法文本，输出清洗为原样透传。
        // 这里保留 any() 是合理的——它们是「让链路跑通」的兜底返回值，不承载被测逻辑的输出参数。
        lenient().when(llmProvider.isConfigured()).thenReturn(true);
        lenient().when(llmProvider.complete(any(), any())).thenReturn("raw");
        lenient().when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"summary\":\"s\",\"items\":[]}");
        lenient().when(llmProvider.stream(any(), any())).thenReturn(Flux.just("tok1", "tok2"));
        lenient().when(promptGuard.neutralizeInjection(anyString())).thenAnswer(i -> i.getArgument(0));
        lenient().when(promptGuard.sanitizeOutput(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ===================== 请求载荷捕获辅助 =====================

    /** 一次发往 LLM 的请求载荷（消息列表 + 温度 + JSON Schema），三者均为被测逻辑输出，需逐项断言。 */
    private record LlmRequest(List<Map<String, String>> messages, Double temperature, String schema) {
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> captureCompleteMessages(double expectedTemperature) {
        ArgumentCaptor<List<Map<String, String>>> cap = ArgumentCaptor.forClass(List.class);
        verify(llmProvider).complete(cap.capture(), eq(expectedTemperature));
        return cap.getValue();
    }

    @SuppressWarnings("unchecked")
    private LlmRequest captureJsonRequest() {
        List<LlmRequest> all = captureAllJsonRequests();
        assertEquals(1, all.size(), "completeJson 应恰好被调用一次");
        return all.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<LlmRequest> captureAllJsonRequests() {
        ArgumentCaptor<List<Map<String, String>>> msgs = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Double> temp = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<String> schema = ArgumentCaptor.forClass(String.class);
        verify(llmProvider, atLeastOnce()).completeJson(msgs.capture(), temp.capture(), schema.capture());
        List<LlmRequest> out = new ArrayList<>();
        for (int i = 0; i < msgs.getAllValues().size(); i++) {
            out.add(new LlmRequest(msgs.getAllValues().get(i), temp.getAllValues().get(i), schema.getAllValues().get(i)));
        }
        return out;
    }

    /** 取 user 角色内容拼成的 prompt（buildMessages 生成的载荷只有一条 user 消息）。 */
    private static String promptOf(List<Map<String, String>> msgs) {
        return msgs.stream()
                .filter(m -> "user".equals(m.get("role")))
                .map(m -> String.valueOf(m.get("content")))
                .collect(Collectors.joining("\n"));
    }

    /** 全部角色的 role=content 拼接，用于断言 system 上下文是否真的下发。 */
    private static String flatten(List<Map<String, String>> msgs) {
        return msgs.stream().map(m -> m.get("role") + "=" + m.get("content")).collect(Collectors.joining("|"));
    }

    /** 按业务类型读取 AI 调用埋点计数（tag=type），用于断言埋点分桶没有被标错。 */
    private double aiCallCount(String type) {
        return meterRegistry.counter("aicourse_ai_calls_total", "type", type).count();
    }

    // ===================== chat =====================
    @Test
    @DisplayName("chat(String) 正常路径：上下文以 system、输入以 user 下发，温度 0.7，埋点 type=chat")
    void chat_str_ok() {
        String r = service.chat("你好", "sys", 1L);
        assertEquals("raw", r);
        List<Map<String, String>> msgs = captureCompleteMessages(0.7);
        // 业务上下文必须作为 system 角色存在：若退化成拼进 user 文本或直接丢弃，「带上下文的对话」形同虚设
        assertEquals(2, msgs.size(), "实际下发=" + flatten(msgs));
        assertEquals("system", msgs.get(0).get("role"));
        assertEquals("sys", msgs.get(0).get("content"));
        assertEquals("user", msgs.get(1).get("role"));
        assertEquals("你好", msgs.get(1).get("content"));
        assertEquals(1.0, aiCallCount("chat"));
    }

    @Test
    @DisplayName("chat(List) 空消息返回降级文案")
    void chat_list_empty() {
        assertEquals(AIServiceImplTest.SERVICE_ERROR_MSG(), service.chat(List.of(), 1L));
        // 空历史必须在门面处短路，绝不能带着空 messages 去调用供应商
        verify(llmProvider, never()).complete(any(), any());
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
        List<Map<String, String>> msgs = captureCompleteMessages(0.7);
        // 多轮历史要原条数透传（不能被压成单条），且注入中和后的内容仍是原始 user 内容
        assertEquals(1, msgs.size(), "实际下发=" + flatten(msgs));
        assertEquals("user", msgs.get(0).get("role"));
        assertEquals("hi", msgs.get(0).get("content"));
    }

    // ===================== generate* =====================
    @Test
    @DisplayName("generateQuestion 正常：课程/题量/难度/题型进入 prompt，温度 0.9，埋点 type=question")
    void generateQuestion_ok() {
        assertEquals("raw", service.generateQuestion(4721L, "single", "easy", 3));
        String prompt = promptOf(captureCompleteMessages(0.9));
        assertTrue(prompt.contains("3道"), "题量必须进入 prompt，实际=" + prompt);
        assertTrue(prompt.contains("easy难度"), "难度必须进入 prompt，实际=" + prompt);
        assertTrue(prompt.contains("类型为single"), "题型必须进入 prompt，实际=" + prompt);
        assertTrue(prompt.contains("JSON"), "应要求以 JSON 返回，实际=" + prompt);
        // 回归：courseId 必须进入 prompt，否则 AI 无从知道题目属于哪门课程，跨课程出题语境丢失
        assertTrue(prompt.contains("4721"), "courseId 必须进入 prompt，实际=" + prompt);
        assertEquals(1.0, aiCallCount("question"));
    }

    @Test
    @DisplayName("recommendQuestionsJson difficulty 为 null：兜底 MEDIUM，prompt 不出现字面量 null")
    void recommendQuestionsJson_nullDifficulty() {
        service.recommendQuestionsJson(9L, null, 3);
        LlmRequest req = captureJsonRequest();
        String prompt = promptOf(req.messages());
        assertTrue(prompt.contains("MEDIUM难度"), "难度为空应兜底 MEDIUM，实际=" + prompt);
        assertTrue(prompt.contains("courseId=9"), "课程上下文必须保留，实际=" + prompt);
        assertTrue(req.schema().contains("MEDIUM"), "schema 的难度示例应同步兜底");
        assertEquals(0.8, req.temperature());
    }

    @Test
    @DisplayName("recommendQuestionsJson courseId 为 null：省略课程限定语而不是拼出 null")
    void recommendQuestionsJson_nullCourse() {
        service.recommendQuestionsJson(null, "HARD", 2);
        String prompt = promptOf(captureJsonRequest().messages());
        assertFalse(prompt.contains("null"), "prompt 不得出现字面量 null，实际=" + prompt);
        assertTrue(prompt.contains("2道HARD难度"), "实际=" + prompt);
    }

    @Test
    @DisplayName("generateContent correct 分支：批改老师角色 + 待处理文本进入 prompt")
    void generateContent_correct() {
        String r = service.generateContent("correct", "文本");
        assertTrue(r.contains("raw"));
        String prompt = promptOf(captureCompleteMessages(0.7));
        assertTrue(prompt.contains("批改老师"), "correct 分支必须使用批改角色，实际=" + prompt);
        assertTrue(prompt.contains("错别字"), "应指明纠错维度，实际=" + prompt);
        assertTrue(prompt.contains("文本内容：\n文本"), "待处理文本必须拼入 prompt，实际=" + prompt);
    }

    @Test
    @DisplayName("generateContent polish 分支：润色角色 + 保留 Markdown 约定 + 文本进入 prompt")
    void generateContent_polish() {
        String r = service.generateContent("polish", "文本");
        assertTrue(r.contains("raw"));
        String prompt = promptOf(captureCompleteMessages(0.7));
        assertTrue(prompt.contains("润色"), "polish 分支必须是润色指令，实际=" + prompt);
        assertTrue(prompt.contains("Markdown"), "应要求保留原有 Markdown 结构，实际=" + prompt);
        assertTrue(prompt.contains("文本内容：\n文本"), "待处理文本必须拼入 prompt，实际=" + prompt);
        // 三分支必须可区分：润色不能复用批改老师的模板
        assertFalse(prompt.contains("批改老师"), "polish 与 correct 模板混淆，实际=" + prompt);
    }

    @Test
    @DisplayName("generateContent 其它分支：type 与 params 直接拼入 prompt")
    void generateContent_else() {
        String r = service.generateContent("summary", "参数");
        assertTrue(r.contains("raw"));
        String prompt = promptOf(captureCompleteMessages(0.7));
        assertTrue(prompt.contains("请生成summary内容，参数"), "默认分支应拼接 type+params，实际=" + prompt);
    }

    @Test
    @DisplayName("generateSummary 正常：原文进入 prompt，温度 0.3，埋点 type=content")
    void generateSummary_ok() {
        assertEquals("raw", service.generateSummary("内容"));
        String prompt = promptOf(captureCompleteMessages(0.3));
        assertTrue(prompt.contains("请总结以下内容："), "应显式声明摘要任务，实际=" + prompt);
        assertTrue(prompt.contains("内容"), "待总结正文必须拼入 prompt，实际=" + prompt);
        assertEquals(1.0, aiCallCount("content"));
    }

    @Test
    @DisplayName("checkPlagiarism 正常：两段文本分别带标签进入 prompt，温度 0.2")
    void checkPlagiarism_ok() {
        assertEquals("raw", service.checkPlagiarism("a", "b"));
        String prompt = promptOf(captureCompleteMessages(0.2));
        assertTrue(prompt.contains("相似度"), "应声明相似度比对任务，实际=" + prompt);
        // 两段文本必须可区分（各自带序号标签），否则模型收到的是 concatenated 单段
        assertTrue(prompt.contains("文本1：a"), "第一段应带标签拼入，实际=" + prompt);
        assertTrue(prompt.contains("文本2：b"), "第二段应带标签拼入，实际=" + prompt);
        // 回归：查重必须落在独立的 plagiarism 桶，否则 Grafana 按类型下钻时查重量混入批改统计
        assertEquals(0.0, aiCallCount("correct"), "批改桶不得被查重调用污染");
        assertEquals(1.0, aiCallCount("plagiarism"), "查重埋点 type 应为 plagiarism");
    }

    // ===================== ragAsk =====================
    @Test
    @DisplayName("ragAsk RAG 返回 null 降级纯 LLM（降级 prompt 必须保留课程语境）")
    void ragAsk_ragNull() {
        // topK=5 与 courseId 都是 AiRagComponent 硬编码/透传的业务参数，必须按值打桩而非 anyInt()
        when(ragService.ask("q", 1L, 5)).thenReturn(null);
        assertEquals("raw", service.ragAsk("q", 1L));
        List<Map<String, String>> msgs = captureCompleteMessages(0.5);
        assertTrue(flatten(msgs).contains("请基于课程知识回答问题"), "降级路径应带课程语境 system，实际=" + flatten(msgs));
        assertTrue(promptOf(msgs).contains("q"), "原始问题必须透传给降级 LLM，实际=" + flatten(msgs));
        assertEquals(1.0, aiCallCount("rag"));
    }

    @Test
    @DisplayName("ragAsk RAG 含 503 降级纯 LLM")
    void ragAsk_rag503() {
        when(ragService.ask("q", 1L, 5)).thenReturn("{\"code\":503,\"msg\":\"x\"}");
        assertEquals("raw", service.ragAsk("q", 1L));
        assertEquals(1.0, aiCallCount("rag"));
    }

    @Test
    @DisplayName("ragAsk RAG 正常返回答案：检索参数为(问题, 课程ID, topK=5)")
    void ragAsk_ragOk() {
        when(ragService.ask("q", 1L, 5)).thenReturn("{\"answer\":\"hello\"}");
        assertEquals("hello", service.ragAsk("q", 1L));
        // 检索召回条数决定答案的证据密度，写错 topK 不会改变类型却会改变结果，必须按值校验
        verify(ragService).ask("q", 1L, 5);
        verify(llmProvider, never()).complete(any(), any());
    }

    @Test
    @DisplayName("ragAsk RAG 抛异常降级")
    void ragAsk_ragThrows() {
        when(ragService.ask("q", 1L, 5)).thenThrow(new RuntimeException("boom"));
        assertEquals("raw", service.ragAsk("q", 1L));
        assertEquals(1.0, aiCallCount("rag"));
    }

    // ===================== recommend* =====================
    @Test
    @DisplayName("recommendLearningJson 已配置供应商：课程列表进入 prompt，schema 约束 recommendations")
    void recommendLearningJson_ok() {
        when(llmProvider.completeJson(any(), any(), any()))
                .thenReturn("{\"recommendations\":[{\"resource\":\"r\",\"reason\":\"why\",\"priority\":\"HIGH\"}]}");
        String r = service.recommendLearningJson("数学");
        assertTrue(r.contains("recommendations"));
        LlmRequest req = captureJsonRequest();
        assertEquals(0.7, req.temperature());
        assertTrue(promptOf(req.messages()).contains("我目前在学习以下课程：数学"),
                "课程列表必须拼入 prompt，实际=" + promptOf(req.messages()));
        assertTrue(req.schema().contains("recommendations"), "schema 应约束 recommendations 字段");
        assertTrue(req.schema().contains("resource") && req.schema().contains("reason"), "schema 字段不完整");
        assertEquals(1.0, aiCallCount("recommend"));
    }

    @Test
    @DisplayName("recommendLearningJson 未配置供应商返回提示")
    void recommendLearningJson_notConfigured() {
        when(llmProvider.isConfigured()).thenReturn(false);
        assertTrue(service.recommendLearningJson("数学").contains("暂未配置"));
        // 未配置供应商时不得发起真实调用（避免把 NO_PROVIDER 路径污染成计费调用）
        verify(llmProvider, never()).completeJson(any(), any(), any());
    }

    @Test
    @DisplayName("recommendLearningWithContext 含薄弱项：学情画像/薄弱项名称与掌握度进入 prompt")
    void recommendLearningWithContext_withWeak() {
        Map<String, Object> ctx = new java.util.LinkedHashMap<>();
        ctx.put("weakPoints", List.of(Map.of("name", "函数", "mastery", 30)));
        String r = service.recommendLearningWithContext(ctx);
        assertNotNull(r);
        LlmRequest req = captureJsonRequest();
        assertEquals(0.6, req.temperature());
        String prompt = promptOf(req.messages());
        assertTrue(prompt.contains("【学生学情画像】"), "应声明学情画像段落，实际=" + prompt);
        assertTrue(prompt.contains("函数"), "薄弱知识点名称必须进入 prompt，实际=" + prompt);
        assertTrue(prompt.contains("掌握度：30"), "掌握度数值必须随薄弱项下发，实际=" + prompt);
        assertTrue(prompt.contains("薄弱知识点"), "应声明薄弱项小节，实际=" + prompt);
        assertTrue(req.schema().contains("priority"), "推荐结果需带优先级 schema");
    }

    @Test
    @DisplayName("recommendLearningWithContext 无薄弱项：走「暂无薄弱项」文案并透传进度")
    void recommendLearningWithContext_noWeak() {
        String r = service.recommendLearningWithContext(Map.of("progress", 50));
        assertNotNull(r);
        String prompt = promptOf(captureJsonRequest().messages());
        assertTrue(prompt.contains("暂无薄弱项数据"), "无薄弱项时应给出对应说明，实际=" + prompt);
        assertTrue(prompt.contains("课程平均进度：50%"), "进度必须带百分号语义下发，实际=" + prompt);
    }

    // ===================== extractKnowledgeGraphJson =====================
    @Test
    @DisplayName("extractKnowledgeGraphJson 阶段一为空降级单阶段：单阶段 prompt/schema 结构校验")
    @SuppressWarnings("unchecked")
    void extractGraph_stage1blank() {
        when(llmProvider.complete(any(), eq(0.3))).thenReturn("");
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"nodes\":[],\"relations\":[]}");
        String r = service.extractKnowledgeGraphJson("内容");
        // 真实断言：阶段一（阅读理解）返回空白时，服务应直接采用降级单阶段的抽取结果
        assertNotNull(r);
        assertEquals("{\"nodes\":[],\"relations\":[]}", r, "应返回降级单阶段抽取到的 JSON 原文");
        // 阶段一被调用一次；因空白只触发一次抽取（completeJson），不得再进入阶段二产生第二次抽取
        ArgumentCaptor<List<Map<String, String>>> stage1Cap = ArgumentCaptor.forClass(List.class);
        verify(llmProvider, times(1)).complete(stage1Cap.capture(), eq(0.3));
        String stage1Prompt = promptOf(stage1Cap.getValue());
        assertTrue(stage1Prompt.contains("你是一位正在学习以下课程内容的学生"), "阶段一应以学习者视角发起，实际=" + stage1Prompt);
        assertTrue(stage1Prompt.contains("内容"), "阶段一原文必须拼入 prompt，实际=" + stage1Prompt);
        verify(llmProvider, times(1)).completeJson(any(), any(), any());

        LlmRequest req = captureJsonRequest();
        assertEquals(0.3, req.temperature());
        String prompt = promptOf(req.messages());
        assertTrue(prompt.contains("抽取知识结构"), "降级单阶段应声明抽取目标，实际=" + prompt);
        assertTrue(prompt.contains("内容"), "降级单阶段仍须携带原文，实际=" + prompt);
        assertTrue(req.schema().contains("nodes") && req.schema().contains("relations"), "抽取 schema 不完整");
        assertEquals(2.0, aiCallCount("extract_graph"), "阶段一 + 降级抽取共 2 次埋点");
    }

    @Test
    @DisplayName("extractKnowledgeGraphJson 两阶段：阶段二 prompt 必须嵌入阶段一理解摘要与 schema")
    @SuppressWarnings("unchecked")
    void extractGraph_stage1ok() {
        when(llmProvider.complete(any(), eq(0.3))).thenReturn("阅读摘要");
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("{\"nodes\":[],\"relations\":[]}");
        String r = service.extractKnowledgeGraphJson("内容");
        assertNotNull(r);
        ArgumentCaptor<List<Map<String, String>>> stage1Cap = ArgumentCaptor.forClass(List.class);
        verify(llmProvider, times(1)).complete(stage1Cap.capture(), eq(0.3));
        // 阶段一 prompt 丢了原文，摘要就是凭空编造，整条两阶段流水线失去意义
        assertTrue(promptOf(stage1Cap.getValue()).contains("内容"), "阶段一须携带待抽取原文");
        verify(llmProvider).completeJson(any(), any(), any());

        LlmRequest req = captureJsonRequest();
        assertEquals(0.3, req.temperature());
        String prompt = promptOf(req.messages());
        assertTrue(prompt.contains("你是一位领域专家"), "阶段二应以专家视角归纳，实际=" + prompt);
        // IPA 两阶段的核心：阶段一产出必须作为上下文喂给阶段二，否则第二阶段退化成单阶段抽取
        assertTrue(prompt.contains("阅读摘要"), "阶段一结果必须嵌入阶段二 prompt，实际=" + prompt);
        assertTrue(prompt.contains("PREREQUISITE"), "阶段二 prompt 应内联 schema 约束关系类型，实际=" + prompt);
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
    @DisplayName("analyzeWeakPoints 正常解析：学情统计 JSON 被序列化进 prompt")
    void analyzeWeakPoints_ok() {
        when(llmProvider.completeJson(any(), any(), any()))
                .thenReturn("{\"weakPoints\":[{\"description\":\"d\",\"severity\":\"HIGH\"}]}");
        List<Map<String, String>> r = service.analyzeWeakPoints(Map.of("a", 1));
        assertEquals(1, r.size());
        assertEquals("HIGH", r.get(0).get("severity"));

        LlmRequest req = captureJsonRequest();
        assertEquals(0.4, req.temperature());
        String prompt = promptOf(req.messages());
        assertTrue(prompt.contains("分析学习者的知识薄弱项"), "应声明薄弱项分析任务，实际=" + prompt);
        // userStats 是方法入参，必须先序列化成 JSON 再喂给模型；丢了它模型只能凭空编造薄弱项
        assertTrue(prompt.contains("\"a\":1"), "学情统计必须序列化进 prompt，实际=" + prompt);
        assertTrue(req.schema().contains("weakPoints"), "schema 应约束 weakPoints 字段");
        assertEquals(1.0, aiCallCount("analyze"));
    }

    @Test
    @DisplayName("analyzeWeakPoints 调用失败降级")
    void analyzeWeakPoints_fallback() {
        // 异常注入桩：any() 合理——此处只关心「调用抛异常」这一事件，不依赖具体参数
        when(llmProvider.completeJson(any(), any(), any())).thenThrow(new RuntimeException("boom"));
        List<Map<String, String>> r = service.analyzeWeakPoints(Map.of("a", 1));
        assertEquals(1, r.size());
        assertEquals("AI分析暂时不可用，请稍后重试", r.get(0).get("description"));
    }

    @Test
    @DisplayName("generateLearningAdvice 正常解析：两次抽取串联，薄弱项结果必须喂进建议 prompt")
    void generateLearningAdvice_ok() {
        String weakJson = "{\"weakPoints\":[{\"description\":\"d\",\"severity\":\"HIGH\"}]}";
        when(llmProvider.completeJson(any(), any(), any()))
                .thenReturn(weakJson, "{\"summary\":\"总体建议\",\"items\":[{\"title\":\"t\",\"detail\":\"d\",\"level\":\"HIGH\"}]}");
        Map<String, Object> r = service.generateLearningAdvice(Map.of("a", 1));
        assertEquals("总体建议", r.get("summary"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) r.get("items");
        assertEquals(1, items.size());
        assertEquals("HIGH", items.get(0).get("level"));

        List<LlmRequest> reqs = captureAllJsonRequests();
        assertEquals(2, reqs.size(), "应先是薄弱项抽取、再是建议生成两次调用");
        // 第一次：薄弱项分析（温度 0.4），prompt 带学情统计
        assertEquals(0.4, reqs.get(0).temperature());
        assertTrue(promptOf(reqs.get(0).messages()).contains("\"a\":1"),
                "第一次调用应携带序列化后的学情统计，实际=" + promptOf(reqs.get(0).messages()));
        // 第二次：建议生成（温度 0.6），prompt 同时带学情统计与第一次的薄弱项产出
        assertEquals(0.6, reqs.get(1).temperature());
        String advicePrompt = promptOf(reqs.get(1).messages());
        assertTrue(advicePrompt.contains("\"a\":1"), "建议生成应再次携带学情统计，实际=" + advicePrompt);
        assertTrue(advicePrompt.contains(weakJson), "建议生成必须把上一步薄弱项结果传入，实际=" + advicePrompt);
        assertTrue(reqs.get(1).schema().contains("summary") && reqs.get(1).schema().contains("items"),
                "建议 schema 应约束 summary/items");
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
        // 空响应既要降级也要计入失败埋点，否则失败率看板会低估故障面
        assertEquals(1.0, meterRegistry.counter("aicourse_ai_calls_failed_total", "type", "recommend").count());
    }

    // ===================== streamChat =====================
    @Test
    @DisplayName("streamChat 未配置返回提示")
    void streamChat_notConfigured() {
        when(llmProvider.isConfigured()).thenReturn(false);
        Flux<String> f = service.streamChat("q");
        assertEquals(service.noProviderMessage(), f.blockFirst());
        verify(llmProvider, never()).stream(any(), any());
    }

    @Test
    @DisplayName("streamChat 已配置返回流式 token，且问题以 user 角色、温度 0.7 下发")
    void streamChat_configured() {
        when(llmProvider.isConfigured()).thenReturn(true);
        Flux<String> f = service.streamChat("q");
        assertEquals("tok1", f.blockFirst());
        assertEquals(2, f.collectList().block().size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> cap = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Double> tempCap = ArgumentCaptor.forClass(Double.class);
        verify(llmProvider).stream(cap.capture(), tempCap.capture());
        assertEquals(0.7, tempCap.getValue(), "流式对话温度应为 0.7");
        assertEquals(1, cap.getValue().size());
        assertEquals("user", cap.getValue().get(0).get("role"));
        assertEquals("q", cap.getValue().get(0).get("content"), "问题必须原样下发给流式接口");
    }

    // ===================== completeAndSanitize 异常 =====================
    @Test
    @DisplayName("completeAndSanitize 异常抛出并记失败")
    void completeAndSanitize_exception() {
        // 异常注入桩：any() 合理，此处断言的是异常传播与失败埋点，不依赖具体参数
        when(llmProvider.complete(any(), any())).thenThrow(new RuntimeException("boom"));
        assertThrows(RuntimeException.class, () -> service.chat("hi", "sys", 1L));
        assertEquals(1.0, meterRegistry.counter("aicourse_ai_calls_failed_total", "type", "chat").count());
    }
}
