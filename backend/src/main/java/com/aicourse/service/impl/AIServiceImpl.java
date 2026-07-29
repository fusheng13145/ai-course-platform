package com.aicourse.service.impl;

import com.aicourse.common.JsonUtils;
import com.aicourse.llm.LLMProvider;
import com.aicourse.service.AIService;
import com.aicourse.service.GraphRAGService;
import com.aicourse.service.KnowledgeRecommendationService;
import com.aicourse.service.PromptGuard;
import com.aicourse.service.RAGService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 服务实现类
 * 通过可插拔的 {@link LLMProvider} 对接各类大模型（DeepSeek/OpenAI/Qwen），提供对话、出题、
 * 内容生成、查重、RAG 问答等能力。供应商由配置 {@code ai.provider} 决定，与业务解耦。
 */
@Service
@Slf4j
public class AIServiceImpl implements AIService {


    /** 未配置任何 LLM 供应商 Key 时返回的清晰业务提示（不再返回伪造 JSON） */
    private static final String NO_PROVIDER_MSG = "AI 服务暂未配置（请联系管理员配置大模型 API Key 后重试）。";
    private static final String SERVICE_ERROR_MSG = "AI 服务暂时不可用，请稍后再试。";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LLMProvider llmProvider;
    private final RAGService ragService;
    private final PromptGuard promptGuard;
    private final GraphRAGService graphRagService;
    private final KnowledgeRecommendationService knowledgeRecommendationService;

    public AIServiceImpl(LLMProvider llmProvider, RAGService ragService, PromptGuard promptGuard,
                         GraphRAGService graphRagService,
                         KnowledgeRecommendationService knowledgeRecommendationService) {
        this.llmProvider = llmProvider;
        this.ragService = ragService;
        this.promptGuard = promptGuard;
        this.graphRagService = graphRagService;
        this.knowledgeRecommendationService = knowledgeRecommendationService;
    }

    /**
     * AI 对话交互
     */
    @Override
    public String chat(String message, String context, Long userId) {
        return completeAndSanitize(buildMessages(message, context), 0.7);
    }

    /**
     * 多轮对话：透传完整消息历史前，对 user 角色内容逐条做注入中和；输出统一清洗。
     */
    @Override
    public String chat(List<Map<String, String>> messages, Long userId) {
        if (messages == null || messages.isEmpty()) {
            return SERVICE_ERROR_MSG;
        }
        return completeAndSanitize(neutralizeUserMessages(messages), 0.7);
    }

    /**
     * AI 自动生成课程题目
     */
    @Override
    public String generateQuestion(Long courseId, String type, String difficulty, int count) {
        String prompt = String.format(
                "请生成%d道%s难度、类型为%s的课程题目，包含题目内容、选项和正确答案，以JSON格式返回。",
                count, difficulty, type);
        String raw = completeAndSanitize(buildMessages(prompt, null), 0.9);
        return raw != null ? raw : SERVICE_ERROR_MSG;
    }

    /**
     * 通用内容生成
     */
    @Override
    public String generateContent(String type, String params) {
        String prompt = "请生成" + type + "内容，" + params;
        String raw = completeAndSanitize(buildMessages(prompt, null), 0.7);
        return raw != null ? raw : SERVICE_ERROR_MSG;
    }

    /**
     * 文本内容摘要总结
     */
    @Override
    public String generateSummary(String content) {
        String prompt = "请总结以下内容：\n" + content;
        String raw = completeAndSanitize(buildMessages(prompt, null), 0.3);
        return raw != null ? raw : SERVICE_ERROR_MSG;
    }

    /**
     * 文本相似度/抄袭检测
     */
    @Override
    public String checkPlagiarism(String text1, String text2) {
        String prompt = "请对比以下两段文本的相似度，返回相似百分比和重复内容分析：\n文本1：" + text1 + "\n文本2：" + text2;
        String raw = completeAndSanitize(buildMessages(prompt, null), 0.2);
        return raw != null ? raw : SERVICE_ERROR_MSG;
    }

