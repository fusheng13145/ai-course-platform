package com.aicourse.service.component.ai;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.exception.BusinessException;
import com.aicourse.exception.LLMUnavailableException;
import com.aicourse.integration.rag.RagContract.RagSource;
import com.aicourse.service.RAGService;
import com.aicourse.vo.QaSourceVO;
import com.aicourse.vo.QaStreamVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 对话组件：单轮/多轮对话、流式对话、出题、内容生成、摘要、查重。
 * <p>拆分自原 AIServiceImpl（2026-08-29），底层调用委托 {@link AiCoreComponent}。</p>
 */
@Slf4j
@Component
public class AiDialogueComponent {

    /** 流式问答检索的召回条数（与同步 RAG 问答 {@code AiRagComponent.ragAsk} 保持同一口径）。 */
    private static final int STREAM_RETRIEVE_TOP_K = 5;
    /** 出题时注入的课程资料召回条数。 */
    private static final int QUESTION_RETRIEVE_TOP_K = 5;

    private final AiCoreComponent core;
    private final RAGService ragService;
    private final CourseEvidenceComponent evidence;

    public AiDialogueComponent(AiCoreComponent core, RAGService ragService, CourseEvidenceComponent evidence) {
        this.core = core;
        this.ragService = ragService;
        this.evidence = evidence;
    }

    /**
     * AI 对话交互
     */
    public String chat(String message, String context, Long userId) {
        return core.completeAndSanitize(core.buildMessages(message, context), 0.7, "chat");
    }

    /**
     * 多轮对话：透传完整消息历史前，对 user 角色内容逐条做注入中和；输出统一清洗。
     */
    public String chat(List<Map<String, String>> messages, Long userId) {
        if (messages == null || messages.isEmpty()) {
            // 这是入参问题，不是上游故障；过去复用 SERVICE_ERROR_MSG 会让"你没带历史"
            // 和"大模型 401"在用户端长得一模一样（DR-74 语义分名）
            throw new BusinessException(ApiCode.BAD_REQUEST, "没有可用于续聊的历史消息，请重新发起对话");
        }
        return core.completeAndSanitize(core.neutralizeUserMessages(messages), 0.7, "chat");
    }

    /**
     * AI 自动生成课程题目
     */
    public String generateQuestion(Long courseId, String type, String difficulty, int count) {
        log.info("生成AI");

        // courseId 必须进 prompt，否则跨课程出题时模型不知道题目归属哪门课；
        // 难度/题型缺省要显式兜底，不能把字面量 "null" 拼进提示词。
        String scope = courseId != null ? "为课程(courseId=" + courseId + ")" : "";
        // 键位必须点名（DR-89）：下游 QuestionServiceImpl 只读 content/options/correctAnswers/
        // explanation/score，此前提示词只用散文描述"包含题目内容、选项和正确答案"，真实 Key 下
        // 模型自选 stem 之类的键名，于是取不到题干、落库成空题干题目并返回 200——假成功。
        String prompt = String.format(
                "请%s生成%d道%s难度、类型为%s的课程题目。"
                        + "只返回一个 JSON 对象，不要输出任何解释文字或代码栅栏之外的内容，键固定为："
                        + "content(题干字符串)、options(选项字符串数组)、correctAnswers(正确答案，单选为字符串、"
                        + "多选为字符串数组)、explanation(解析字符串)、score(分值数字)。",
                scope, count, AiCoreComponent.orDefault(difficulty, "MEDIUM"), AiCoreComponent.orDefault(type, "SINGLE_CHOICE"));
        // 课程接地：把本课程真实召回的课程资料与知识图谱知识点拼进提示词，把「出什么题」限定在
        // 课程范围内，降低超纲题目与凭空编造。证据为空时原样返回，不改变原有生成行为。
        List<KnowledgePoint> points = evidence.loadPoints(courseId);
        String query = questionQuery(points, type, difficulty);
        List<RagSource> sources = evidence.retrieve(query, courseId, QUESTION_RETRIEVE_TOP_K);
        List<KnowledgePoint> grounded = evidence.matchPoints(points, query);
        // 失败即抛（503），不再把降级文案当生成结果返回——下游要拿它当 JSON 解析。
        return core.completeAndSanitize(core.buildMessages(withGrounding(prompt, sources, grounded), null),
                0.9, "question");
    }

