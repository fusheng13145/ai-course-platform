package com.aicourse.service.impl;

import com.aicourse.llm.LLMProvider;
import com.aicourse.service.AIService;
import com.aicourse.service.PromptGuard;
import com.aicourse.service.RAGService;
import com.aicourse.service.component.AiCoreComponent;
import com.aicourse.service.component.AiDialogueComponent;
import com.aicourse.service.component.AiRagComponent;
import com.aicourse.service.component.AiStructuredComponent;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * AI 服务门面：对话/流式/结构化输出/RAG 问答职责委托给 service.component 包下协作者组件。
 * <p>拆分自原 658 行单类（2026-08-29）。通过可插拔的 {@link LLMProvider} 对接各类大模型
 * （DeepSeek/OpenAI/Qwen），供应商由配置 {@code ai.provider} 决定，与业务解耦。</p>
 */
@Service
public class AIServiceImpl implements AIService {

    private final AiDialogueComponent dialogueComponent;
    private final AiRagComponent ragComponent;
    private final AiStructuredComponent structuredComponent;
    private final AiCoreComponent coreComponent;

    @Autowired
    public AIServiceImpl(AiDialogueComponent dialogueComponent,
                         AiRagComponent ragComponent,
                         AiStructuredComponent structuredComponent,
                         AiCoreComponent coreComponent) {
        this.dialogueComponent = dialogueComponent;
        this.ragComponent = ragComponent;
        this.structuredComponent = structuredComponent;
        this.coreComponent = coreComponent;
    }

    /**
     * 兼容构造器（临时）：组装真实组件 + 调用方注入的底层依赖。
     * <p>供旧单测沿用（原 4 参数签名），组件单测重构完成后移除。</p>
     */
    @Deprecated
    public AIServiceImpl(LLMProvider llmProvider, RAGService ragService, PromptGuard promptGuard,
                         MeterRegistry meterRegistry) {
        this.coreComponent = new AiCoreComponent(llmProvider, promptGuard, meterRegistry);
        this.dialogueComponent = new AiDialogueComponent(this.coreComponent);
        this.ragComponent = new AiRagComponent(ragService, promptGuard, this.coreComponent);
        this.structuredComponent = new AiStructuredComponent(this.coreComponent);
    }

    // ===================== 对话/生成 =====================

    @Override
    public String chat(String message, String context, Long userId) {
        return dialogueComponent.chat(message, context, userId);
    }

    @Override
    public String chat(List<Map<String, String>> messages, Long userId) {
        return dialogueComponent.chat(messages, userId);
    }

    @Override
    public String generateQuestion(Long courseId, String type, String difficulty, int count) {
        return dialogueComponent.generateQuestion(courseId, type, difficulty, count);
    }

    @Override
    public String generateContent(String type, String params) {
        return dialogueComponent.generateContent(type, params);
    }

    @Override
    public String generateSummary(String content) {
        return dialogueComponent.generateSummary(content);
    }

    @Override
    public String checkPlagiarism(String text1, String text2) {
        return dialogueComponent.checkPlagiarism(text1, text2);
    }

    @Override
    public Flux<String> streamChat(String question) {
        return dialogueComponent.streamChat(question);
    }

    // ===================== RAG 问答 =====================

    @Override
    public String ragAsk(String question, Long courseId) {
        return ragComponent.ragAsk(question, courseId);
    }

    // ===================== 结构化输出/解析 =====================

    @Override
    public String analyzeWeakPointsJson(String statsJson) {
        return structuredComponent.analyzeWeakPointsJson(statsJson);
    }

    @Override
    public List<Map<String, String>> parseWeakPoints(String jsonText) {
        return structuredComponent.parseWeakPoints(jsonText);
    }

    @Override
    public String recommendQuestionsJson(Long courseId, String difficulty, int count) {
        return structuredComponent.recommendQuestionsJson(courseId, difficulty, count);
    }

    @Override
    public String recommendLearningJson(String courseList) {
        return structuredComponent.recommendLearningJson(courseList);
    }

    @Override
    public String recommendLearningWithContext(Map<String, Object> context) {
        return structuredComponent.recommendLearningWithContext(context);
    }

    @Override
    public String extractKnowledgeGraphJson(String content) {
        return structuredComponent.extractKnowledgeGraphJson(content);
    }

    @Override
    public String generateLearningAdviceJson(String statsJson, String weakPointsJson) {
        return structuredComponent.generateLearningAdviceJson(statsJson, weakPointsJson);
    }

    @Override
    public String analyzeAnomaliesJson(String logsJson) {
        return structuredComponent.analyzeAnomaliesJson(logsJson);
    }

    @Override
    public List<Map<String, Object>> parseQuestions(String jsonText, String diff) {
        return structuredComponent.parseQuestions(jsonText, diff);
    }

    @Override
    public List<Map<String, String>> analyzeWeakPoints(Map<String, Object> userStats) {
        return structuredComponent.analyzeWeakPoints(userStats);
    }

    @Override
    public Map<String, Object> generateLearningAdvice(Map<String, Object> userStats) {
        return structuredComponent.generateLearningAdvice(userStats);
    }

    // ===================== 供应商状态 =====================

    @Override
    public boolean isProviderConfigured() {
        return coreComponent.isProviderConfigured();
    }

    /**
     * 当供应商未配置 Key 时返回的统一提示（非接口方法，供既有调用方/测试使用）
     */
    public String noProviderMessage() {
        return coreComponent.noProviderMessage();
    }
}