    /**
     * 基于课程知识库的 GraphRAG 问答（自包含：BM25 检索 + 图谱增强 + LLM 生成）
     * <p>
     * 流程：
     * <ol>
     *   <li>从 {@link GraphRAGService} 获取问句相关的图谱子图（实体链接 + BFS 出发）；</li>
     *   <li>将图谱上下文作为前置信息注入问题，交由 {@link RAGService} 做 BM25 检索 + LLM 生成；</li>
     *   <li>当图谱数据为空时自动降级为纯文本 RAG，不影响正常问答。</li>
     * </ol>
     */
    @Override
    public String ragAsk(String question, Long courseId) {
        // 先尝试获取图谱上下文（若图谱有数据）
        String graphContext = "";
        try {
            Map<String, Object> ctx = graphRagService.retrieveGraphContext(question, courseId, 2);
            if (ctx != null && !ctx.isEmpty()) {
                graphContext = graphRagService.formatGraphContextForPrompt(ctx);
            }
        } catch (Exception e) {
            log.debug("[GraphRAG] 图谱上下文获取失败，降级为纯文本 RAG: {}", e.getMessage());
        }

        // 将图谱上下文前置注入问题（让 RAG 检索和 LLM 生成都能感知图谱结构）
        String enhancedQuestion = graphContext.isEmpty() ? question
                : graphContext + "\n用户问题：" + question;

        try {
            String ragResult = ragService.ask(enhancedQuestion, courseId, 5);
            if (ragResult != null && !ragResult.contains("\"code\":503")) {
                String answer = extractRagAnswer(ragResult);
                if (answer != null && !answer.isBlank()) {
                    return promptGuard.sanitizeOutput(answer);
                }
            }
        } catch (Exception e) {
            log.warn("RAG 服务调用异常，降级为纯 LLM 调用: {}", e.getMessage());
        }
        String systemPrompt = "请基于课程知识回答问题。如果问题超出课程范围，请明确说明。";
        String raw = completeAndSanitize(buildMessages(question, systemPrompt), 0.5);
        return raw != null ? raw : SERVICE_ERROR_MSG;
    }

    /**
     * 从 RAG 返回的 JSON（{@code {"answer":...,"sources":...}}）中提取纯文本答案；
     * 若非 JSON 或缺少 answer 字段，则原样返回（兼容历史格式）。
     */
    private String extractRagAnswer(String ragJson) {
        String json = JsonUtils.extractJsonObject(ragJson);
        if (json == null) {
            return ragJson;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if (node.has("answer") && node.get("answer").isTextual()
                    && !node.get("answer").asText().isBlank()) {
                return node.get("answer").asText();
            }
        } catch (Exception e) {
            log.debug("RAG 答案 JSON 解析失败，原样返回: {}", e.getMessage());
        }
        return ragJson;
    }

    // ===================== 结构化输出（健壮性增强） =====================

    /**
     * 个性化学习建议（结构化 JSON 输出，便于前端直接渲染）
     */
    @Override
    public String recommendLearningJson(String courseList) {
        String schema = "{\"recommendations\": [{\"resource\": \"资源名\", \"reason\": \"推荐理由\"}]}";
        String prompt = "我目前在学习以下课程：" + courseList + "。请为我推荐个性化的学习资源和学习建议。";
        return callLlm(buildMessages(prompt, null), 0.7, schema);
    }