    /**
     * 出题证据检索用的查询：本课程全部知识点名称以顿号连接（含全部名称才能让
     * {@link CourseEvidenceComponent#matchPoints} 按重要度取前 5）；无知识点时退回「题型 难度」，
     * 两者都为空则不检索。
     */
    private String questionQuery(List<KnowledgePoint> points, String type, String difficulty) {
        String names = points.stream()
                .map(KnowledgePoint::getName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .reduce((a, b) -> a + "、" + b)
                .orElse("");
        if (!names.isEmpty()) {
            return names;
        }
        if ((type == null || type.isBlank()) && (difficulty == null || difficulty.isBlank())) {
            return "";
        }
        return (AiCoreComponent.orDefault(type, "") + " " + AiCoreComponent.orDefault(difficulty, "")).trim();
    }

    /** 在原有出题提示词后追加课程证据段与「不得超纲」硬约束；无证据时原样返回。 */
    private String withGrounding(String prompt, List<RagSource> sources, List<KnowledgePoint> grounded) {
        boolean hasSources = sources != null && !sources.isEmpty();
        boolean hasPoints = grounded != null && !grounded.isEmpty();
        if (!hasSources && !hasPoints) {
            return prompt;
        }
        StringBuilder sb = new StringBuilder(prompt);
        if (hasSources) {
            sb.append("\n【课程资料摘录】\n").append(evidence.renderSources(sources));
        }
        if (hasPoints) {
            sb.append("\n【课程知识点（知识图谱）】\n").append(evidence.renderPoints(grounded));
        }
        sb.append("\n题目必须落在上述课程范围内，不得超纲；引用课程内容时须与资料一致。");
        return sb.toString();
    }

    /**
     * 通用内容生成
     */
    public String generateContent(String type, String params) {
        log.info("生成AI");

        // 纠错 / 润色使用专门提示词，确保返回内容对学生笔记场景真正可用
        String prompt;
        if ("correct".equals(type)) {
            prompt = "你是一位严谨的批改老师。请检查以下文本中的错别字、语法错误、标点误用与逻辑不通之处，"
                    + "先逐条指出问题，再给出修改后的完整文本。若文本无明显问题，也请明确说明。\n文本内容：\n" + AiCoreComponent.orDefault(params, "");
        } else if ("polish".equals(type)) {
            prompt = "请在不改变原意的前提下，润色以下文本，使其表达更流畅、专业、优美，"
                    + "保留原有的 Markdown 结构（如标题、列表）。直接返回润色后的文本。\n文本内容：\n" + AiCoreComponent.orDefault(params, "");
        } else {
            prompt = "请生成" + AiCoreComponent.orDefault(type, "") + "内容，" + AiCoreComponent.orDefault(params, "");
        }
        return core.completeAndSanitize(core.buildMessages(prompt, null), 0.7, "content");
    }

    /**
     * 文本内容摘要总结
     */
    public String generateSummary(String content) {
        log.info("生成AI");

        String prompt = "请总结以下内容：\n" + AiCoreComponent.orDefault(content, "");
        return core.completeAndSanitize(core.buildMessages(prompt, null), 0.3, "content");
    }

    /**
     * 文本相似度/抄袭检测
     */
    public String checkPlagiarism(String text1, String text2) {
        String prompt = "请对比以下两段文本的相似度，返回相似百分比和重复内容分析：\n文本1："
                + AiCoreComponent.orDefault(text1, "") + "\n文本2：" + AiCoreComponent.orDefault(text2, "");
        // 埋点类型必须是 plagiarism：并入 "correct" 会让批改与查重的调用量/失败率互相污染，
        // Grafana 按 type 下钻时无法区分两类业务。
        return core.completeAndSanitize(core.buildMessages(prompt, null), 0.2, "plagiarism");
    }

    /**
     * 流式 AI 对话：将用户问题经 PromptGuard 注入中和后，委托至 LLM 供应商流式接口，
     * 以 Flux 逐 token 推送内容增量，供 SSE 端点使用。
     * <p>
     * <b>课程维度接地（2026-10-14 增）</b>：当传入 {@code courseId} 时，先对该课程知识库做一次
     * 检索（仅召回、不生成），把命中的课程资料片段拼进 system prompt，让模型「依据资料回答」——
     * 避免出现「界面展示了出处、但出处根本没影响答案」的误导；命中的出处随流一并回传供界面溯源。
     * 检索失败/未命中不中断问答：降级为纯 LLM 直答，sources 为空列表。
     * <p>
     * 供应商侧异常（无效 Key 的 401、网关 5xx 等）在订阅期异步抛出，映射为
     * {@code BusinessException(SERVICE_UNAVAILABLE)}，使端点返回 503 降级语义而非落入
     * 兜底的「系统未知异常」500；口径与同步出口 {@code completeAndSanitize} 一致，
     * 失败同样计入按类型分桶的失败埋点。
     */
    public QaStreamVO streamChat(String question, Long courseId) {
        if (!core.isProviderEnabled()) {
            // 未配 Key 时过去把提示文案当成"流式回答"的第一帧发出去，界面看到的就是正常回答在打字；
            // 现在同步抛出 → 端点以 503 结束，SSE 根本不开播（口径与同步出口一致）
            throw new LLMUnavailableException(LLMUnavailableException.Kind.NOT_CONFIGURED,
                    core.noProviderMessage());
        }
        core.recordAiCall("chat");
        String safeQuestion = core.neutralizeInjection(question);

        // 有归属课程时先召回课程资料：既做接地提示词，又把出处带回界面（两条用途同源，避免「出处是摆设」）
        List<QaSourceVO> sources = List.of();
        String grounding = null;
        if (courseId != null) {
            try {
                List<QaSourceVO> retrieved = core.toQaSources(
                        ragService.retrieveTyped(question, courseId, STREAM_RETRIEVE_TOP_K));
                if (!retrieved.isEmpty()) {
                    sources = retrieved;
                    grounding = buildGrounding(retrieved);
                }
            } catch (Exception e) {
                log.warn("流式问答课程检索失败，降级为纯 LLM 对话: {}", e.getMessage());
            }
        }

        List<Map<String, String>> messages = new ArrayList<>(grounding != null ? 2 : 1);
        if (grounding != null) {
            messages.add(Map.of("role", "system", "content", grounding));
        }
        messages.add(Map.of("role", "user", "content", safeQuestion));
        core.recordAiToken("chat", core.estimateInputTokens(messages));

        // 归属快照必须在本方法（Tomcat 线程、控制器作用域内）捕获：流式订阅发生在 Reactor 线程，
        // ThreadLocal 作用域跨线程即失效，快照是流终止回调落库（QA_STREAM 场景）的唯一归属来源
        AiCallContext.Snapshot callInfo = AiCallContext.capture();
        Flux<String> tokens = core.stream(messages, 0.7, callInfo)
                .onErrorMap(e -> {
                    log.error("流式问答调用失败: {}", e.getMessage(), e);
                    core.recordAiFailure("chat");
                    if (e instanceof LLMUnavailableException le) {
                        // 保留类别与原话术（401 该说"凭据被拒"，不该塌成"暂时不可用"）；
                        // 它本身就是 BusinessException(503)，SSE 端点据此以 503 收场
                        return le;
                    }
                    return new BusinessException(ApiCode.SERVICE_UNAVAILABLE, e.getMessage(), e);
                });
        return new QaStreamVO(tokens, sources);
    }

    /**
     * 由检索命中的资料片段拼装接地 system 提示：明确「优先依据资料回答」，
     * 资料不足时允许结合通用知识但须显式说明。片段逐条做输入侧注入中和
     * （课程资料同样可能夹带对话模板控制符，不能因其"来源可信"就跳过护栏）。
     */
    private String buildGrounding(List<QaSourceVO> sources) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是课程学习助手。请优先依据下方检索到的课程资料回答学生问题；")
                .append("若资料不足以回答，可结合通用知识补充，但必须明确说明哪些内容超出资料范围。\n\n")
                .append("【检索到的课程资料】\n");
        int idx = 1;
        for (QaSourceVO s : sources) {
            sb.append('[').append(idx++).append("] ");
            if (s.getDocumentName() != null && !s.getDocumentName().isBlank()) {
                sb.append(s.getDocumentName()).append("：");
            }
            sb.append(core.neutralizeInjection(s.getSnippet() != null ? s.getSnippet() : "")).append('\n');
        }
        return sb.toString();
    }
}
