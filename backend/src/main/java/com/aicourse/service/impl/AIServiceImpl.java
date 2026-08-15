package com.aicourse.service.impl;


import com.aicourse.common.JsonUtils;
import com.aicourse.exception.BusinessException;
import com.aicourse.llm.LLMProvider;
import com.aicourse.service.AIService;
import com.aicourse.service.PromptGuard;
import com.aicourse.service.RAGService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 服务实现类
 * 通过可插拔的 {@link LLMProvider} 对接各类大模型（DeepSeek/OpenAI/Qwen），提供对话、出题、
 * 内容生成、查重、RAG 问答等能力。供应商由配置 {@code ai.provider} 决定，与业务解耦。
 * <p>已脱离 knowledge 模块：原 GraphRAG 多跳增强与基于图谱关系的算法推荐引擎（属另一成员负责的知识图谱
 * 知识库范畴）不再内置，本端 AI 仅保留 RAG 问答与学情驱动的 LLM 推荐。</p>
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
    private final MeterRegistry meterRegistry;
    /** 按业务类型分桶的 AI 调用计数器缓存：key=类型，value=对应 Counter（首次访问时懒注册）。 */
    private final java.util.Map<String, Counter> aiCallCounters = new java.util.concurrent.ConcurrentHashMap<>();
    /** 按业务类型分桶的 AI 输入 token 估算计数器缓存（与调用计数器对称，口径见 {@link #recordAiToken}）。 */
    private final java.util.Map<String, Counter> aiTokenCounters = new java.util.concurrent.ConcurrentHashMap<>();
    /** 按业务类型分桶的 AI 调用失败计数器缓存（与调用计数器对称，口径见 {@link #recordAiFailure}）。 */
    private final java.util.Map<String, Counter> aiFailedCounters = new java.util.concurrent.ConcurrentHashMap<>();

    public AIServiceImpl(LLMProvider llmProvider, RAGService ragService, PromptGuard promptGuard,
                         MeterRegistry meterRegistry) {
        this.llmProvider = llmProvider;
        this.ragService = ragService;
        this.promptGuard = promptGuard;
        this.meterRegistry = meterRegistry;
    }

    /**
     * LLM 调用埋点：每次实际发起大模型请求时，按业务类型递增对应 Counter，
     * 使 Grafana 能按类型（chat/question/content/correct/rag/recommend/analyze/extract_graph）下钻统计。
     *
     * @param type 业务类型，取值见各公开方法的调用处
     */
    private void recordAiCall(String type) {
        aiCallCounters
                .computeIfAbsent(type, t -> Counter.builder("aicourse_ai_calls_total")
                        .tag("type", t)
                        .description("AI 大模型调用总次数（按业务类型分桶）")
                        .register(meterRegistry))
                .increment();
    }

    /**
     * AI 输入 token 估算埋点：按业务类型累加本次调用的输入 token 估算量。
     * <p>token 估算与目标供应商计费口径对齐项目既有约定（{@code AiQuotaService}）：
     * 中英文混合场景下保守按 {@code 1 token ≈ 1.5 字符} 折算，仅统计 prompt 输入规模，
     * 不含响应输出（响应长度在请求前不可知）；用于 Grafana 估算 AI 成本消耗趋势。</p>
     *
     * @param type  业务类型（与 {@link #recordAiCall} 一致）
     * @param tokens 输入 token 估算量（≥0）
     */
    private void recordAiToken(String type, long tokens) {
        if (tokens <= 0) {
            return;
        }
        aiTokenCounters
                .computeIfAbsent(type, t -> Counter.builder("aicourse_ai_tokens_estimate_total")
                        .tag("type", t)
                        .description("AI 大模型输入 token 估算总量（按业务类型分桶，1 token≈1.5 字符折算）")
                        .register(meterRegistry))
                .increment(tokens);
    }

    /**
     * AI 调用失败埋点：按业务类型累加失败次数。
     * <p>失败判定口径与 {@link #recordAiCall} 对齐（type 一致）：
     * LLM 供应商调用抛出异常，或返回 null（统一降级为 {@code SERVICE_ERROR_MSG}）均计一次失败。
     * 注意：未配置供应商（{@code NO_PROVIDER_MSG} 路径）不计入失败——那属于部署配置缺失，
     * 而非调用失败，避免污染失败率。与调用计数配合可计算各类型失败率，
     * 供 Prometheus 告警「AI 调用失败率突增」使用。</p>
     *
     * @param type 业务类型（与 {@link #recordAiCall} 一致）
     */
    private void recordAiFailure(String type) {
        aiFailedCounters
                .computeIfAbsent(type, t -> Counter.builder("aicourse_ai_calls_failed_total")
                        .tag("type", t)
                        .description("AI 大模型调用失败次数（按业务类型分桶，含异常与空响应降级）")
                        .register(meterRegistry))
                .increment();
    }

    /**
     * 依据消息列表的总字符数估算输入 token 数（1 token ≈ 1.5 字符）。
     */
    private long estimateInputTokens(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0L;
        }
        long chars = 0L;
        for (Map<String, String> m : messages) {
            if (m == null) {
                continue;
            }
            String content = m.get("content");
            if (content != null) {
                chars += content.length();
            }
        }
        return (long) (chars / 1.5);
    }

    /**
     * AI 对话交互
     */
    @Override
    public String chat(String message, String context, Long userId) {
        return completeAndSanitize(buildMessages(message, context), 0.7, "chat");
    }

    /**
     * 多轮对话：透传完整消息历史前，对 user 角色内容逐条做注入中和；输出统一清洗。
     */
    @Override
    public String chat(List<Map<String, String>> messages, Long userId) {
        if (messages == null || messages.isEmpty()) {
            return SERVICE_ERROR_MSG;
        }
        return completeAndSanitize(neutralizeUserMessages(messages), 0.7, "chat");
    }

    /**
     * AI 自动生成课程题目
     */
    @Override
    public String generateQuestion(Long courseId, String type, String difficulty, int count) {
        log.info("生成AI");

        String prompt = String.format(
                "请生成%d道%s难度、类型为%s的课程题目，包含题目内容、选项和正确答案，以JSON格式返回。",
                count, difficulty, type);
        String raw = completeAndSanitize(buildMessages(prompt, null), 0.9, "question");
        return raw != null ? raw : SERVICE_ERROR_MSG;
    }

    /**
     * 通用内容生成
     */
    @Override
    public String generateContent(String type, String params) {
        log.info("生成AI");

        // 纠错 / 润色使用专门提示词，确保返回内容对学生笔记场景真正可用
        String prompt;
        if ("correct".equals(type)) {
            prompt = "你是一位严谨的批改老师。请检查以下文本中的错别字、语法错误、标点误用与逻辑不通之处，"
                    + "先逐条指出问题，再给出修改后的完整文本。若文本无明显问题，也请明确说明。\n文本内容：\n" + params;
        } else if ("polish".equals(type)) {
            prompt = "请在不改变原意的前提下，润色以下文本，使其表达更流畅、专业、优美，"
                    + "保留原有的 Markdown 结构（如标题、列表）。直接返回润色后的文本。\n文本内容：\n" + params;
        } else {
            prompt = "请生成" + type + "内容，" + params;
        }
        String raw = completeAndSanitize(buildMessages(prompt, null), 0.7, "content");
        return raw != null ? raw : SERVICE_ERROR_MSG;
    }

    /**
     * 文本内容摘要总结
     */
    @Override
    public String generateSummary(String content) {
        log.info("生成AI");

        String prompt = "请总结以下内容：\n" + content;
        String raw = completeAndSanitize(buildMessages(prompt, null), 0.3, "content");
        return raw != null ? raw : SERVICE_ERROR_MSG;
    }

    /**
     * 文本相似度/抄袭检测
     */
    @Override
    public String checkPlagiarism(String text1, String text2) {
        String prompt = "请对比以下两段文本的相似度，返回相似百分比和重复内容分析：\n文本1：" + text1 + "\n文本2：" + text2;
        String raw = completeAndSanitize(buildMessages(prompt, null), 0.2, "correct");
        return raw != null ? raw : SERVICE_ERROR_MSG;
    }

    /**
     * 基于课程知识库（课程章节文档）的 RAG 问答（自包含：BM25/向量检索 + LLM 生成）。
     * <p>
     * 流程：将用户问题直接交由 {@link RAGService} 做混合检索 + LLM 生成；
     * 检索失败时降级为纯 LLM 调用，保证问答链路不中断。
     * <p>已脱离 knowledge 模块：原 GraphRAG 多跳增强由另一成员图谱服务负责，本端不再内置图谱检索。</p>
     */
    @Override
    public String ragAsk(String question, Long courseId) {
        String enhancedQuestion = question;

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
        String raw = completeAndSanitize(buildMessages(question, systemPrompt), 0.5, "rag");
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
        return callLlm(buildMessages(prompt, null), 0.7, schema, "recommend");
    }

    /**
     * 基于真实学情数据的学习推荐（LLM 引擎）：
     * <p>
     * 将学情画像（课程、进度、薄弱项、成绩）注入提示词，由 LLM 生成可解释的结构化学习建议。
     * <p>已脱离 knowledge 模块：原基于图谱关系的算法推荐引擎（另一成员负责）不再内置，本端仅做学情驱动的 LLM 推荐。</p>
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

        // 近期成绩
        if (context.containsKey("recentScores")) {
            sb.append("近期成绩：").append(context.get("recentScores")).append("\n");
        }

        sb.append("\n请根据以上真实学情数据，生成个性化学习推荐方案，优先针对薄弱项给出具体可操作的建议。");
        sb.append("\n只返回纯JSON：{\"recommendations\":[{\"resource\":\"推荐资源或行动\",\"reason\":\"基于数据的具体理由\",\"priority\":\"HIGH|MEDIUM|LOW\"}]}");

        String schema = "{\"recommendations\":[{\"resource\":\"资源名\",\"reason\":\"推荐理由\",\"priority\":\"HIGH|MEDIUM|LOW\"}]}";
        return callLlm(buildMessages(sb.toString(), null), 0.6, schema, "recommend");
    }

    /**
     * 知识图谱 AI 抽取（两阶段法，对齐 IPA 框架）。
     * 第一阶段：模拟学习者阅读理解，识别文本中的核心概念与结构；
     * 第二阶段：以专家身份归纳总结，输出结构化知识点与关系。
     * <p>说明：本方法仅产出图谱结构 JSON（供图谱服务消费），属 LLM 抽取能力，不在本端图谱存储/查询实现内。</p>
     */
    public String extractKnowledgeGraphJson(String content) {
        String schema = "{\"nodes\":[{\"name\":\"知识点名称\",\"content\":\"要点说明\",\"type\":\"CONCEPT|PRINCIPLE|METHOD|FACT\"}],"
                + "\"relations\":[{\"source\":\"源节点名称\",\"target\":\"目标节点名称\",\"relationType\":\"PREREQUISITE|INCLUDES|RELATED|EXTENDS\"}]}";

        // 阶段一：阅读理解
        String stage1Prompt = "你是一位正在学习以下课程内容的学生。请仔细阅读，识别文中的核心概念、"
                + "关键原理和方法，并用自己的话整理出主要内容框架。只需陈述理解，不要输出 JSON。\n\n" + content;

        String readingResult;
        try {
            readingResult = completeAndSanitize(
                    List.of(Map.of("role", "user", "content", stage1Prompt)), 0.3, "extract_graph");
            if (readingResult == null || readingResult.isBlank()) {
                // 退化为单阶段
                String prompt = "请从以下课程内容中抽取知识结构（知识点及其关系），仅返回符合 schema 的 JSON，不要额外说明：\n" + content;
                return callLlm(buildMessages(prompt, null), 0.3, schema, "extract_graph");
            }
        } catch (Exception e) {
            log.debug("[IPA] 阶段一失败，降级为单阶段抽取: {}", e.getMessage());
            String prompt = "请从以下课程内容中抽取知识结构（知识点及其关系），仅返回符合 schema 的 JSON，不要额外说明：\n" + content;
            return callLlm(buildMessages(prompt, null), 0.3, schema, "extract_graph");
        }

        // 阶段二：专家归纳
        String stage2Prompt = "你是一位领域专家。以下是学生对课程内容的理解摘要：\n\n" + readingResult
                + "\n\n基于以上理解，请精确抽取知识点及其关系，"
                + "仅返回符合以下 schema 的 JSON，不要额外说明：\n" + schema;

        return callLlm(buildMessages(stage2Prompt, null), 0.3, schema, "extract_graph");
    }

    /**
     * 学情 AI 建议：聚合学情统计与薄弱点，生成结构化学习建议
     */
    public String generateLearningAdviceJson(String statsJson, String weakPointsJson) {
        log.info("生成AI");

        String schema = "{\"summary\":\"一句话总体建议\",\"items\":[{\"title\":\"建议标题\",\"detail\":\"具体做法\",\"level\":\"HIGH|MEDIUM|LOW\"}]}";
        String prompt = "以下是某学习者的学情统计：" + statsJson
                + "\n以及 AI 分析出的薄弱项：" + weakPointsJson
                + "\n请结合两者给出可落地的学习建议，按优先级排列。";
        return callLlm(buildMessages(prompt, null), 0.6, schema, "recommend");
    }

    /**
     * 管理员 AI 监控：基于日志文本检测异常，返回结构化洞察
     */
    public String analyzeAnomaliesJson(String logsJson) {
        String schema = "{\"alerts\":[{\"level\":\"HIGH|MEDIUM|LOW\",\"title\":\"问题标题\",\"detail\":\"风险说明与建议\"}]}";
        String prompt = "以下是系统近期的登录与操作日志：\n" + logsJson
                + "\n请检测其中的异常行为（如暴力破解、异常高频操作、成绩异动等），仅返回符合 schema 的 JSON。";
        return callLlm(buildMessages(prompt, null), 0.3, schema, "analyze");
    }

    /**
     * 知识点薄弱项分析（结构化 JSON 输出）
     * 返回格式：{"weakPoints": [{"description": "薄弱项描述", "severity": "HIGH|MEDIUM|LOW"}]}
     */
    public String analyzeWeakPointsJson(String statsJson) {
        String schema = "{\"weakPoints\": [{\"description\": \"薄弱项描述\", \"severity\": \"HIGH 或 MEDIUM 或 LOW\"}]}";
        String prompt = "请根据以下学习统计数据，分析学习者的知识薄弱项：" + statsJson;
        return callLlm(buildMessages(prompt, null), 0.4, schema, "analyze");
    }

    /**
     * 题目推荐（结构化 JSON 输出）
     * 返回格式：{"questions": [{"content": "题目内容", "type": "SINGLE_CHOICE", "difficulty": "..."}]}
     */
    public String recommendQuestionsJson(Long courseId, String difficulty, int count) {
        String schema = "{\"questions\": [{\"content\": \"题目内容\", \"type\": \"SINGLE_CHOICE\", \"difficulty\": \""
                + (difficulty != null ? difficulty : "MEDIUM") + "\"}]}";
        String prompt = String.format("请为课程(courseId=%d)生成%d道%s难度的练习题。", courseId, count, difficulty);
        return callLlm(buildMessages(prompt, null), 0.8, schema, "question");
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
     * 将题目推荐 JSON 健壮解析为列表（兼容 ```json ``` 代码块 / 裸 JSON）。
     * 解析失败时回退为单条文本记录（由 Controller 内联实现下沉，行为保持一致）。
     */
    @Override
    public List<Map<String, Object>> parseQuestions(String jsonText, String diff) {
        List<Map<String, Object>> list = new ArrayList<>();
        String json = JsonUtils.extractJsonObject(jsonText);
        if (json == null) return list;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode arr = root.has("questions") ? root.get("questions") : root;
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    Map<String, Object> rec = new LinkedHashMap<>();
                    rec.put("content", n.has("content") ? n.get("content").asText() : n.asText());
                    rec.put("type", n.has("type") ? n.get("type").asText() : "SINGLE_CHOICE");
                    rec.put("difficulty", n.has("difficulty") ? n.get("difficulty").asText() : (diff != null ? diff : "MEDIUM"));
                    list.add(rec);
                }
            }
        } catch (Exception e) {
            log.warn("题目推荐 JSON 解析失败: {}", e.getMessage());
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("content", jsonText);
            rec.put("type", "SINGLE_CHOICE");
            rec.put("difficulty", diff != null ? diff : "MEDIUM");
            list.add(rec);
        }
        return list;
    }

    /**
     * 知识点薄弱项分析：基于用户学情统计生成薄弱点列表。
     * 失败时返回单条降级提示（不抛异常），与原有 HTTP 行为一致。
     */
    @Override
    public List<Map<String, String>> analyzeWeakPoints(Map<String, Object> userStats) {
        try {
            String statsJson = OBJECT_MAPPER.writeValueAsString(userStats);
            String json = analyzeWeakPointsJson(statsJson);
            return parseWeakPoints(json);
        } catch (Exception e) {
            log.warn("薄弱点分析失败，返回降级提示: {}", e.getMessage());
            List<Map<String, String>> fallback = new ArrayList<>();
            fallback.add(Map.of("description", "AI分析暂时不可用，请稍后重试", "severity", "LOW"));
            return fallback;
        }
    }

    /**
     * 学情 AI 建议：基于学情统计生成结构化学习建议 {"summary","items"}。
     * LLM 未返回有效内容或解析失败时抛出 BusinessException（由调用方统一包装，与原 HTTP 行为一致）。
     */
    @Override
    public Map<String, Object> generateLearningAdvice(Map<String, Object> userStats) {
        try {
            String statsJson = OBJECT_MAPPER.writeValueAsString(userStats);
            String weakJson = analyzeWeakPointsJson(statsJson);
            String adviceJson = generateLearningAdviceJson(statsJson, weakJson);
            String raw = JsonUtils.extractJsonObject(adviceJson);
            if (raw == null) {
                throw new BusinessException("AI 未返回有效建议");
            }
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("summary", root.has("summary") ? root.get("summary").asText() : "");
            List<Map<String, Object>> items = new ArrayList<>();
            if (root.has("items") && root.get("items").isArray()) {
                for (JsonNode it : root.get("items")) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("title", it.has("title") ? it.get("title").asText() : "");
                    m.put("detail", it.has("detail") ? it.get("detail").asText() : "");
                    String lvl = it.has("level") ? it.get("level").asText().toUpperCase() : "MEDIUM";
                    if (!"HIGH".equals(lvl) && !"MEDIUM".equals(lvl) && !"LOW".equals(lvl)) lvl = "MEDIUM";
                    m.put("level", lvl);
                    items.add(m);
                }
            }
            result.put("items", items);
            return result;
        } catch (Exception e) {
            log.error("生成AI失败: {}", e.getMessage(), e);

            // 原控制器将本段整体 try/catch 并重抛 BusinessException("生成学习建议失败: " + msg)；
            // 此处去前缀重抛，由控制器 catch(Exception) 统一加前缀，保证最终消息与原来一致。
            throw new BusinessException(e.getMessage());
        }
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
        recordAiCall("chat");
        String safeQuestion = promptGuard.neutralizeInjection(question);
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", safeQuestion));
        recordAiToken("chat", estimateInputTokens(messages));
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
    private String completeAndSanitize(List<Map<String, String>> messages, double temperature, String type) {
        recordAiCall(type);
        recordAiToken(type, estimateInputTokens(messages));
        try {
            return promptGuard.sanitizeOutput(llmProvider.complete(messages, temperature));
        } catch (Exception e) {
            log.error("AI相关操作失败: {}", e.getMessage(), e);

            recordAiFailure(type);
            throw e;
        }
    }

    /**
     * 统一调用入口：未配置供应商时返回清晰提示，调用失败返回通用错误文案；成功结果统一清洗后下发
     */
    private String callLlm(List<Map<String, String>> messages, Double temperature, String jsonSchema, String type) {
        if (!llmProvider.isConfigured()) {
            return NO_PROVIDER_MSG;
        }
        recordAiCall(type);
        long inputTokens = estimateInputTokens(messages);
        if (jsonSchema != null) {
            inputTokens += (long) (jsonSchema.length() / 1.5);
        }
        recordAiToken(type, inputTokens);
        try {
            String result = jsonSchema != null
                    ? llmProvider.completeJson(messages, temperature, jsonSchema)
                    : llmProvider.complete(messages, temperature);
            if (result == null) {
                recordAiFailure(type);
                return SERVICE_ERROR_MSG;
            }
            return promptGuard.sanitizeOutput(result);
        } catch (Exception e) {
            log.error("AI相关操作失败: {}", e.getMessage(), e);

            recordAiFailure(type);
            throw e;
        }
    }
}