    /**
     * 基于真实学情数据 + 知识图谱的学习推荐（双引擎增强版）：
     * <p>
     * 引擎一（算法）：{@link KnowledgeRecommendationService} 基于图谱关系做薄弱点关联推荐；
     * 引擎二（LLM）：将算法推荐结果 + 学情画像注入提示词，生成可解释的自然语言建议。
     * 若图谱数据为空（团队 Python 侧尚未灌入），自动降级为纯 LLM 推荐。
     */
    @Override
    public String recommendLearningWithContext(Map<String, Object> context) {
        StringBuilder sb = new StringBuilder("【学生学情画像】\n");

        // 课程列表
        if (context.containsKey("courseList")) {
            sb.append("已选课程：").append(context.get("courseList")).append("\n");
        }

        // 课程进度
        if (context.containsKey("progress")) {
            sb.append("课程平均进度：").append(context.get("progress")).append("%\n");
        }

        // 薄弱项（按掌握度升序排列，最弱的在前）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> weakPoints = context.containsKey("weakPoints")
                ? (List<Map<String, Object>>) context.get("weakPoints") : Collections.emptyList();
        if (!weakPoints.isEmpty()) {
            List<Map<String, Object>> sorted = new ArrayList<>(weakPoints);
            sorted.sort(Comparator.comparingDouble(
                    m -> ((Number) m.getOrDefault("mastery", 100)).doubleValue()));
            sb.append("薄弱知识点（按掌握度排序，越靠前越需强化）：\n");
            for (int i = 0; i < Math.min(sorted.size(), 8); i++) {
                Map<String, Object> wp = sorted.get(i);
                sb.append("  ").append(i + 1).append(". ").append(wp.getOrDefault("name", "未知"))
                        .append("（掌握度：").append(wp.getOrDefault("mastery", "N/A")).append("）\n");
            }
        } else {
            sb.append("（暂无薄弱项数据，请完成更多学习任务以获取精准推荐）\n");
        }

        // ====== 图谱推荐引擎（引擎一） ======
        if (context.containsKey("courseId") && !weakPoints.isEmpty()) {
            try {
                Long courseId = ((Number) context.get("courseId")).longValue();
                // 将 weakPoints List<Map> 转换为 Map<String, Double>
                Map<String, Double> weakMap = weakPoints.stream()
                        .filter(m -> m.containsKey("name") && m.containsKey("mastery"))
                        .collect(Collectors.toMap(
                                m -> (String) m.get("name"),
                                m -> ((Number) m.get("mastery")).doubleValue(),
                                (a, b) -> a));
                if (!weakMap.isEmpty()) {
                    List<Map<String, Object>> kgRecs = knowledgeRecommendationService
                            .recommendFromWeakPoints(courseId, weakMap, 5);
                    if (!kgRecs.isEmpty()) {
                        sb.append("\n【知识图谱关联推荐】（基于知识点关系的算法推荐）：\n");
                        for (int i = 0; i < kgRecs.size(); i++) {
                            Map<String, Object> rec = kgRecs.get(i);
                            sb.append("  ").append(i + 1).append(". ")
                                    .append(rec.getOrDefault("recommendType", "推荐")).append("：")
                                    .append(rec.getOrDefault("name", "")).append(" — ")
                                    .append(rec.getOrDefault("reason", "")).append("\n");
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("[推荐引擎] 图谱推荐失败，降级为纯 LLM 推荐: {}", e.getMessage());
            }
        }

        // 近期成绩
        if (context.containsKey("recentScores")) {
            sb.append("近期成绩：").append(context.get("recentScores")).append("\n");
        }

        sb.append("\n请根据以上真实学情数据和图谱推荐，生成个性化学习推荐方案，优先针对薄弱项给出具体可操作的建议。");
        sb.append("\n只返回纯JSON：{\"recommendations\":[{\"resource\":\"推荐资源或行动\",\"reason\":\"基于数据的具体理由\",\"priority\":\"HIGH|MEDIUM|LOW\"}]}");

        String schema = "{\"recommendations\":[{\"resource\":\"资源名\",\"reason\":\"推荐理由\",\"priority\":\"HIGH|MEDIUM|LOW\"}]}";
        return callLlm(buildMessages(sb.toString(), null), 0.6, schema);
    }

    /**
     * 知识图谱 AI 抽取（两阶段法，对齐 IPA 框架）。
     * 第一阶段：模拟学习者阅读理解，识别文本中的核心概念与结构；
     * 第二阶段：以专家身份归纳总结，输出结构化知识点与关系。
     */
    public String extractKnowledgeGraphJson(String content) {
        String schema = "{\"nodes\":[{\"name\":\"知识点名称\",\"content\":\"要点说明\",\"type\":\"CONCEPT|PRINCIPLE|METHOD|FACT\"}],"
                + "\"relations\":[{\"source\":\"源节点名称\",\"target\":\"目标节点名称\",\"relationType\":\"PREREQUISITE|INCLUDES|RELATED|EXTENDS\"}]}";

        // 阶段一：阅读理解
        String stage1Prompt = "你是一位正在学习以下课程内容的学生。请仔细阅读，识别文中的核心概念、"
                + "关键原理和方法，并用自己的话整理出主要内容框架。只需陈述理解，不要输出 JSON。\n\n" + content;

        String readingResult;
        try {
            readingResult = llmProvider.complete(
                    List.of(Map.of("role", "user", "content", stage1Prompt)), 0.3);
            if (readingResult == null || readingResult.isBlank()) {
                // 退化为单阶段
                String prompt = "请从以下课程内容中抽取知识结构（知识点及其关系），仅返回符合 schema 的 JSON，不要额外说明：\n" + content;
                return callLlm(buildMessages(prompt, null), 0.3, schema);
            }
        } catch (Exception e) {
            log.debug("[IPA] 阶段一失败，降级为单阶段抽取: {}", e.getMessage());
            String prompt = "请从以下课程内容中抽取知识结构（知识点及其关系），仅返回符合 schema 的 JSON，不要额外说明：\n" + content;
            return callLlm(buildMessages(prompt, null), 0.3, schema);
        }

        // 阶段二：专家归纳
        String stage2Prompt = "你是一位领域专家。以下是学生对课程内容的理解摘要：\n\n" + readingResult
                + "\n\n基于以上理解，请精确抽取知识点及其关系，"
                + "仅返回符合以下 schema 的 JSON，不要额外说明：\n" + schema;

        return callLlm(buildMessages(stage2Prompt, null), 0.3, schema);
    }

    /**
     * 学情 AI 建议：聚合学情统计与薄弱点，生成结构化学习建议
     */
    public String generateLearningAdviceJson(String statsJson, String weakPointsJson) {
        String schema = "{\"summary\":\"一句话总体建议\",\"items\":[{\"title\":\"建议标题\",\"detail\":\"具体做法\",\"level\":\"HIGH|MEDIUM|LOW\"}]}";
        String prompt = "以下是某学习者的学情统计：" + statsJson
                + "\n以及 AI 分析出的薄弱项：" + weakPointsJson
                + "\n请结合两者给出可落地的学习建议，按优先级排列。";
        return callLlm(buildMessages(prompt, null), 0.6, schema);
    }

    /**
     * 管理员 AI 监控：基于日志文本检测异常，返回结构化洞察
     */
    public String analyzeAnomaliesJson(String logsJson) {
        String schema = "{\"alerts\":[{\"level\":\"HIGH|MEDIUM|LOW\",\"title\":\"问题标题\",\"detail\":\"风险说明与建议\"}]}";
        String prompt = "以下是系统近期的登录与操作日志：\n" + logsJson
                + "\n请检测其中的异常行为（如暴力破解、异常高频操作、成绩异动等），仅返回符合 schema 的 JSON。";
        return callLlm(buildMessages(prompt, null), 0.3, schema);
    }

    /**
     * 知识点薄弱项分析（结构化 JSON 输出）
     * 返回格式：{"weakPoints": [{"description": "薄弱项描述", "severity": "HIGH|MEDIUM|LOW"}]}
     */
    public String analyzeWeakPointsJson(String statsJson) {
        String schema = "{\"weakPoints\": [{\"description\": \"薄弱项描述\", \"severity\": \"HIGH 或 MEDIUM 或 LOW\"}]}";
        String prompt = "请根据以下学习统计数据，分析学习者的知识薄弱项：" + statsJson;
        return callLlm(buildMessages(prompt, null), 0.4, schema);
    }

    /**
     * 题目推荐（结构化 JSON 输出）
     * 返回格式：{"questions": [{"content": "题目内容", "type": "SINGLE_CHOICE", "difficulty": "..."}]}
     */
    public String recommendQuestionsJson(Long courseId, String difficulty, int count) {
        String schema = "{\"questions\": [{\"content\": \"题目内容\", \"type\": \"SINGLE_CHOICE\", \"difficulty\": \""
                + (difficulty != null ? difficulty : "MEDIUM") + "\"}]}";
        String prompt = String.format("请为课程(courseId=%d)生成%d道%s难度的练习题。", courseId, count, difficulty);
        return callLlm(buildMessages(prompt, null), 0.8, schema);
    }

    /**
     * 解析薄弱点 JSON 为列表（健壮：解析失败回退为单条文本）
     */
    public List<Map<String, String>> parseWeakPoints(String jsonText) {
        List<Map<String, String>> result = new ArrayList<>();
        String json = JsonUtils.extractJsonObject(jsonText);
        if (json == null) {
            if (jsonText != null && !jsonText.isBlank()) {
                result.add(Map.of("description", jsonText.trim(), "severity", "MEDIUM"));
            }
            return result;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode arr = root.has("weakPoints") ? root.get("weakPoints") : root;
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String desc = n.has("description") ? n.get("description").asText() : n.asText();
                    String sev = n.has("severity") ? n.get("severity").asText().toUpperCase() : "MEDIUM";
                    if (!"HIGH".equals(sev) && !"MEDIUM".equals(sev) && !"LOW".equals(sev)) sev = "MEDIUM";
                    result.add(Map.of("description", desc, "severity", sev));
                }
            }
        } catch (Exception e) {
            log.warn("薄弱点 JSON 解析失败，回退文本: {}", e.getMessage());
            result.add(Map.of("description", json, "severity", "MEDIUM"));
        }
        if (result.isEmpty()) {
            result.add(Map.of("description", "AI 暂未返回具体分析，请稍后重试", "severity", "LOW"));
        }
        return result;
    }

    /**
     * 判断当前是否有可用的 LLM 供应商（已配置 Key）
     */
    public boolean isProviderConfigured() {
        return llmProvider != null;
    }

    /**
     * 流式 AI 对话：将用户问题经 PromptGuard 注入中和后，委托至 LLM 供应商流式接口，
     * 以 Flux 逐 token 推送内容增量，供 SSE 端点使用。
     */
    @Override
    public reactor.core.publisher.Flux<String> streamChat(String question) {
        if (!llmProvider.isConfigured()) {
            return reactor.core.publisher.Flux.just(NO_PROVIDER_MSG);
        }
        String safeQuestion = promptGuard.neutralizeInjection(question);
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", safeQuestion));
        return llmProvider.stream(messages, 0.7)
                .map(promptGuard::sanitizeOutput);
    }

    /**
     * 当供应商未配置 Key 时返回的统一提示
     */
    public String noProviderMessage() {
        return NO_PROVIDER_MSG;
    }

    private List<Map<String, String>> buildMessages(String userMessage, String systemPrompt) {
        // 输入侧护栏：中和用户内容中的对话模板控制符与伪角色标记，防止从 user 角色「越狱」为系统指令
        String safeUser = promptGuard.neutralizeInjection(userMessage);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            return List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", safeUser)
            );
        }
        return List.of(Map.of("role", "user", "content", safeUser));
    }

    /**
     * 对多轮消息历史逐条中和 user 角色内容（system/assistant 由服务端可信构造，不做改写）。
     */
    private List<Map<String, String>> neutralizeUserMessages(List<Map<String, String>> messages) {
        List<Map<String, String>> safe = new ArrayList<>(messages.size());
        for (Map<String, String> msg : messages) {
            if (msg == null) {
                continue;
            }
            String role = msg.get("role");
            String content = msg.get("content");
            if ("user".equalsIgnoreCase(role) && content != null) {
                safe.add(Map.of("role", role, "content", promptGuard.neutralizeInjection(content)));
            } else {
                safe.add(msg);
            }
        }
        return safe;
    }

    /**
     * 直连出口：调用供应商后对输出统一做活动内容清洗（null 透传，交由上层判空）。
     */
    private String completeAndSanitize(List<Map<String, String>> messages, double temperature) {
        return promptGuard.sanitizeOutput(llmProvider.complete(messages, temperature));
    }

    /**
     * 统一调用入口：未配置供应商时返回清晰提示，调用失败返回通用错误文案；成功结果统一清洗后下发
     */
    private String callLlm(List<Map<String, String>> messages, Double temperature, String jsonSchema) {
        if (!llmProvider.isConfigured()) {
            return NO_PROVIDER_MSG;
        }
        String result = jsonSchema != null
                ? llmProvider.completeJson(messages, temperature, jsonSchema)
                : llmProvider.complete(messages, temperature);
        return result != null ? promptGuard.sanitizeOutput(result) : SERVICE_ERROR_MSG;
    }
}
