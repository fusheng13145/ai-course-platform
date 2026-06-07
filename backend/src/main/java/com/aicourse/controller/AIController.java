package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.AIService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AIController {
    private final AIService aiService;

    public AIController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public ApiResponse<Map<String, String>> chat(@RequestBody Map<String, String> body) {
        String response = aiService.chat(body.get("message"), body.get("context"), null);
        return ApiResponse.success(Map.of("response", response));
    }

    @PostMapping("/generate/question")
    public ApiResponse<Map<String, String>> generateQuestion(@RequestBody Map<String, Object> body) {
        String response = aiService.generateQuestion(
            Long.valueOf(body.get("courseId").toString()),
            (String) body.get("type"),
            (String) body.get("difficulty"),
            (Integer) body.getOrDefault("count", 5));
        return ApiResponse.success(Map.of("questions", response));
    }

    @PostMapping("/summary")
    public ApiResponse<Map<String, String>> generateSummary(@RequestBody Map<String, String> body) {
        String response = aiService.generateSummary(body.get("content"));
        return ApiResponse.success(Map.of("summary", response));
    }

    @PostMapping("/plagiarism")
    public ApiResponse<Map<String, String>> checkPlagiarism(@RequestBody Map<String, String> body) {
        String response = aiService.checkPlagiarism(body.get("text1"), body.get("text2"));
        return ApiResponse.success(Map.of("result", response));
    }

    @GetMapping("/rag/ask")
    public ApiResponse<Map<String, String>> ragAsk(@RequestParam String question,
                                                    @RequestParam(required = false) Long courseId) {
        String response = aiService.ragAsk(question, courseId);
        return ApiResponse.success(Map.of("answer", response));
    }
}
