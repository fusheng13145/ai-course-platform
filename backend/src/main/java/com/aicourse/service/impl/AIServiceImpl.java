package com.aicourse.service.impl;

import com.aicourse.service.AIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class AIServiceImpl implements AIService {
    private static final Logger log = LoggerFactory.getLogger(AIServiceImpl.class);
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public AIServiceImpl(@Value("${ai.deepseek.api-key:}") String apiKey,
                         @Value("${ai.deepseek.base-url:https://api.deepseek.com/v1}") String baseUrl,
                         @Value("${ai.deepseek.model:deepseek-chat}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String chat(String message, String context, Long userId) {
        return callDeepSeek(buildMessages(message, context));
    }

    @Override
    public String generateQuestion(Long courseId, String type, String difficulty, int count) {
        String prompt = String.format(
            "请生成%d道%s难度、类型为%s的课程题目，包含题目内容、选项和正确答案，以JSON格式返回。",
            count, difficulty, type);
        return callDeepSeek(buildMessages(prompt, null));
    }

    @Override
    public String generateContent(String type, String params) {
        return callDeepSeek(buildMessages("请生成" + type + "内容，" + params, null));
    }

    @Override
    public String generateSummary(String content) {
        return callDeepSeek(buildMessages("请总结以下内容：\n" + content, null));
    }

    @Override
    public String checkPlagiarism(String text1, String text2) {
        return callDeepSeek(buildMessages(
            "请对比以下两段文本的相似度，返回相似百分比和重复内容分析：\n文本1：" + text1 + "\n文本2：" + text2, null));
    }

    @Override
    public String ragAsk(String question, Long courseId) {
        return callDeepSeek(buildMessages(question, "请基于课程知识回答问题。"));
    }

    @SuppressWarnings("unchecked")
    private String callDeepSeek(Object requestBody) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("DeepSeek API key not configured, returning mock response");
            return "{\"content\":\"AI服务未配置，这是模拟回复。请配置DEEPSEEK_API_KEY环境变量。\"}";
        }
        try {
            Map response = restClient.post()
                    .uri("/chat/completions")
                    .body(Map.of(
                        "model", model,
                        "messages", requestBody,
                        "temperature", 0.7
                    ))
                    .retrieve()
                    .body(Map.class);
            if (response != null && response.containsKey("choices")) {
                var choices = (List<Map>) response.get("choices");
                if (!choices.isEmpty()) {
                    var message = (Map) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
            return "AI响应解析失败";
        } catch (Exception e) {
            log.error("DeepSeek API call failed", e);
            return "AI服务暂时不可用，请稍后再试。";
        }
    }

    private Object buildMessages(String userMessage, String systemPrompt) {
        if (systemPrompt != null) {
            return List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            );
        }
        return List.of(Map.of("role", "user", "content", userMessage));
    }
}
