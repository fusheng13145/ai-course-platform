package com.aicourse.service.component.ai;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.impl.PromptGuard;
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
 * AI RAG 问答组件：基于课程知识库（课程章节文档）的混合检索问答，检索失败降级纯 LLM。
 * <p>拆分自原 AIServiceImpl（2026-08-29）。主路径的图谱多跳增强由另一成员图谱服务负责；
 * 降级路径（RAG 不可达/未启用）从本课程知识图谱的节点（{@code knowledge_points}）取证据接地，
 * 并在无资料时显式声明“无法基于课程知识库回答”，避免零资料纯 LLM 冒充课程答案。</p>
 */
@Slf4j
@Component
public class AiRagComponent {

    /** 降级路径注入 prompt 的知识点数量上限（命中项与课程范围项分开计算）。 */
    private static final int MAX_MATCHED_POINTS = 5;
    private static final int MAX_COURSE_POINTS = 6;
    private static final int MAX_PREREQUISITES = 3;
    private static final int MAX_DESCRIPTION_LEN = 200;
    private static final String NO_COURSE_KB_ANSWER = "无法基于课程知识库回答";

    private final RAGService ragService;
    private final PromptGuard promptGuard;
    private final AiCoreComponent core;
    private final KnowledgePointService knowledgePointService;

    public AiRagComponent(RAGService ragService, PromptGuard promptGuard, AiCoreComponent core,
                          KnowledgePointService knowledgePointService) {
        this.ragService = ragService;
        this.promptGuard = promptGuard;
        this.core = core;
        this.knowledgePointService = knowledgePointService;
    }

    /**
     * 基于课程知识库（课程章节文档）的 RAG 问答（自包含：BM25/向量检索 + LLM 生成），并带回检索出处。
     * <p>
     * 流程：将用户问题直接交由 {@link RAGService} 做混合检索 + LLM 生成；检索到的片段作为
     * {@code sources} 一并返回，供界面溯源展示。检索失败时降级为纯 LLM 调用，保证问答链路不中断；
     * 降级路径先取本课程知识图谱节点作为接地证据，并显式声明资料边界（无证据时必须回答
     * {@value #NO_COURSE_KB_ANSWER}，不得编造）。
     */
    public QaAnswerVO ragAsk(String question, Long courseId) {
        try {
            String ragResult = ragService.ask(question, courseId, 5);
            if (ragResult != null && !ragResult.contains("\"code\":503")) {
                String answer = core.extractRagAnswer(ragResult);
                if (answer != null && !answer.isBlank()) {
                    return new QaAnswerVO(promptGuard.sanitizeOutput(answer), core.extractRagSources(ragResult));
                }
            }
        } catch (Exception e) {
            log.warn("RAG 服务调用异常，降级为纯 LLM 调用: {}", e.getMessage());
        }
        FallbackContext context = buildFallbackContext(question, courseId);
        // 失败已由 completeAndSanitize 抛 LLMUnavailableException（503），这里不再兜文案：
        // 兜出来的串会被控制器用 ApiResponse.success 发出，"AI 挂了"就长成"AI 答了一句客套话"。
        String raw = core.completeAndSanitize(core.buildMessages(question, context.systemPrompt()), 0.5, "rag");
        return new QaAnswerVO(raw, context.sources());
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
        List<KnowledgePoint> points;
        try {
            points = knowledgePointService.listByCourse(courseId);
        } catch (Exception e) {
            log.warn("读取课程知识点失败，降级为无知识库问答: {}", e.getMessage());
            points = List.of();
        }
        if (points == null || points.isEmpty()) {
            return new FallbackContext(
                    "你是课程助教。当前课程知识库暂无知识点。涉及本课程具体内容时，必须明确回答“"
                            + NO_COURSE_KB_ANSWER + "”，不得编造；通用概念可以回答并注明是通用知识。",
                    List.of());
        }

        String normalizedQuestion = normalize(question);
        List<KnowledgePoint> matched = points.stream()
                .filter(p -> p.getName() != null && !p.getName().isBlank())
                .filter(p -> matchesQuestion(normalizedQuestion, p.getName()))
                .sorted(pointPriority())
                .limit(MAX_MATCHED_POINTS)
                .toList();

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

        StringBuilder context = new StringBuilder();
        List<QaSourceVO> sources = new ArrayList<>();
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
            sources.add(new QaSourceVO(point.getId(), "课程知识图谱 · " + point.getName(), 0.9,
                    description.isEmpty() ? "课程知识点：" + point.getName() : description));
        }
        return new FallbackContext(
                "你是课程助教。请基于下列课程知识（来自本课程知识图谱）回答问题。\n"
                        + context
                        + "只能依据上述资料回答；资料未覆盖或无法确认时，必须明确回答“"
                        + NO_COURSE_KB_ANSWER + "”，不得编造。如果问题超出课程范围，请明确说明。",
                List.copyOf(sources));
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
