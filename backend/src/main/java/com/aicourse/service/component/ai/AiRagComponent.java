package com.aicourse.service.component.ai;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.integration.ContractPaths;
import com.aicourse.integration.rag.RagContract.RagRetrievalResult;
import com.aicourse.integration.rag.RagContract.RagSource;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.RAGService;
import com.aicourse.vo.QaAnswerVO;
import com.aicourse.vo.QaSourceVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * AI RAG 问答组件：检索由 Python RAG 引擎承担，生成由本端 LLM 网关承担。
 * <p>拆分自原 AIServiceImpl（2026-08-29）。2026-11-13 起问答主路径改为「Java 单一路径」：
 * 先调 {@code /api/v1/rag/retrieve} 取真实引擎召回（{@code mode=ENGINE}），再用本端
 * {@link AiCoreComponent} 发起真实 LLM 生成——不再调用 Python 的 {@code /rag/ask}，
 * 避免其抽取式（EXTRACTIVE）模板被当成 AI 生成答案对外返回。</p>
 * <p>检索不可用（{@code UNAVAILABLE}/{@code PLACEHOLDER}/异常/零命中）时降级为本课程知识图谱
 * 节点接地问答，无证据时显式声明“无法基于课程知识库回答”，不得编造。</p>
 */
@Slf4j
@Component
public class AiRagComponent {

    /** 检索召回条数：决定送进 prompt 的证据密度。 */
    private static final int RETRIEVE_TOP_K = 5;
    /** 降级路径注入 prompt 的知识点数量上限（命中项与课程范围项分开计算）。 */
    private static final int MAX_MATCHED_POINTS = 5;
    private static final int MAX_COURSE_POINTS = 6;
    private static final int MAX_PREREQUISITES = 3;
    private static final int MAX_DESCRIPTION_LEN = 200;
    private static final String NO_COURSE_KB_ANSWER = "无法基于课程知识库回答";

    private final RAGService ragService;
    private final AiCoreComponent core;
    private final KnowledgePointService knowledgePointService;

    public AiRagComponent(RAGService ragService, AiCoreComponent core,
                          KnowledgePointService knowledgePointService) {
        this.ragService = ragService;
        this.core = core;
        this.knowledgePointService = knowledgePointService;
    }

    /**
     * 基于课程知识库的 RAG 问答：引擎检索 + 本端 LLM 生成，并带回检索出处。
     * <p>
     * 主路径仅在检索结果 {@code mode=ENGINE} 且召回非空时成立——此时证据来自真实引擎，
     * 答案由本端大模型生成，响应 mode 标记为 {@code ENGINE}；其余情况（引擎不可用、CI 占位、
     * 零命中、调用异常）一律降级为知识点接地问答，mode 标记为 {@code LOCAL}，绝不把
     * 模型记忆或模板文案冒充引擎答案。
     */
    public QaAnswerVO ragAsk(String question, Long courseId) {
        RagRetrievalResult retrieval = retrieveFromEngine(question, courseId);
        if (retrieval != null && retrieval.engineReady()
                && retrieval.results() != null && !retrieval.results().isEmpty()) {
            return answerFromRetrieval(question, courseId, retrieval.results());
        }
        FallbackContext context = buildFallbackContext(question, courseId);
        // 失败已由 completeAndSanitize 抛 LLMUnavailableException（503），这里不再兜文案：
        // 兜出来的串会被控制器用 ApiResponse.success 发出，"AI 挂了"就长成"AI 答了一句客套话"。
        String raw = core.completeAndSanitize(core.buildMessages(question, context.systemPrompt()), 0.5, "rag");
        return new QaAnswerVO(raw, context.sources(), ContractPaths.MODE_LOCAL);
    }

