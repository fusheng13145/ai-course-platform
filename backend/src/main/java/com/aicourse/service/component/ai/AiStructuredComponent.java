package com.aicourse.service.component.ai;

import com.aicourse.common.JsonUtils;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.exception.BusinessException;
import com.aicourse.exception.LLMUnavailableException;
import com.aicourse.integration.rag.RagContract.RagSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 结构化输出组件：推荐/抽取/建议/监控等 JSON 生成方法与健壮解析。
 * <p>拆分自原 AIServiceImpl（2026-08-29），底层调用委托 {@link AiCoreComponent}。</p>
 */
@Slf4j
@Component
public class AiStructuredComponent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 出题时注入的课程资料召回条数（与 {@code AiDialogueComponent} 出题口径一致）。 */
    private static final int QUESTION_RETRIEVE_TOP_K = 5;

    private final AiCoreComponent core;
    private final CourseEvidenceComponent evidence;

    public AiStructuredComponent(AiCoreComponent core, CourseEvidenceComponent evidence) {
        this.core = core;
        this.evidence = evidence;
    }

    // ===================== 结构化 JSON 生成 =====================

    /**
     * 个性化学习建议（结构化 JSON 输出，便于前端直接渲染）
     */
    public String recommendLearningJson(String courseList) {
        String schema = "{\"recommendations\": [{\"resource\": \"资源名\", \"reason\": \"推荐理由\"}]}";
        String prompt = "我目前在学习以下课程：" + courseList + "。请为我推荐个性化的学习资源和学习建议。";
        return core.callLlm(core.buildMessages(prompt, null), 0.7, schema, "recommend");
    }

    /**
     * 基于真实学情数据的学习推荐（LLM 引擎）：
     * <p>
     * 将学情画像（课程、进度、薄弱项、成绩）注入提示词，由 LLM 生成可解释的结构化学习建议。
     * <p>已脱离 knowledge 模块：原基于图谱关系的算法推荐引擎（另一成员负责）不再内置，本端仅做学情驱动的 LLM 推荐。</p>
     */
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
        return core.callLlm(core.buildMessages(sb.toString(), null), 0.6, schema, "recommend");
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
            readingResult = core.completeAndSanitize(
                    List.of(Map.of("role", "user", "content", stage1Prompt)), 0.3, "extract_graph");
            if (readingResult == null || readingResult.isBlank()) {
                // 退化为单阶段
                String prompt = "请从以下课程内容中抽取知识结构（知识点及其关系），仅返回符合 schema 的 JSON，不要额外说明：\n" + content;
                return core.callLlm(core.buildMessages(prompt, null), 0.3, schema, "extract_graph");
            }
        } catch (Exception e) {
            // 阶段一失败（含空白响应、401）降级为单阶段：这是一条真实的恢复路径，抽到的仍是模型原文。
            // 诚实性由单阶段自己保证——它若同样失败会抛 LLMUnavailableException(503)，
            // 绝不会拿"退化成单阶段"这件事去伪造抽取结果（DR-74-B 复核后保留本降级）。
            log.debug("[IPA] 阶段一失败，降级为单阶段抽取: {}", e.getMessage());
            String prompt = "请从以下课程内容中抽取知识结构（知识点及其关系），仅返回符合 schema 的 JSON，不要额外说明：\n" + content;
            return core.callLlm(core.buildMessages(prompt, null), 0.3, schema, "extract_graph");
        }

        // 阶段二：专家归纳
        String stage2Prompt = "你是一位领域专家。以下是学生对课程内容的理解摘要：\n\n" + readingResult
                + "\n\n基于以上理解，请精确抽取知识点及其关系，"
                + "仅返回符合以下 schema 的 JSON，不要额外说明：\n" + schema;

        return core.callLlm(core.buildMessages(stage2Prompt, null), 0.3, schema, "extract_graph");
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
        return core.callLlm(core.buildMessages(prompt, null), 0.6, schema, "recommend");
    }

    /**
     * 管理员 AI 监控：基于日志文本检测异常，返回结构化洞察
     */
    public String analyzeAnomaliesJson(String logsJson) {
        String schema = "{\"alerts\":[{\"level\":\"HIGH|MEDIUM|LOW\",\"title\":\"问题标题\",\"detail\":\"风险说明与建议\"}]}";
        String prompt = "以下是系统近期的登录与操作日志：\n" + logsJson
                + "\n请检测其中的异常行为（如暴力破解、异常高频操作、成绩异动等），仅返回符合 schema 的 JSON。";
        return core.callLlm(core.buildMessages(prompt, null), 0.3, schema, "analyze");
    }

    /**
     * 知识点薄弱项分析（结构化 JSON 输出）
     * 返回格式：{"weakPoints": [{"description": "薄弱项描述", "severity": "HIGH|MEDIUM|LOW"}]}
     */
    public String analyzeWeakPointsJson(String statsJson) {
        String schema = "{\"weakPoints\": [{\"description\": \"薄弱项描述\", \"severity\": \"HIGH 或 MEDIUM 或 LOW\"}]}";
        // 2026-11-14 P2-1（红线②）：stats 内含 knowledgeMastery（知识点级掌握度，来自课程
        // 知识图谱）时显式点名，让薄弱项定位落在图谱知识点维度，而非只看聚合统计推断
        String prompt = "请根据以下学习统计数据，分析学习者的知识薄弱项。"
                + "若数据中含 knowledgeMastery（按课程知识图谱知识点给出的掌握度，升序排列），"
                + "薄弱项必须对应到具体知识点名称并按掌握度定位：" + statsJson;
        return core.callLlm(core.buildMessages(prompt, null), 0.4, schema, "analyze");
    }

    /**
     * 题目推荐（结构化 JSON 输出）
     * 返回格式：{"questions": [{"content": "题目内容", "type": "SINGLE_CHOICE", "difficulty": "...",
     * "knowledgePoint": "该题对应的课程知识点名称"}]}
     */
    public String recommendQuestionsJson(Long courseId, String difficulty, int count) {
        String diff = AiCoreComponent.orDefault(difficulty, "MEDIUM");
        String schema = "{\"questions\": [{\"content\": \"题目内容\", \"type\": \"SINGLE_CHOICE\", \"difficulty\": \""
                + diff + "\", \"knowledgePoint\": \"该题对应的课程知识点名称\"}]}";
        // courseId 为空时整段课程限定语要省略：String.format 会把 null 渲染成字面量 "null"，
        // "null难度" 之类的污染会直接压低生成质量。
        String scope = courseId != null ? "为课程(courseId=" + courseId + ")" : "";
        String prompt = String.format("请%s生成%d道%s难度的练习题。", scope, count, diff);
        // 课程接地：与 AiDialogueComponent.generateQuestion 同一口径，用真实资料召回 + 知识图谱
        // 知识点约束出题范围；证据为空时原样返回，不改变原有生成行为。
        List<KnowledgePoint> points = evidence.loadPoints(courseId);
        String query = questionQuery(points, diff);
        List<RagSource> sources = evidence.retrieve(query, courseId, QUESTION_RETRIEVE_TOP_K);
        List<KnowledgePoint> grounded = evidence.matchPoints(points, query);
        return core.callLlm(core.buildMessages(withGrounding(prompt, sources, grounded), null), 0.8, schema, "question");
    }

    /**
     * 出题证据检索用的查询：本课程全部知识点名称以顿号连接（含全部名称才能让
     * {@link CourseEvidenceComponent#matchPoints} 按重要度取前 5）；无知识点时退回难度，缺省则不检索。
     */
    private String questionQuery(List<KnowledgePoint> points, String difficulty) {
        String names = points.stream()
                .map(KnowledgePoint::getName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .reduce((a, b) -> a + "、" + b)
                .orElse("");
        return names.isEmpty() ? AiCoreComponent.orDefault(difficulty, "").trim() : names;
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

    // ===================== 健壮解析 =====================

    /**
     * 解析薄弱点 JSON 为列表（健壮：解析失败回退为单条文本）
     */
    public List<Map<String, String>> parseWeakPoints(String jsonText) {
        List<Map<String, String>> result = new ArrayList<>();
        if (jsonText == null) {
            return result;
        }
        String json = JsonUtils.extractJsonObject(jsonText);
        // extractJsonObject 仅提取首个 {...} 对象，会丢失根数组；优先用原文解析以保留数组结构
        String trimmed = jsonText.trim();
        String toParse = (trimmed.startsWith("[") || trimmed.startsWith("{"))
                ? jsonText : (json != null ? json : jsonText);
        try {
            JsonNode root = OBJECT_MAPPER.readTree(toParse);
            JsonNode arr = root.has("weakPoints") ? root.get("weakPoints") : root;
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String desc = n.has("description") ? n.get("description").asText() : n.asText();
                    String sev = n.has("severity") ? n.get("severity").asText().toUpperCase() : "MEDIUM";
                    if (!"HIGH".equals(sev) && !"MEDIUM".equals(sev) && !"LOW".equals(sev)) sev = "MEDIUM";
                    result.add(Map.of("description", desc, "severity", sev));
                }
            } else if (root.isObject() && !root.isEmpty()) {
                // 根对象（无 weakPoints 数组）-> 单条 MEDIUM
                String desc = root.has("description") ? root.get("description").asText() : jsonText.trim();
                result.add(Map.of("description", desc, "severity", "MEDIUM"));
            }
        } catch (Exception e) {
            log.warn("薄弱点 JSON 解析失败，回退文本: {}", e.getMessage());
            if (!jsonText.isBlank()) {
                result.add(Map.of("description", jsonText.trim(), "severity", "MEDIUM"));
            }
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
    public List<Map<String, Object>> parseQuestions(String jsonText, String diff) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (jsonText == null) {
            return list;
        }
        String json = JsonUtils.extractJsonObject(jsonText);
        // extractJsonObject 仅提取首个 {...} 对象，会丢失根数组；优先用原文解析以保留数组结构
        String trimmed = jsonText.trim();
        String toParse = (trimmed.startsWith("[") || trimmed.startsWith("{"))
                ? jsonText : (json != null ? json : jsonText);
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(toParse);
        } catch (Exception e) {
            // 非法 JSON -> 回退为单条文本记录
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("content", jsonText);
            rec.put("type", "SINGLE_CHOICE");
            rec.put("difficulty", diff != null ? diff : "MEDIUM");
            list.add(rec);
            return list;
        }
        JsonNode arr = root.has("questions") ? root.get("questions") : root;
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("content", n.has("content") ? n.get("content").asText() : n.asText());
                rec.put("type", n.has("type") ? n.get("type").asText() : "SINGLE_CHOICE");
                rec.put("difficulty", n.has("difficulty") ? n.get("difficulty").asText() : (diff != null ? diff : "MEDIUM"));
                putKnowledgePoint(rec, n);
                list.add(rec);
            }
        } else if (root.isObject() && !root.isEmpty()) {
            // 根对象（无 questions 数组）-> 单条
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("content", root.has("content") ? root.get("content").asText() : jsonText);
            rec.put("type", "SINGLE_CHOICE");
            rec.put("difficulty", diff != null ? diff : "MEDIUM");
            putKnowledgePoint(rec, root);
            list.add(rec);
        }
        return list;
    }

    /** 仅当节点带非空 {@code knowledgePoint} 时透传该键（缺失/空值不落键，也不报错）。 */
    private void putKnowledgePoint(Map<String, Object> rec, JsonNode node) {
        JsonNode knowledgePoint = node.get("knowledgePoint");
        if (knowledgePoint != null && !knowledgePoint.isNull() && !knowledgePoint.asText().isBlank()) {
            rec.put("knowledgePoint", knowledgePoint.asText());
        }
    }

    /**
     * 知识点薄弱项分析：基于用户学情统计生成薄弱点列表。
     * 失败时返回单条降级提示（不抛异常），与原有 HTTP 行为一致。
     */
    public List<Map<String, String>> analyzeWeakPoints(Map<String, Object> userStats) {
        try {
            String statsJson = OBJECT_MAPPER.writeValueAsString(userStats);
            String json = analyzeWeakPointsJson(statsJson);
            return parseWeakPoints(json);
        } catch (LLMUnavailableException e) {
            // 上游不可用不是"分析结论"，不能在这里退化成一条 severity=LOW 的"AI分析暂时不可用"条目：
            // 那等于让 /analysis/weak-points 对 AI 故障返回 200 + 假装分析过的低风险提示
            throw e;
        } catch (Exception e) {
            log.warn("薄弱点分析失败，返回降级提示: {}", e.getMessage());
            List<Map<String, String>> fallback = new ArrayList<>();
            fallback.add(Map.of("description", "AI分析暂时不可用，请稍后重试", "severity", "LOW"));
            return fallback;
        }
    }

    /**
     * 学情 AI 建议：基于学情统计生成结构化学习建议 {"summary","items"}。
     * LLM 未返回有效内容或解析失败时抛 {@link LLMUnavailableException}(503)：
     * 这是上游输出不可用，不是请求参数错误，不能与入参 400 混用（DR-74 同口径）。
     */
    public Map<String, Object> generateLearningAdvice(Map<String, Object> userStats) {
        try {
            String statsJson = OBJECT_MAPPER.writeValueAsString(userStats);
            String weakJson = analyzeWeakPointsJson(statsJson);
            String adviceJson = generateLearningAdviceJson(statsJson, weakJson);
            String raw = JsonUtils.extractJsonObject(adviceJson);
            if (raw == null) {
                throw new LLMUnavailableException(LLMUnavailableException.Kind.EMPTY_RESPONSE,
                        "AI 返回内容无法解析，请稍后重试");
            }
            JsonNode root;
            try {
                root = OBJECT_MAPPER.readTree(raw);
            } catch (Exception parseEx) {
                throw new LLMUnavailableException(LLMUnavailableException.Kind.EMPTY_RESPONSE,
                        "AI 返回内容无法解析，请稍后重试", parseEx);
            }
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
        } catch (LLMUnavailableException e) {
            // 保持 503：上游故障与模型输出不可解析都不该长成"你的请求不合法"
            throw e;
        } catch (Exception e) {
            log.error("生成AI失败: {}", e.getMessage(), e);
            // 原控制器将本段整体 try/catch 并重抛 BusinessException("生成学习建议失败: " + msg)；
            // 此处去前缀重抛，由控制器 catch(Exception) 统一加前缀，保证最终消息与原来一致。
            throw new BusinessException(e.getMessage());
        }
    }
}
