package com.aicourse.service.component.ai;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.integration.ContractPaths;
import com.aicourse.integration.rag.RagContract.RagSource;
import com.aicourse.vo.QaAnswerVO;
import com.aicourse.vo.QaSourceVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI RAG 问答组件：检索由 Python RAG 引擎承担，生成由本端 LLM 网关承担。
 * <p>拆分自原 AIServiceImpl（2026-08-29）。2026-11-13 起问答主路径改为「Java 单一路径」：
 * 先调 {@code /api/v1/rag/retrieve} 取真实引擎召回（{@code mode=ENGINE}），再用本端
 * {@link AiCoreComponent} 发起真实 LLM 生成——不再调用 Python 的 {@code /rag/ask}，
 * 避免其抽取式（EXTRACTIVE）模板被当成 AI 生成答案对外返回。</p>
 * <p>检索不可用（{@code UNAVAILABLE}/{@code PLACEHOLDER}/异常/零命中）时降级为本课程知识图谱
 * 节点接地问答，无证据时显式声明“无法基于课程知识库回答”，不得编造。</p>
 * <p>课程证据的召回、知识点匹配与渲染统一委托 {@link CourseEvidenceComponent}，与出题/批改复用同一口径。</p>
 */
@Slf4j
@Component
public class AiRagComponent {

    /** 检索召回条数：决定送进 prompt 的证据密度。 */
    private static final int RETRIEVE_TOP_K = 5;

    private final CourseEvidenceComponent evidence;
    private final AiCoreComponent core;

    public AiRagComponent(CourseEvidenceComponent evidence, AiCoreComponent core) {
        this.evidence = evidence;
        this.core = core;
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
        List<RagSource> results = evidence.retrieve(question, courseId, RETRIEVE_TOP_K);
        if (!results.isEmpty()) {
            return answerFromRetrieval(question, courseId, results);
        }
        FallbackContext context = buildFallbackContext(question, courseId);
        // 失败已由 completeAndSanitize 抛 LLMUnavailableException（503），这里不再兜文案：
        // 兜出来的串会被控制器用 ApiResponse.success 发出，"AI 挂了"就长成"AI 答了一句客套话"。
        String raw = core.completeAndSanitize(core.buildMessages(question, context.systemPrompt()), 0.5, "rag");
        return new QaAnswerVO(raw, context.sources(), ContractPaths.MODE_LOCAL);
    }

    /**
     * 用引擎召回的课程资料 + 本课程知识图谱命中节点拼装接地 prompt，由本端 LLM 生成答案。
     */
    private QaAnswerVO answerFromRetrieval(String question, Long courseId, List<RagSource> results) {
        List<KnowledgePoint> matched = evidence.matchPoints(evidence.loadPoints(courseId), question);

        StringBuilder prompt = new StringBuilder("你是课程助教。请严格依据下列课程资料回答学生问题。\n课程资料：\n");
        prompt.append(evidence.renderSources(results));
        if (!matched.isEmpty()) {
            prompt.append("课程知识点（知识图谱）：\n").append(evidence.renderPoints(matched));
        }
        prompt.append("只能依据上述资料回答；资料未覆盖或无法确认时，必须明确回答“")
                .append(CourseEvidenceComponent.NO_COURSE_KB_ANSWER)
                .append("”，不得编造。如果问题超出课程范围，请明确说明。");

        String raw = core.completeAndSanitize(core.buildMessages(question, prompt.toString()), 0.3, "rag");

        List<QaSourceVO> sources = new ArrayList<>();
        for (RagSource source : results) {
            sources.add(new QaSourceVO(source.documentId(), source.documentName(),
                    source.relevanceScore(), source.snippet()));
        }
        sources.addAll(evidence.pointSources(matched));
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
                            + "涉及具体课程内容时，必须明确回答“" + CourseEvidenceComponent.NO_COURSE_KB_ANSWER
                            + "”，不得编造。",
                    List.of());
        }
        List<KnowledgePoint> points = evidence.loadPoints(courseId);
        if (points.isEmpty()) {
            return new FallbackContext(
                    "你是课程助教。当前课程知识库暂无知识点。涉及本课程具体内容时，必须明确回答“"
                            + CourseEvidenceComponent.NO_COURSE_KB_ANSWER + "”，不得编造；通用概念可以回答并注明是通用知识。",
                    List.of());
        }

        List<KnowledgePoint> matched = evidence.matchPoints(points, question);
        if (matched.isEmpty()) {
            String scope = evidence.scopeText(points);
            return new FallbackContext(
                    "你是课程助教。当前未检索到与问题直接相关的课程知识点，以下仅为该课程的知识点范围："
                            + scope
                            + "。不得声称回答来自课程资料；涉及课程具体细节且无法确认时，必须明确回答“"
                            + CourseEvidenceComponent.NO_COURSE_KB_ANSWER + "”，不得编造。通用概念可以回答并注明是通用知识。",
                    List.of());
        }

        return new FallbackContext(
                "你是课程助教。请基于下列课程知识（来自本课程知识图谱）回答问题。\n"
                        + evidence.renderPoints(matched)
                        + "只能依据上述资料回答；资料未覆盖或无法确认时，必须明确回答“"
                        + CourseEvidenceComponent.NO_COURSE_KB_ANSWER + "”，不得编造。如果问题超出课程范围，请明确说明。",
                evidence.pointSources(matched));
    }

    private record FallbackContext(String systemPrompt, List<QaSourceVO> sources) {
    }
}
