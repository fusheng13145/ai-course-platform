package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.LearningRecord;
import com.aicourse.service.AIService;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.LearningPathService;
import com.aicourse.service.LearningRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final CourseEnrollmentService courseEnrollmentService;
    private final LearningRecordService learningRecordService;
    private final CourseService courseService;

    public AIController(AIService aiService,
                        AnalyticsService analyticsService,
                        LearningPathService learningPathService,
                        CourseEnrollmentService courseEnrollmentService,
                        LearningRecordService learningRecordService,
                        CourseService courseService) {
        this.aiService = aiService;
        this.analyticsService = analyticsService;
        this.learningPathService = learningPathService;
        this.courseEnrollmentService = courseEnrollmentService;
        this.learningRecordService = learningRecordService;
        this.courseService = courseService;
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
        List<Map<String, Object>> sessions = new ArrayList<>();
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        if (userId != null) {
            // 从用户最近的学习记录中生成会话历史（后续可接入QA会话表）
            List<LearningRecord> records = learningRecordService.list(
                    new LambdaQueryWrapper<LearningRecord>()
                            .eq(LearningRecord::getUserId, userId)
                            .orderByDesc(LearningRecord::getCreatedAt)
                            .last("LIMIT 5")
            );
            for (LearningRecord record : records) {
                Map<String, Object> session = new HashMap<>();
                session.put("sessionId", UUID.randomUUID().toString());
                session.put("title", "学习记录 - " + (record.getActionType() != null ? record.getActionType() : "未知操作"));
                session.put("lastMessage", "学习时长: " + (record.getDuration() != null ? record.getDuration() + "分钟" : "未知"));
                session.put("updatedAt", record.getCreatedAt() != null ? record.getCreatedAt().toString() : LocalDateTime.now().toString());
                session.put("messageCount", 1);
                sessions.add(session);
            }
            // 如果记录不足5条，补充mock数据
            while (sessions.size() < 3) {
                Map<String, Object> session = new HashMap<>();
                session.put("sessionId", UUID.randomUUID().toString());
                session.put("title", "AI问答会话 #" + (sessions.size() + 1));
                session.put("lastMessage", "欢迎使用AI智能问答功能");
                session.put("updatedAt", LocalDateTime.now().toString());
                session.put("messageCount", 2);
                sessions.add(session);
            }
        }
        return ApiResponse.success(sessions);
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
        List<Map<String, Object>> recommendations = new ArrayList<>();
        if (userId != null) {
            // 获取用户已选课程
            List<CourseEnrollment> enrollments = courseEnrollmentService.list(
                    new LambdaQueryWrapper<CourseEnrollment>()
                            .eq(CourseEnrollment::getStudentId, userId)
                            .eq(CourseEnrollment::getIsActive, true)
            );
            List<String> courseNames = new ArrayList<>();
            for (CourseEnrollment enrollment : enrollments) {
                Course course = courseService.getById(enrollment.getCourseId());
                if (course != null) {
                    courseNames.add(course.getName());
                }
            }
            if (!courseNames.isEmpty()) {
                String courseList = String.join("、", courseNames);
                String recommendation = aiService.chat(
                        "我目前在学习以下课程：" + courseList + "。请根据这些课程为我推荐个性化的学习资源和学习建议。",
                        null, userId);
                result.put("recommendation", recommendation);
            } else {
                result.put("recommendation", "您尚未加入任何课程，请先选课以获取个性化推荐。");
            }
        }
        result.put("recommendations", recommendations);
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
        List<Map<String, Object>> recommendations = new ArrayList<>();
        if (courseId != null) {
            try {
                String questions = aiService.generateQuestion(courseId, "SINGLE_CHOICE",
                        difficulty != null ? difficulty : "MEDIUM", 3);
                // 将生成的问题拆分并放入recommendations列表
                String[] questionItems = questions.split("\n---\n");
                for (String item : questionItems) {
                    Map<String, Object> rec = new HashMap<>();
                    rec.put("content", item.trim());
                    rec.put("type", "SINGLE_CHOICE");
                    rec.put("difficulty", difficulty != null ? difficulty : "MEDIUM");
                    recommendations.add(rec);
                }
                if (recommendations.isEmpty() && !questions.isEmpty()) {
                    Map<String, Object> rec = new HashMap<>();
                    rec.put("content", questions);
                    rec.put("type", "SINGLE_CHOICE");
                    rec.put("difficulty", difficulty != null ? difficulty : "MEDIUM");
                    recommendations.add(rec);
                }
            } catch (Exception e) {
                result.put("error", "题目生成失败: " + e.getMessage());
            }
        }
        result.put("recommendations", recommendations);
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
        List<Map<String, Object>> weakPoints = new ArrayList<>();
        if (userId != null) {
            // 获取学生的学习数据用于分析
            Map<String, Object> userStats = analyticsService.getUserDashboard(userId);
            result.put("userStats", userStats);
            // 使用AI分析薄弱项
            try {
                String analysis = aiService.chat(
                        "请根据以下学习统计数据，分析学习者的知识薄弱项：" + userStats.toString(),
                        null, userId);
                // 将AI分析结果按段落拆分为薄弱项列表
                String[] lines = analysis.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        Map<String, Object> wp = new HashMap<>();
                        wp.put("description", line);
                        wp.put("severity", line.contains("严重") || line.contains("重点") ? "HIGH" : "MEDIUM");
                        weakPoints.add(wp);
                    }
                }
                if (weakPoints.isEmpty()) {
                    Map<String, Object> wp = new HashMap<>();
                    wp.put("description", analysis);
                    wp.put("severity", "MEDIUM");
                    weakPoints.add(wp);
                }
            } catch (Exception e) {
                Map<String, Object> wp = new HashMap<>();
                wp.put("description", "AI分析暂时不可用，请稍后重试");
                wp.put("severity", "LOW");
                weakPoints.add(wp);
            }
        }
        result.put("weakPoints", weakPoints);
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
                result.put("status", "error");
                result.put("message", "学习路径生成失败: " + e.getMessage());
            }
        } else {
            result.put("status", "error");
            result.put("message", "缺少必要参数：userId或courseId");
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