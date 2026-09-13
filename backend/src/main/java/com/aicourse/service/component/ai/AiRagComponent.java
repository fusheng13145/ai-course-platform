package com.aicourse.service.component.ai;

import com.aicourse.service.impl.PromptGuard;
import com.aicourse.service.RAGService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI RAG 问答组件：基于课程知识库（课程章节文档）的混合检索问答，检索失败降级纯 LLM。
 * <p>拆分自原 AIServiceImpl（2026-08-29）。已脱离 knowledge 模块：原 GraphRAG 多跳增强
 * 由另一成员图谱服务负责，本端不再内置图谱检索。</p>
 */
@Slf4j
@Component
public class AiRagComponent {

    private final RAGService ragService;
    private final PromptGuard promptGuard;
    private final AiCoreComponent core;

    public AiRagComponent(RAGService ragService, PromptGuard promptGuard, AiCoreComponent core) {
        this.ragService = ragService;
        this.promptGuard = promptGuard;
        this.core = core;
    }

    /**
     * 基于课程知识库（课程章节文档）的 RAG 问答（自包含：BM25/向量检索 + LLM 生成）。
     * <p>
     * 流程：将用户问题直接交由 {@link RAGService} 做混合检索 + LLM 生成；
     * 检索失败时降级为纯 LLM 调用，保证问答链路不中断。
     */
    public String ragAsk(String question, Long courseId) {
        String enhancedQuestion = question;

        try {
            String ragResult = ragService.ask(enhancedQuestion, courseId, 5);
            if (ragResult != null && !ragResult.contains("\"code\":503")) {
                String answer = core.extractRagAnswer(ragResult);
                if (answer != null && !answer.isBlank()) {
                    return promptGuard.sanitizeOutput(answer);
                }
            }
        } catch (Exception e) {
            log.warn("RAG 服务调用异常，降级为纯 LLM 调用: {}", e.getMessage());
        }
        String systemPrompt = "请基于课程知识回答问题。如果问题超出课程范围，请明确说明。";
        String raw = core.completeAndSanitize(core.buildMessages(question, systemPrompt), 0.5, "rag");
        return raw != null ? raw : AiCoreComponent.SERVICE_ERROR_MSG;
    }
}
