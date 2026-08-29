package com.aicourse.service.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * AI 对话组件：单轮/多轮对话、流式对话、出题、内容生成、摘要、查重。
 * <p>拆分自原 AIServiceImpl（2026-08-29），底层调用委托 {@link AiCoreComponent}。</p>
 */
@Slf4j
@Component
public class AiDialogueComponent {

    private final AiCoreComponent core;

    public AiDialogueComponent(AiCoreComponent core) {
        this.core = core;
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
            return AiCoreComponent.SERVICE_ERROR_MSG;
        }
        return core.completeAndSanitize(core.neutralizeUserMessages(messages), 0.7, "chat");
    }

    /**
     * AI 自动生成课程题目
     */
    public String generateQuestion(Long courseId, String type, String difficulty, int count) {
        log.info("生成AI");

        String prompt = String.format(
                "请生成%d道%s难度、类型为%s的课程题目，包含题目内容、选项和正确答案，以JSON格式返回。",
                count, difficulty, type);
        String raw = core.completeAndSanitize(core.buildMessages(prompt, null), 0.9, "question");
        return raw != null ? raw : AiCoreComponent.SERVICE_ERROR_MSG;
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
                    + "先逐条指出问题，再给出修改后的完整文本。若文本无明显问题，也请明确说明。\n文本内容：\n" + params;
        } else if ("polish".equals(type)) {
            prompt = "请在不改变原意的前提下，润色以下文本，使其表达更流畅、专业、优美，"
                    + "保留原有的 Markdown 结构（如标题、列表）。直接返回润色后的文本。\n文本内容：\n" + params;
        } else {
            prompt = "请生成" + type + "内容，" + params;
        }
        String raw = core.completeAndSanitize(core.buildMessages(prompt, null), 0.7, "content");
        return raw != null ? raw : AiCoreComponent.SERVICE_ERROR_MSG;
    }

    /**
     * 文本内容摘要总结
     */
    public String generateSummary(String content) {
        log.info("生成AI");

        String prompt = "请总结以下内容：\n" + content;
        String raw = core.completeAndSanitize(core.buildMessages(prompt, null), 0.3, "content");
        return raw != null ? raw : AiCoreComponent.SERVICE_ERROR_MSG;
    }

    /**
     * 文本相似度/抄袭检测
     */
    public String checkPlagiarism(String text1, String text2) {
        String prompt = "请对比以下两段文本的相似度，返回相似百分比和重复内容分析：\n文本1：" + text1 + "\n文本2：" + text2;
        String raw = core.completeAndSanitize(core.buildMessages(prompt, null), 0.2, "correct");
        return raw != null ? raw : AiCoreComponent.SERVICE_ERROR_MSG;
    }

    /**
     * 流式 AI 对话：将用户问题经 PromptGuard 注入中和后，委托至 LLM 供应商流式接口，
     * 以 Flux 逐 token 推送内容增量，供 SSE 端点使用。
     */
    public Flux<String> streamChat(String question) {
        if (!core.isProviderEnabled()) {
            return Flux.just(AiCoreComponent.NO_PROVIDER_MSG);
        }
        core.recordAiCall("chat");
        String safeQuestion = core.neutralizeInjection(question);
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", safeQuestion));
        core.recordAiToken("chat", core.estimateInputTokens(messages));
        return core.stream(messages, 0.7);
    }
}
