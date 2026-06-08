package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.AIService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 功能接口控制器
 */
@RestController
@RequestMapping("/ai")
public class AIController {

    private final AIService aiService;

    // 构造器注入
    public AIController(AIService aiService) {
        this.aiService = aiService;
    }

    /**
     * AI 对话聊天
     * @param body 请求体：message 对话内容, context 上下文
     * @return 对话回复
     */
    @PostMapping("/chat")
    public ApiResponse<Map<String, String>> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        String context = body.get("context");
        String response = aiService.chat(message, context, null);
        return ApiResponse.success(Map.of("response", response));
    }

    /**
     * AI 生成试题
     * @param body 请求体
     * @return 生成题目内容
     */
    @PostMapping("/generate-question")
    public ApiResponse<Map<String, String>> generateQuestion(@RequestBody Map<String, Object> body) {
        Long courseId = Long.valueOf(body.get("courseId").toString());
        String type = (String) body.get("type");
        String difficulty = (String) body.get("difficulty");
        Integer count = (Integer) body.getOrDefault("count", 5);

        String response = aiService.generateQuestion(courseId, type, difficulty, count);
        return ApiResponse.success(Map.of("questions", response));
    }

    /**
     * 内容智能总结
     * @param body 请求体：content 待总结文本
     * @return 总结内容
     */
    @PostMapping("/generate-summary")
    public ApiResponse<Map<String, String>> generateSummary(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        String summary = aiService.generateSummary(content);
        return ApiResponse.success(Map.of("summary", summary));
    }

    /**
     * 文本查重/相似度检测
     * @param body 请求体：text1 文本1, text2 文本2
     * @return 检测结果
     */
    @PostMapping("/check-plagiarism")
    public ApiResponse<Map<String, String>> checkPlagiarism(@RequestBody Map<String, String> body) {
        String text1 = body.get("text1");
        String text2 = body.get("text2");
        String result = aiService.checkPlagiarism(text1, text2);
        return ApiResponse.success(Map.of("result", result));
    }

    /**
     * RAG 知识库问答
     * @param question 提问内容
     * @param courseId 课程ID(可选)
     * @return 问答结果
     */
    @GetMapping("/rag/ask")
    public ApiResponse<Map<String, String>> ragAsk(
            @RequestParam @NotBlank(message = "提问内容不能为空") String question,
            @RequestParam(required = false) Long courseId) {
        String answer = aiService.ragAsk(question, courseId);
        return ApiResponse.success(Map.of("answer", answer));
    }
}