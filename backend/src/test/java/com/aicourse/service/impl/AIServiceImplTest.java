package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.exception.BusinessException;
import com.aicourse.integration.ContractPaths;
import com.aicourse.integration.rag.RagContract.RagRetrievalResult;
import com.aicourse.integration.rag.RagContract.RagSource;
import com.aicourse.vo.QaAnswerVO;
import com.aicourse.exception.LLMUnavailableException;
import com.aicourse.llm.LLMProvider;
import com.aicourse.service.RAGService;
import com.aicourse.service.component.ai.AiCoreComponent;
import com.aicourse.service.component.ai.AiDialogueComponent;
import com.aicourse.service.component.ai.AiRagComponent;
import com.aicourse.service.component.ai.AiStructuredComponent;
import com.aicourse.service.component.ai.CourseEvidenceComponent;
import com.aicourse.vo.QaSourceVO;
import com.aicourse.vo.QaStreamVO;
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
    /** 落库服务桩：本测试不设 AiCallContext 作用域，故不会有任何 record 交互。 */
    @Mock
    private com.aicourse.service.AiCallLogService aiCallLogService;
    /** 降级接地桩：默认返回空课程知识点，具体用例按需打桩。 */
    @Mock
    private com.aicourse.service.KnowledgePointService knowledgePointService;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private AIServiceImpl service;

    @BeforeEach
    void setUp() {
        // 原兼容构造器已移除：此处内联其组件组装逻辑，再走主构造器（4 个组件）
        AiCoreComponent coreComponent = new AiCoreComponent(llmProvider, promptGuard, meterRegistry, aiCallLogService);
        CourseEvidenceComponent evidenceComponent =
                new CourseEvidenceComponent(ragService, knowledgePointService);
        AiDialogueComponent dialogueComponent = new AiDialogueComponent(coreComponent, ragService, evidenceComponent);
        AiRagComponent ragComponent = new AiRagComponent(evidenceComponent, coreComponent);
        AiStructuredComponent structuredComponent = new AiStructuredComponent(coreComponent, evidenceComponent);
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
    @DisplayName("chat(List) 空历史按入参错误失败，不再冒充上游降级文案")
    void chat_list_empty() {
        // 旧行为：空历史也返回 "AI 服务暂时不可用，请稍后再试。"——与真的 401 同形，
        // 用户和排障者都分不清"你没带历史"和"大模型挂了"（DR-74 语义分名）。
        BusinessException ex = assertThrows(BusinessException.class, () -> service.chat(List.of(), 1L));
        assertEquals(ApiCode.BAD_REQUEST, ex.getCode());
        // 空历史必须在门面处短路，绝不能带着空 messages 去调用供应商
        verify(llmProvider, never()).complete(any(), any());
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

    // ===================== ragAsk（Java 单一路径：引擎检索 mode=ENGINE + 本端 LLM 生成） =====================

    private static RagRetrievalResult engineResult(RagSource... sources) {
        return new RagRetrievalResult(List.of(sources), ContractPaths.MODE_ENGINE, null, null);
    }

    private static RagRetrievalResult unavailableRetrieval() {
        return new RagRetrievalResult(null, ContractPaths.MODE_UNAVAILABLE,
                ContractPaths.MSG_RAG_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE);
    }

    @Test
    @DisplayName("ragAsk 检索返回 null 降级：无知识点时显式声明课程知识库为空，mode=LOCAL")
    void ragAsk_retrievalNull() {
        // topK=5 与 courseId 都是 AiRagComponent 硬编码/透传的业务参数，必须按值打桩而非 anyInt()
        when(ragService.retrieveTyped("q", 1L, 5)).thenReturn(null);
        QaAnswerVO vo = service.ragAsk("q", 1L);
        assertEquals("raw", vo.getAnswer());
        assertEquals(ContractPaths.MODE_LOCAL, vo.getMode(), "无引擎证据时不得自称 ENGINE");
        List<Map<String, String>> msgs = captureCompleteMessages(0.5);
        assertTrue(flatten(msgs).contains("当前课程知识库暂无知识点"),
                "降级路径须声明无课程知识库，实际=" + flatten(msgs));
        assertTrue(flatten(msgs).contains("无法基于课程知识库回答"), "降级路径须给出如实兜底措辞");
        assertTrue(promptOf(msgs).contains("q"), "原始问题必须透传给降级 LLM，实际=" + flatten(msgs));
        assertEquals(1.0, aiCallCount("rag"));
        verify(ragService, never()).ask(anyString(), any(), anyInt());
    }

    @Test
    @DisplayName("ragAsk 检索 503 降级纯 LLM，mode=LOCAL")
    void ragAsk_retrievalUnavailable() {
        when(ragService.retrieveTyped("q", 1L, 5)).thenReturn(unavailableRetrieval());
        QaAnswerVO vo = service.ragAsk("q", 1L);
        assertEquals("raw", vo.getAnswer());
        assertEquals(ContractPaths.MODE_LOCAL, vo.getMode());
        assertEquals(1.0, aiCallCount("rag"));
    }

    @Test
    @DisplayName("ragAsk 引擎召回 mode=ENGINE：本端 LLM 依资料生成答案，sources 含文档与图谱节点")
    void ragAsk_engineAnswerUsesRetrievedEvidence() {
        KnowledgePoint closure = knowledgePoint(1L, "闭包", "闭包是由函数及其引用的环境组合而成的实体", 5);
        when(knowledgePointService.listByCourse(1L)).thenReturn(List.of(closure));
        when(ragService.retrieveTyped("什么是闭包", 1L, 5)).thenReturn(engineResult(
                new RagSource(9L, "Python-函数与lambda", 0.93, "lambda 是表达式，def 是语句")));

        QaAnswerVO vo = service.ragAsk("什么是闭包", 1L);

        assertEquals("raw", vo.getAnswer());
        assertEquals(ContractPaths.MODE_ENGINE, vo.getMode());
        assertEquals(2, vo.getSources().size(), "出处应同时包含检索文档与图谱命中节点");
        assertEquals(9L, vo.getSources().get(0).getDocumentId());
        assertEquals("课程知识图谱 · 闭包", vo.getSources().get(1).getDocumentName());

        List<Map<String, String>> msgs = captureCompleteMessages(0.3);
        String prompt = flatten(msgs);
        assertTrue(prompt.contains("lambda 是表达式，def 是语句"), "召回片段必须进入生成 prompt，实际=" + prompt);
        assertTrue(prompt.contains("闭包是由函数及其引用的环境组合而成的实体"),
                "命中知识点的描述必须进入生成 prompt，实际=" + prompt);
        assertTrue(prompt.contains("无法基于课程知识库回答"), "接地 prompt 必须带如实兜底硬约束");
        // 删除字符串嗅探后，主路径不得再调用 Python 的 /ask（那里会用 EXTRACTIVE 模板冒充生成）
        verify(ragService, never()).ask(anyString(), any(), anyInt());
        assertEquals(1.0, aiCallCount("rag"));
    }

    @Test
    @DisplayName("ragAsk 检索 mode=PLACEHOLDER：CI 占位桩不得冒充引擎答案，降级且 mode=LOCAL")
    void ragAsk_placeholderModeFallsBack() {
        when(ragService.retrieveTyped("q", 1L, 5)).thenReturn(
                new RagRetrievalResult(List.of(new RagSource(1L, "TCP", 0.9, "占位片段")), "PLACEHOLDER", null, null));

        QaAnswerVO vo = service.ragAsk("q", 1L);

        assertEquals("raw", vo.getAnswer());
        assertEquals(ContractPaths.MODE_LOCAL, vo.getMode(), "占位召回不得被当作引擎证据");
        assertTrue(vo.getSources().isEmpty(), "占位召回不得进入出处");
        String prompt = flatten(captureCompleteMessages(0.5));
        assertTrue(prompt.contains("当前课程知识库暂无知识点"), "实际=" + prompt);
    }

    @Test
    @DisplayName("ragAsk 检索抛异常降级纯 LLM")
    void ragAsk_retrievalThrows() {
        when(ragService.retrieveTyped("q", 1L, 5)).thenThrow(new RuntimeException("boom"));
        assertEquals("raw", service.ragAsk("q", 1L).getAnswer());
        assertEquals(1.0, aiCallCount("rag"));
    }

    @Test
    @DisplayName("ragAsk 降级接地：命中知识点与图谱前置关系进入 prompt，sources 可溯源")
    void ragAsk_fallbackGroundsOnKnowledgePoints() {
        KnowledgePoint closure = knowledgePoint(1L, "闭包",
                "闭包是由函数及其引用的环境组合而成的实体", 5);
        KnowledgePoint recursion = knowledgePoint(2L, "递归", "函数调用自身", 3);
        KnowledgePoint scope = knowledgePoint(3L, "作用域", "变量的可见范围", 4);
        when(knowledgePointService.listByCourse(1L)).thenReturn(List.of(recursion, closure));
        when(knowledgePointService.getPrerequisites(1L)).thenReturn(List.of(scope));
        when(ragService.retrieveTyped("什么是闭包", 1L, 5)).thenReturn(unavailableRetrieval());

        QaAnswerVO vo = service.ragAsk("什么是闭包", 1L);
        assertEquals("raw", vo.getAnswer());
        assertEquals(ContractPaths.MODE_LOCAL, vo.getMode());
        assertEquals(1, vo.getSources().size(), "只有问题命中的知识点才能作为出处");
        assertEquals("课程知识图谱 · 闭包", vo.getSources().get(0).getDocumentName());
        assertEquals(1L, vo.getSources().get(0).getDocumentId());

        String prompt = flatten(captureCompleteMessages(0.5));
        assertTrue(prompt.contains("闭包"), "命中知识点必须进入 prompt，实际=" + prompt);
        assertTrue(prompt.contains("前置知识") && prompt.contains("作用域"),
                "图谱前置关系必须进入 prompt，实际=" + prompt);
        assertTrue(prompt.contains("无法基于课程知识库回答"), "接地 prompt 必须带如实兜底硬约束");
    }

    @Test
    @DisplayName("ragAsk 降级无命中：只给课程范围、sources 为空，并明确未检索到相关知识点")
    void ragAsk_fallbackNoMatchKeepsHonestScope() {
        when(knowledgePointService.listByCourse(1L)).thenReturn(List.of(
                knowledgePoint(1L, "继承", "子类复用父类", 3)));
        when(ragService.retrieveTyped("今天天气", 1L, 5)).thenReturn(null);

        QaAnswerVO vo = service.ragAsk("今天天气", 1L);
        assertEquals("raw", vo.getAnswer());
        assertEquals(ContractPaths.MODE_LOCAL, vo.getMode());
        assertTrue(vo.getSources().isEmpty(), "无直接命中不得伪造出处");
        String prompt = flatten(captureCompleteMessages(0.5));
        assertTrue(prompt.contains("未检索到与问题直接相关的课程知识点"), "实际=" + prompt);
        assertTrue(prompt.contains("不得编造"));
    }

    @Test
    @DisplayName("ragAsk 未指定课程：显式声明无课程知识库，且不发起检索、不读取任何知识点")
    void ragAsk_fallbackWithoutCourseDeclaresNoKnowledgeBase() {
        QaAnswerVO vo = service.ragAsk("q", null);
        assertEquals("raw", vo.getAnswer());
        assertEquals(ContractPaths.MODE_LOCAL, vo.getMode());
        assertTrue(vo.getSources().isEmpty());
        String prompt = flatten(captureCompleteMessages(0.5));
        assertTrue(prompt.contains("当前未指定课程"), "实际=" + prompt);
        assertTrue(prompt.contains("无法基于课程知识库回答"));
        verify(knowledgePointService, never()).listByCourse(any());
        verify(ragService, never()).retrieveTyped(anyString(), any(), anyInt());
    }

    private KnowledgePoint knowledgePoint(Long id, String name, String description, int importance) {
        KnowledgePoint point = new KnowledgePoint();
        point.setId(id);
        point.setName(name);
        point.setDescription(description);
        point.setImportance(importance);
        return point;
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
        // 文案仍要说"暂未配置"，但通道必须是失败（503）而不是"看起来像回答"
        LLMUnavailableException ex = assertThrows(LLMUnavailableException.class,
                () -> service.recommendLearningJson("数学"));
        assertEquals(LLMUnavailableException.Kind.NOT_CONFIGURED, ex.kind());
        assertTrue(ex.getMessage().contains("暂未配置"), ex.getMessage());
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
    @DisplayName("generateLearningAdvice 空响应抛 503(EMPTY_RESPONSE)")
    void generateLearningAdvice_emptyRaw() {
        when(llmProvider.completeJson(any(), any(), any())).thenReturn("");
        LLMUnavailableException ex = assertThrows(LLMUnavailableException.class,
                () -> service.generateLearningAdvice(Map.of("a", 1)));
        assertEquals(ApiCode.SERVICE_UNAVAILABLE, ex.getCode());
        assertEquals(LLMUnavailableException.Kind.EMPTY_RESPONSE, ex.kind());
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
    @DisplayName("callLlm 拿到空响应抛 503，不再把降级文案当结果返回")
    void callLlm_nullResult() {
        when(llmProvider.completeJson(any(), any(), any())).thenReturn(null);
        // recommendLearningJson 在已配置时走 callLlm；completeJson 返回 null → 失败必须是失败
        LLMUnavailableException ex = assertThrows(LLMUnavailableException.class,
                () -> service.recommendLearningJson("数学"));
        assertEquals(LLMUnavailableException.Kind.EMPTY_RESPONSE, ex.kind());
        assertEquals(ApiCode.SERVICE_UNAVAILABLE, ex.getCode());
        // 空响应既要报错也要计入失败埋点，否则失败率看板会低估故障面
        assertEquals(1.0, meterRegistry.counter("aicourse_ai_calls_failed_total", "type", "recommend").count());
    }

    // ===================== streamChat =====================
    @Test
    @DisplayName("streamChat 未配置返回提示，且不出处")
    void streamChat_notConfigured() {
        when(llmProvider.isConfigured()).thenReturn(false);
        // 未配 Key 时不许"播出一个假装是回答的首帧"：同步抛 503，SSE 不开播
        LLMUnavailableException ex = assertThrows(LLMUnavailableException.class,
                () -> service.streamChat("q", null));
        assertEquals(LLMUnavailableException.Kind.NOT_CONFIGURED, ex.kind());
        assertTrue(ex.getMessage().contains("暂未配置"), ex.getMessage());
        verify(llmProvider, never()).stream(any(), any());
    }

    @Test
    @DisplayName("streamChat 已配置返回流式 token，且问题以 user 角色、温度 0.7 下发；无 courseId 不出处")
    void streamChat_configured() {
        when(llmProvider.isConfigured()).thenReturn(true);
        QaStreamVO r = service.streamChat("q", null);
        assertEquals("tok1", r.getTokens().blockFirst());
        assertEquals(2, r.getTokens().collectList().block().size());
        assertTrue(r.getSources().isEmpty());
        // 无归属课程时不得发起检索（否则会平白增加一次 RAG 往返）
        verify(ragService, never()).retrieveTyped(anyString(), any(), anyInt());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> cap = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Double> tempCap = ArgumentCaptor.forClass(Double.class);
        verify(llmProvider).stream(cap.capture(), tempCap.capture());
        assertEquals(0.7, tempCap.getValue(), "流式对话温度应为 0.7");
        assertEquals(1, cap.getValue().size());
        assertEquals("user", cap.getValue().get(0).get("role"));
        assertEquals("q", cap.getValue().get(0).get("content"), "问题必须原样下发给流式接口");
    }

    @Test
    @DisplayName("streamChat 带 courseId：检索命中 -> 接地 system 提示 + 出处随流回传")
    void streamChat_withCourseId_retrieveHits_groundsAndReturnsSources() {
        when(llmProvider.isConfigured()).thenReturn(true);
        when(ragService.retrieveTyped(eq("q"), eq(7L), eq(5))).thenReturn(engineResult(
                new RagSource(1L, "第1章 绪论", 0.9, "算法的定义")));

        QaStreamVO r = service.streamChat("q", 7L);

        // 出处回传并与引擎召回项对齐（溯源展示的形状）
        assertEquals(1, r.getSources().size());
        QaSourceVO s = r.getSources().get(0);
        assertEquals("第1章 绪论", s.getDocumentName());
        assertEquals("算法的定义", s.getSnippet());
        assertEquals(0.9, s.getRelevanceScore());

        // 命中资料必须真正进入 system 提示（否则「展示了出处却没影响答案」）
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> cap = ArgumentCaptor.forClass(List.class);
        verify(llmProvider).stream(cap.capture(), any());
        assertEquals(2, cap.getValue().size(), "应下发 system(接地) + user(问题)");
        assertEquals("system", cap.getValue().get(0).get("role"));
        assertTrue(cap.getValue().get(0).get("content").contains("算法的定义"),
                "检索片段必须拼进 system 提示");
        assertEquals("user", cap.getValue().get(1).get("role"));
        assertEquals("q", cap.getValue().get(1).get("content"));
    }

    @Test
    @DisplayName("streamChat 带 courseId：检索异常 -> 降级纯 LLM 且不出处，流不中断")
    void streamChat_withCourseId_retrieveFails_degradesToPlainLlm() {
        when(llmProvider.isConfigured()).thenReturn(true);
        when(ragService.retrieveTyped(anyString(), any(), anyInt()))
                .thenThrow(new RuntimeException("RAG 服务不可用"));

        QaStreamVO r = service.streamChat("q", 7L);

        assertEquals("tok1", r.getTokens().blockFirst(), "检索失败不得中断流式问答");
        assertTrue(r.getSources().isEmpty());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> cap = ArgumentCaptor.forClass(List.class);
        verify(llmProvider).stream(cap.capture(), any());
        assertEquals(1, cap.getValue().size(), "降级后只下发 user 消息");
        assertEquals("user", cap.getValue().get(0).get("role"));
    }

    @Test
    @DisplayName("streamChat 带 courseId：检索为 PLACEHOLDER 占位 -> 不得接地、不出处")
    void streamChat_withCourseId_placeholderIsNotEvidence() {
        when(llmProvider.isConfigured()).thenReturn(true);
        when(ragService.retrieveTyped(anyString(), any(), anyInt())).thenReturn(
                new RagRetrievalResult(List.of(new RagSource(1L, "占位", 0.9, "占位片段")),
                        "PLACEHOLDER", null, null));

        QaStreamVO r = service.streamChat("q", 7L);

        assertTrue(r.getSources().isEmpty(), "占位召回不得进入出处");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> cap = ArgumentCaptor.forClass(List.class);
        verify(llmProvider).stream(cap.capture(), any());
        assertEquals(1, cap.getValue().size(), "占位召回不得拼接地 system 提示");
        assertEquals("user", cap.getValue().get(0).get("role"));
    }

    @Test
    @DisplayName("streamChat 供应商侧异常 -> 映射为 503 业务异常并计入失败埋点")
    void streamChat_providerError_mapsToServiceUnavailable() {
        // 本机实测形状：Key 无效时供应商返回 401，异常在订阅期抛出而非建流时抛出
        when(llmProvider.stream(any(), any())).thenReturn(Flux.error(new RuntimeException("401 Unauthorized")));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.streamChat("q", null).getTokens().blockFirst());
        assertEquals(503, ex.getCode(), "流式失败须以 503 降级语义收尾，而非落入兜底 500「系统未知异常」");
        assertEquals(1.0, meterRegistry.counter("aicourse_ai_calls_failed_total", "type", "chat").count(),
                "流式失败必须与同步路径一样计入失败埋点");
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
