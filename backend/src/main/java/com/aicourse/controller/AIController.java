package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.AIService;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.LearningPathService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AI 功能接口控制器（需求文档对齐版）
 * 按照需求文档 4.13 节定义，提供9个AI功能REST API
 * 基础路径: /api/ai
 */
@RestController
@RequestMapping("/ai")
public class AIController {

    private final AIService aiService;
    private final AnalyticsService analyticsService;
    private final LearningPathService learningPathService;

    public AIController(AIService aiService,
                        AnalyticsService analyticsService,
                        LearningPathService learningPathService) {
        this.aiService = aiService;
        this.analyticsService = analyticsService;
        this.learningPathService = learningPathService;
    }

    // ==================== 智能问答（3个接口） ====================

    /**
     * 智能答疑 - RAG增强专业问题解答
     * POST /api/ai/qa/ask
     */
    @PostMapping("/qa/ask")
    public ApiResponse<Map<String, Object>> qaAsk(@RequestBody Map<String, String> body,
                                                   Authentication auth) {
        String question = body.get("question");
        String courseId = body.get("courseId");
        Long cid = courseId != null ? Long.valueOf(courseId) : null;
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        String answer = aiService.ragAsk(question, cid);
        return ApiResponse.success(Map.of(
                "answer", answer,
                "sessionId", UUID.randomUUID().toString()
        ));
    }

    /**
     * 继续对话 - 多轮上下文连续对话
     * POST /api/ai/qa/continue
     */
    @PostMapping("/qa/continue")
    public ApiResponse<Map<String, Object>> qaContinue(@RequestBody Map<String, String> body,
                                                        Authentication auth) {
        String message = body.get("message");
        String context = body.get("context");
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        String answer = aiService.chat(message, context, userId);
        return ApiResponse.success(Map.of(
                "answer", answer,
                "sessionId", body.getOrDefault("sessionId", UUID.randomUUID().toString())
        ));
    }

    /**
     * 获取会话历史
     * GET /api/ai/qa/sessions
     */
    @GetMapping("/qa/sessions")
    public ApiResponse<List<Map<String, Object>>> getSessions(Authentication auth) {
        // 返回当前用户的会话历史（后续可接入持久化存储）
        return ApiResponse.success(Collections.emptyList());
    }

    // ==================== 作文批改（2个接口） ====================

    /**
     * 作文批改 - AI自动批改作文
     * POST /api/ai/essay/correct
     */
    @PostMapping("/essay/correct")
    public ApiResponse<Map<String, String>> essayCorrect(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        String result = aiService.generateContent("correct", content);
        return ApiResponse.success(Map.of("result", result));
    }

    /**
     * 作文润色 - AI优化作文表达
     * POST /api/ai/essay/polish
     */
    @PostMapping("/essay/polish")
    public ApiResponse<Map<String, String>> essayPolish(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        String result = aiService.generateContent("polish", content);
        return ApiResponse.success(Map.of("result", result));
    }

    // ==================== 智能推荐（2个接口） ====================

    /**
     * 个性化学习推荐
     * GET /api/ai/recommendations/learning
     */
    @GetMapping("/recommendations/learning")
    public ApiResponse<Map<String, Object>> learningRecommendations(Authentication auth) {
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        Map<String, Object> result = new HashMap<>();
        if (userId != null) {
            // 基于学生学习数据生成推荐
            String recommendation = aiService.chat("请根据我的学习情况推荐学习内容", null, userId);
            result.put("recommendation", recommendation);
        }
        result.put("recommendations", Collections.emptyList());
        return ApiResponse.success(result);
    }

    /**
     * 作业题目推荐
     * GET /api/ai/recommendations/questions
     */
    @GetMapping("/recommendations/questions")
    public ApiResponse<Map<String, Object>> questionRecommendations(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String difficulty) {
        Map<String, Object> result = new HashMap<>();
        result.put("courseId", courseId);
        result.put("difficulty", difficulty);
        result.put("recommendations", Collections.emptyList());
        return ApiResponse.success(result);
    }

    // ==================== 学情分析（1个接口） ====================

    /**
     * 知识点薄弱项分析
     * GET /api/ai/analysis/weak-points
     */
    @GetMapping("/analysis/weak-points")
    public ApiResponse<Map<String, Object>> weakPointsAnalysis(
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        Map<String, Object> result = new HashMap<>();
        result.put("courseId", courseId);
        if (userId != null) {
            // 获取学生的学习数据用于分析
            Map<String, Object> userStats = analyticsService.getUserDashboard(userId);
            result.put("userStats", userStats);
        }
        result.put("weakPoints", Collections.emptyList());
        return ApiResponse.success(result);
    }

    // ==================== 学习路径（1个接口） ====================

    /**
     * 学习路径规划 - AI生成学习路径
     * POST /api/ai/learning-path/generate
     */
    @PostMapping("/learning-path/generate")
    public ApiResponse<Map<String, Object>> generateLearningPath(
            @RequestBody Map<String, String> body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String courseId = body.get("courseId");
        String goal = body.getOrDefault("goal", "掌握课程核心知识");
        Long cid = courseId != null ? Long.valueOf(courseId) : null;

        Map<String, Object> result = new HashMap<>();
        if (cid != null && userId != null) {
            try {
                String pathName = "AI学习路径";
                learningPathService.generatePath(userId, cid, pathName, goal);
                result.put("status", "created");
                result.put("message", "学习路径已生成");
            } catch (Exception e) {
                result.put("status", "pending");
                result.put("message", "AI学习路径规划功能开发中");
            }
        } else {
            result.put("status", "pending");
            result.put("message", "AI学习路径规划功能开发中");
        }
        return ApiResponse.success(result);
    }

    // ==================== 保留原有接口（兼容） ====================

    /**
     * AI 对话聊天（兼容旧接口）
     */
    @PostMapping("/chat")
    public ApiResponse<Map<String, String>> chat(@RequestBody Map<String, String> body,
                                                  Authentication auth) {
        String message = body.get("message");
        String context = body.get("context");
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        String response = aiService.chat(message, context, userId);
        return ApiResponse.success(Map.of("response", response));
    }

    /**
     * AI 生成试题（兼容旧接口）
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
     * 内容智能总结（兼容旧接口）
     */
    @PostMapping("/generate-summary")
    public ApiResponse<Map<String, String>> generateSummary(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        String summary = aiService.generateSummary(content);
        return ApiResponse.success(Map.of("summary", summary));
    }

    /**
     * 文本查重/相似度检测（兼容旧接口）
     */
    @PostMapping("/check-plagiarism")
    public ApiResponse<Map<String, String>> checkPlagiarism(@RequestBody Map<String, String> body) {
        String text1 = body.get("text1");
        String text2 = body.get("text2");
        String result = aiService.checkPlagiarism(text1, text2);
        return ApiResponse.success(Map.of("result", result));
    }

    /**
     * RAG 知识库问答（兼容旧接口）
     */
    @GetMapping("/rag/ask")
    public ApiResponse<Map<String, String>> ragAsk(
            @RequestParam @NotBlank(message = "提问内容不能为空") String question,
            @RequestParam(required = false) Long courseId) {
        String answer = aiService.ragAsk(question, courseId);
        return ApiResponse.success(Map.of("answer", answer));
    }
}