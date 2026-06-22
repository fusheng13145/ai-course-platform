package com.aicourse.service.impl;

import com.aicourse.service.AIService;
import com.aicourse.service.RAGService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI 服务实现类
 * 对接 DeepSeek 大模型接口，提供对话、出题、内容生成、查重、RAG 问答等能力
 */
@Service
public class AIServiceImpl implements AIService {

    private static final Logger log = LoggerFactory.getLogger(AIServiceImpl.class);
    private static final String MOCK_RESPONSE = "{\"content\":\"AI服务未配置，这是模拟回复。请配置DEEPSEEK_API_KEY环境变量。\"}";
    private static final String PARSE_FAIL_MSG = "AI响应解析失败";
    private static final String SERVICE_ERROR_MSG = "AI服务暂时不可用，请稍后再试。";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final RAGService ragService;

    public AIServiceImpl(@Value("${ai.deepseek.api-key:}") String apiKey,
                         @Value("${ai.deepseek.base-url:https://api.deepseek.com/v1}") String baseUrl,
                         @Value("${ai.deepseek.model:deepseek-chat}") String model,
                         RAGService ragService) {
        this.apiKey = apiKey;
        this.model = model;
        this.ragService = ragService;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * AI 对话交互
     *
     * @param message  用户提问消息
     * @param context 对话上下文/系统提示词
     * @param userId   用户ID
     * @return AI 回答内容
     */
    @Override
    public String chat(String message, String context, Long userId) {
        return callDeepSeek(buildMessages(message, context));
    }

    /**
     * AI 自动生成课程题目
     *
     * @param courseId   课程ID
     * @param type       题目类型
     * @param difficulty 难度等级
     * @param count      题目数量
     * @return 题目 JSON 字符串
     */
    @Override
    public String generateQuestion(Long courseId, String type, String difficulty, int count) {
        String prompt = String.format(
                "请生成%d道%s难度、类型为%s的课程题目，包含题目内容、选项和正确答案，以JSON格式返回。",
                count, difficulty, type);
        return callDeepSeek(buildMessages(prompt, null));
    }

    /**
     * 通用内容生成
     *
     * @param type   内容类型
     * @param params 扩展参数
     * @return 生成内容
     */
    @Override
    public String generateContent(String type, String params) {
        String prompt = "请生成" + type + "内容，" + params;
        return callDeepSeek(buildMessages(prompt, null));
    }

    /**
     * 文本内容摘要总结
     *
     * @param content 原始文本
     * @return 摘要结果
     */
    @Override
    public String generateSummary(String content) {
        String prompt = "请总结以下内容：\n" + content;
        return callDeepSeek(buildMessages(prompt, null));
    }

    /**
     * 文本相似度/抄袭检测
     *
     * @param text1 文本1
     * @param text2 文本2
     * @return 相似度分析结果
     */
    @Override
    public String checkPlagiarism(String text1, String text2) {
        String prompt = "请对比以下两段文本的相似度，返回相似百分比和重复内容分析：\n文本1：" + text1 + "\n文本2：" + text2;
        return callDeepSeek(buildMessages(prompt, null));
    }

    /**
     * 基于课程知识库的 RAG 问答（分层检索增强）
     * 优先调用 Python RAG 微服务，服务不可用时降级为纯 LLM 调用
     *
     * @param question 提问内容
     * @param courseId 课程ID
     * @return 问答结果（含引用来源）
     */
    @Override
    public String ragAsk(String question, Long courseId) {
        // 1. 尝试 RAG 服务（检索增强）
        if (ragService.healthCheck()) {
            try {
                String ragResult = ragService.ask(question, courseId, 5);
                if (ragResult != null && !ragResult.contains("\"code\":503")) {
                    return ragResult;
                }
            } catch (Exception e) {
                log.warn("RAG服务调用异常，降级为纯LLM调用: {}", e.getMessage());
            }
        }
        // 2. 降级：纯 LLM 调用（无检索增强，可能产生幻觉）
        String systemPrompt = "请基于课程知识回答问题。如果问题超出课程范围，请明确说明。";
        return callDeepSeek(buildMessages(question, systemPrompt));
    }

    /**
     * 调用 DeepSeek 接口统一入口（带重试机制）
     *
     * @param requestBody 请求消息体
     * @return 模型返回文本
     */
    @SuppressWarnings("unchecked")
    private String callDeepSeek(Object requestBody) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DeepSeek API Key 未配置，返回模拟响应");
            return MOCK_RESPONSE;
        }

        int retryCount = 0;
        final int maxRetries = 2;
        Exception lastException = null;
        long baseDelayMs = 1000;

        while (retryCount < maxRetries) {
            try {
                Map<String, Object> response = restClient.post()
                        .uri("/chat/completions")
                        .body(Map.of(
                                "model", model,
                                "messages", requestBody,
                                "temperature", 0.7
                        ))
                        .retrieve()
                        .body(Map.class);

                if (response == null || !response.containsKey("choices")) {
                    log.warn("DeepSeek 接口返回数据异常，无 choices 字段");
                    return PARSE_FAIL_MSG;
                }

                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (choices.isEmpty()) {
                    log.warn("DeepSeek 接口返回 choices 列表为空");
                    return PARSE_FAIL_MSG;
                }

                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                String content = (String) message.get("content");
                if (content == null || content.isEmpty()) {
                    log.warn("DeepSeek 接口返回内容为空");
                    return PARSE_FAIL_MSG;
                }
                return content;

            } catch (Exception e) {
                lastException = e;
                retryCount++;
                log.warn("AI请求失败，重试第{}次: {}", retryCount, e.getMessage());

                if (retryCount < maxRetries) {
                    try {
                        long delay = baseDelayMs * (long) Math.pow(2, retryCount - 1);
                        TimeUnit.MILLISECONDS.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("AI请求多次失败，已达到最大重试次数", lastException);
        return SERVICE_ERROR_MSG;
    }

    /**
     * 组装对话消息体
     *
     * @param userMessage   用户消息
     * @param systemPrompt  系统提示词（可为空）
     * @return 消息列表
     */
    private List<Map<String, String>> buildMessages(String userMessage, String systemPrompt) {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            return List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
            );
        }
        return List.of(Map.of("role", "user", "content", userMessage));
    }
}