    /**
     * 调引擎检索并做语义判定：只有 {@code mode=ENGINE} 的应答才算命中。
     * 未指定课程无从检索；{@code UNAVAILABLE}/{@code PLACEHOLDER}/异常一律返回 null 交给降级路径。
     */
    private RagRetrievalResult retrieveFromEngine(String question, Long courseId) {
        if (courseId == null) {
            return null;
        }
        try {
            RagRetrievalResult result = ragService.retrieveTyped(question, courseId, RETRIEVE_TOP_K);
            if (result == null || !result.engineReady()) {
                return null;
            }
            return result;
        } catch (Exception e) {
            log.warn("RAG 检索失败，降级为知识点接地问答: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 用引擎召回的课程资料 + 本课程知识图谱命中节点拼装接地 prompt，由本端 LLM 生成答案。
     */
    private QaAnswerVO answerFromRetrieval(String question, Long courseId, List<RagSource> results) {
        List<KnowledgePoint> matched = matchKnowledgePoints(loadCoursePoints(courseId), question);

        StringBuilder prompt = new StringBuilder("你是课程助教。请严格依据下列课程资料回答学生问题。\n课程资料：\n");
        for (int i = 0; i < results.size(); i++) {
            RagSource source = results.get(i);
            prompt.append('[').append(i + 1).append("] ")
                    .append(AiCoreComponent.orDefault(source.documentName(), "课程资料"))
                    .append('：')
                    .append(AiCoreComponent.orDefault(source.snippet(), ""))
                    .append('\n');
        }
        if (!matched.isEmpty()) {
            prompt.append("课程知识点（知识图谱）：\n").append(renderPointContext(matched));
        }
        prompt.append("只能依据上述资料回答；资料未覆盖或无法确认时，必须明确回答“")
                .append(NO_COURSE_KB_ANSWER)
                .append("”，不得编造。如果问题超出课程范围，请明确说明。");

        String raw = core.completeAndSanitize(core.buildMessages(question, prompt.toString()), 0.3, "rag");

        List<QaSourceVO> sources = new ArrayList<>();
        for (RagSource source : results) {
            sources.add(new QaSourceVO(source.documentId(), source.documentName(),
                    source.relevanceScore(), source.snippet()));
        }
        sources.addAll(pointSources(matched));
        return new QaAnswerVO(raw, sources, ContractPaths.MODE_ENGINE);
    }

    /**
     * 构造降级路径的接地上下文：优先用问题命中的课程知识点（含图谱前置关系）作为证据，
     * 未命中时仅给出课程知识点范围并明确“未检索到直接相关知识点”，无知识点时显式声明无知识库。
     */
    private FallbackContext buildFallbackContext(String question, Long courseId) {
        if (courseId == null) {
            return new FallbackContext(
                    "你是课程助教。当前未指定课程，无法访问课程知识库。通用概念可以回答并注明是通用知识；"
                            + "涉及具体课程内容时，必须明确回答“" + NO_COURSE_KB_ANSWER + "”，不得编造。",
                    List.of());
        }
        List<KnowledgePoint> points = loadCoursePoints(courseId);
        if (points.isEmpty()) {
            return new FallbackContext(
                    "你是课程助教。当前课程知识库暂无知识点。涉及本课程具体内容时，必须明确回答“"
                            + NO_COURSE_KB_ANSWER + "”，不得编造；通用概念可以回答并注明是通用知识。",
                    List.of());
        }

        List<KnowledgePoint> matched = matchKnowledgePoints(points, question);
        if (matched.isEmpty()) {
            String scope = points.stream()
                    .sorted(pointPriority())
                    .limit(MAX_COURSE_POINTS)
                    .map(KnowledgePoint::getName)
                    .distinct()
                    .reduce((a, b) -> a + "、" + b)
                    .orElse("");
            return new FallbackContext(
                    "你是课程助教。当前未检索到与问题直接相关的课程知识点，以下仅为该课程的知识点范围："
                            + scope
                            + "。不得声称回答来自课程资料；涉及课程具体细节且无法确认时，必须明确回答“"
                            + NO_COURSE_KB_ANSWER + "”，不得编造。通用概念可以回答并注明是通用知识。",
                    List.of());
        }

        return new FallbackContext(
                "你是课程助教。请基于下列课程知识（来自本课程知识图谱）回答问题。\n"
                        + renderPointContext(matched)
                        + "只能依据上述资料回答；资料未覆盖或无法确认时，必须明确回答“"
                        + NO_COURSE_KB_ANSWER + "”，不得编造。如果问题超出课程范围，请明确说明。",
                pointSources(matched));
    }

    /** 读取课程知识点；读取失败不阻断问答，降级为无知识库。 */
    private List<KnowledgePoint> loadCoursePoints(Long courseId) {
        if (courseId == null) {
            return List.of();
        }
        try {
            List<KnowledgePoint> points = knowledgePointService.listByCourse(courseId);
            return points == null ? List.of() : points;
        } catch (Exception e) {
            log.warn("读取课程知识点失败，降级为无知识库问答: {}", e.getMessage());
            return List.of();
        }
    }

    /** 取问题命中的知识点（名称至少 2 字且整体出现在问题中），按重要度排序后限量。 */
    private List<KnowledgePoint> matchKnowledgePoints(List<KnowledgePoint> points, String question) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        String normalizedQuestion = normalize(question);
        return points.stream()
                .filter(p -> p.getName() != null && !p.getName().isBlank())
                .filter(p -> matchesQuestion(normalizedQuestion, p.getName()))
                .sorted(pointPriority())
                .limit(MAX_MATCHED_POINTS)
                .toList();
    }

    /** 把知识点（含图谱前置关系）渲染为 prompt 证据段。 */
    private String renderPointContext(List<KnowledgePoint> matched) {
        StringBuilder context = new StringBuilder();
        for (KnowledgePoint point : matched) {
            context.append("- ").append(point.getName());
            String description = cleanDescription(point.getDescription());
            if (!description.isEmpty()) {
                context.append("：").append(description);
            }
            context.append('\n');
            List<KnowledgePoint> prerequisites = prerequisites(point.getId());
            if (!prerequisites.isEmpty()) {
                context.append("  前置知识：")
                        .append(prerequisites.stream().map(KnowledgePoint::getName)
                                .filter(n -> n != null && !n.isBlank())
                                .distinct()
                                .reduce((a, b) -> a + "、" + b)
                                .orElse(""))
                        .append('\n');
            }
        }
        return context.toString();
    }

    /** 知识点证据转问答出处，供界面溯源展示。 */
    private List<QaSourceVO> pointSources(List<KnowledgePoint> matched) {
        List<QaSourceVO> sources = new ArrayList<>();
        for (KnowledgePoint point : matched) {
            String description = cleanDescription(point.getDescription());
            sources.add(new QaSourceVO(point.getId(), "课程知识图谱 · " + point.getName(), 0.9,
                    description.isEmpty() ? "课程知识点：" + point.getName() : description));
        }
        return sources;
    }

    /** 问题与知识点名称的轻量匹配：名称至少 2 字且整体出现在问题中（忽略大小写与空白）。 */
    private boolean matchesQuestion(String normalizedQuestion, String pointName) {
        String normalizedName = normalize(pointName);
        return normalizedName.length() >= 2 && normalizedQuestion.contains(normalizedName);
    }

    private Comparator<KnowledgePoint> pointPriority() {
        return Comparator.comparingInt((KnowledgePoint p) -> p.getImportance() == null ? 0 : -p.getImportance())
                .thenComparingLong(p -> p.getId() == null ? Long.MAX_VALUE : p.getId());
    }

    /** 图谱前置关系查询失败不应阻断问答，降级为无前置知识。 */
    private List<KnowledgePoint> prerequisites(Long pointId) {
        if (pointId == null) {
            return List.of();
        }
        try {
            List<KnowledgePoint> relations = knowledgePointService.getPrerequisites(pointId);
            return relations == null ? List.of() : relations.stream().limit(MAX_PREREQUISITES).toList();
        } catch (Exception e) {
            log.warn("读取知识点前置关系失败，跳过前置知识: {}", e.getMessage());
            return List.of();
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    /** 描述可能来自教师富文本，注入前压缩换行并截断，避免超长 prompt。 */
    private String cleanDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String compact = description.replaceAll("\\s+", " ").trim();
        return compact.length() > MAX_DESCRIPTION_LEN ? compact.substring(0, MAX_DESCRIPTION_LEN) + "…" : compact;
    }

    private record FallbackContext(String systemPrompt, List<QaSourceVO> sources) {
    }
}
