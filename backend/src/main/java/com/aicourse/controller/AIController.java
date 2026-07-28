package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.JsonUtils;
import com.aicourse.entity.Course;
import com.aicourse.entity.AiChatMessage;
import com.aicourse.entity.AiChatSession;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.LearningRecord;
import com.aicourse.dto.EssayRequest;
import com.aicourse.dto.LearningPathRequest;
import com.aicourse.dto.QaAskRequest;
import com.aicourse.dto.QaContinueRequest;
import com.aicourse.dto.QuestionGenerateDTO;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.AIService;
import com.aicourse.service.AiChatSessionService;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.LearningPathService;
import com.aicourse.service.LearningRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import lombok.extern.slf4j.Slf4j;
import com.aicourse.common.ApiCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.util.HtmlUtils;

/**
 * AI 功能接口控制器（需求文档对齐版）
 * 基础路径: /api/ai
 * <p>
 * 优化点：
 * 1) AI 接口统一返回 Callable，由 MVC 异步线程池执行，释放 Tomcat 请求线程（避免长耗时 LLM 调用阻塞 Web 容器）。
 * 2) 请求参数采用 DTO + jakarta.validation 校验，取代裸 Map。
 * 3) 薄弱点分析 / 题目推荐改用结构化 JSON 输出 + 健壮解析，避免按换行脆弱切分。
 * 4) 旧版兼容接口已标记 @Deprecated，请勿在新前端中使用。
 */
@RestController
@RequestMapping("/ai")
@Slf4j
@Tag(name = "AI 助手")
public class AIController {


    private final AIService aiService;
    private final AnalyticsService analyticsService;
    private final LearningPathService learningPathService;
    private final CourseEnrollmentService courseEnrollmentService;
    private final LearningRecordService learningRecordService;
    private final CourseService courseService;
    private final KnowledgePointService knowledgePointService;
    private final AiChatSessionService aiChatSessionService;

    public AIController(AIService aiService,
                        AnalyticsService analyticsService,
                        LearningPathService learningPathService,
                        CourseEnrollmentService courseEnrollmentService,
                        LearningRecordService learningRecordService,
                        CourseService courseService,
                        KnowledgePointService knowledgePointService,
                        AiChatSessionService aiChatSessionService) {
        this.aiService = aiService;
        this.analyticsService = analyticsService;
        this.learningPathService = learningPathService;
        this.courseEnrollmentService = courseEnrollmentService;
        this.learningRecordService = learningRecordService;
        this.courseService = courseService;
        this.knowledgePointService = knowledgePointService;
        this.aiChatSessionService = aiChatSessionService;
    }

    // ==================== 智能问答（3个接口） ====================

    /**
     * 智能答疑 - RAG增强专业问题解答
     * POST /api/ai/qa/ask
     */
    @PostMapping("/qa/ask")
    public Callable<ApiResponse<Map<String, Object>>> qaAsk(@Valid @RequestBody QaAskRequest body,
                                                            Authentication auth) {
        // 在 Tomcat 线程提取所需参数，避免异步线程读取 SecurityContext
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        final String question = body.getQuestion();
        final String courseId = body.getCourseId();
        final String providedSid = body.getSessionId();
        return () -> {
            Long cid = courseId != null ? Long.valueOf(courseId) : null;
            AiChatSession session = (providedSid != null) ? aiChatSessionService.getBySessionId(providedSid) : null;
            if (session == null) {
                session = aiChatSessionService.createSession(userId, null);
            }
            String sessionId = session.getSessionId();
            // XSS防护：对用户输入进行HTML转义
            String escapedQuestion = HtmlUtils.htmlEscape(question);
            aiChatSessionService.appendMessage(sessionId, "user", escapedQuestion);
            String answer = aiService.ragAsk(question, cid);
            // XSS防护：对AI返回内容进行HTML转义
            String escapedAnswer = HtmlUtils.htmlEscape(answer != null ? answer : "");
            aiChatSessionService.appendMessage(sessionId, "assistant", escapedAnswer);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("answer", escapedAnswer);
            resp.put("sessionId", sessionId);
            return ApiResponse.success(resp);
        };
    }

    /**
     * 继续对话 - 多轮上下文连续对话
     * POST /api/ai/qa/continue
     */
    @PostMapping("/qa/continue")
    public Callable<ApiResponse<Map<String, Object>>> qaContinue(@Valid @RequestBody QaContinueRequest body,
                                                                Authentication auth) {
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        final String message = body.getMessage();
        final String providedSid = body.getSessionId();
        return () -> {
            AiChatSession session = (providedSid != null) ? aiChatSessionService.getBySessionId(providedSid) : null;
            if (session == null) {
                session = aiChatSessionService.createSession(userId, null);
            }
            String sessionId = session.getSessionId();
            // XSS防护：对AI返回的历史消息和用户输入进行HTML转义
            String escapedMessage = HtmlUtils.htmlEscape(message);
            List<Map<String, String>> history = new ArrayList<>();
            for (AiChatMessage m : aiChatSessionService.getMessages(sessionId)) {
                history.add(Map.of("role", m.getRole(), "content", HtmlUtils.htmlEscape(m.getContent() != null ? m.getContent() : "")));
            }
            history.add(Map.of("role", "user", "content", escapedMessage));
            aiChatSessionService.appendMessage(sessionId, "user", escapedMessage);
            String answer = aiService.chat(history, userId);
            // XSS防护：对AI返回内容进行HTML转义
            String escapedAnswer = HtmlUtils.htmlEscape(answer != null ? answer : "");
            aiChatSessionService.appendMessage(sessionId, "assistant", escapedAnswer);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("answer", escapedAnswer);
            resp.put("sessionId", sessionId);
            return ApiResponse.success(resp);
        };
    }

    /**
     * 获取会话历史（基于真实持久化的 AI 会话表）
     * GET /api/ai/qa/sessions
     */
    @GetMapping("/qa/sessions")
    public Callable<ApiResponse<List<Map<String, Object>>>> getSessions(Authentication auth) {
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        return () -> {
            List<Map<String, Object>> sessions = new ArrayList<>();
            if (userId != null) {
                for (AiChatSession s : aiChatSessionService.listSessions(userId)) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("sessionId", s.getSessionId());
                    m.put("title", HtmlUtils.htmlEscape(s.getTitle() != null ? s.getTitle() : ""));
                    m.put("messageCount", s.getMessageCount());
                    m.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
                    m.put("updatedAt", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null);
                    List<AiChatMessage> msgs = aiChatSessionService.getMessages(s.getSessionId());
                    String last = msgs.isEmpty() ? "" : msgs.get(msgs.size() - 1).getContent();
                    String escapedLast = HtmlUtils.htmlEscape(last != null ? last : "");
                    m.put("lastMessage", escapedLast.length() > 100 ? escapedLast.substring(0, 100) + "…" : escapedLast);
                    sessions.add(m);
                }
            }
            return ApiResponse.success(sessions);
        };
    }

    /**
     * 流式智能问答 - SSE 逐 token 推送
     * <p>
     * 与 {@link #qaAsk} 不同，本接口以 Server-Sent Events 方式流式返回 AI 回答，
     * 每个事件为一个 content delta 片段，前端可实时渲染打字机效果。
     * 输入经 PromptGuard 注入中和后，由当前配置的 LLM 供应商（DeepSeek/OpenAI/Qwen）
     * 以流式模式执行补全，输出每 token 经输出护栏清洗后逐条推送。
     *
     * @param question 用户提问内容
     * @return SSE 流，每个事件为一段 delta 文本
     */
    @GetMapping(value = "/qa/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam @NotBlank(message = "提问内容不能为空") String question) {
        return aiService.streamChat(question);
    }

    // ==================== 作文批改（2个接口） ====================

    /**
     * 作文批改 - AI自动批改作文
     * POST /api/ai/essay/correct
     */
    @PostMapping("/essay/correct")
    public Callable<ApiResponse<Map<String, String>>> essayCorrect(@Valid @RequestBody EssayRequest body) {
        final String content = body.getContent();
        return () -> {
            String result = aiService.generateContent("correct", content);
            return ApiResponse.success(Map.of("result", result));
        };
    }

    /**
     * 作文润色 - AI优化作文表达
     * POST /api/ai/essay/polish
     */
    @PostMapping("/essay/polish")
    public Callable<ApiResponse<Map<String, String>>> essayPolish(@Valid @RequestBody EssayRequest body) {
        final String content = body.getContent();
        return () -> {
            String result = aiService.generateContent("polish", content);
            return ApiResponse.success(Map.of("result", result));
        };
    }

    // ==================== 智能推荐（2个接口） ====================

    /**
     * 个性化学习推荐
     * GET /api/ai/recommendations/learning
     */
    @GetMapping("/recommendations/learning")
    public Callable<ApiResponse<Map<String, Object>>> learningRecommendations(Authentication auth) {
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        return () -> {
            Map<String, Object> result = new HashMap<>();
            if (userId != null) {
                List<CourseEnrollment> enrollments = courseEnrollmentService.list(
                        new LambdaQueryWrapper<CourseEnrollment>()
                                .eq(CourseEnrollment::getStudentId, userId)
                                .eq(CourseEnrollment::getIsActive, true)
                );
                Set<Long> courseIds = enrollments.stream()
                        .map(CourseEnrollment::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet());
                if (courseIds.isEmpty()) {
                    result.put("recommendation", "您尚未加入任何课程，请先选课以获取个性化推荐。");
                    return ApiResponse.success(result);
                }

                // 构建数据驱动推荐上下文
                Map<String, Object> context = new LinkedHashMap<>();

                // 1) 课程列表
                Map<Long, String> nameMap = courseService.listByIds(courseIds).stream()
                        .collect(Collectors.toMap(Course::getId, Course::getName, (a, b) -> a));
                String courseList = enrollments.stream()
                        .map(e -> nameMap.getOrDefault(e.getCourseId(), ""))
                        .filter(n -> !n.isEmpty()).collect(Collectors.joining("、"));
                context.put("courseList", courseList);

                // 2) 课程进度
                Double avgProgress = enrollments.stream()
                        .map(CourseEnrollment::getProgress)
                        .filter(Objects::nonNull)
                        .mapToDouble(p -> p.doubleValue()).average().orElse(0);
                context.put("progress", String.format("%.0f", avgProgress));

                // 3) 薄弱知识点（按掌握度升序，取前8最弱项）
                try {
                    List<Map<String, Object>> weakPoints = new ArrayList<>();
                    for (Long cid : courseIds) {
                        var kps = knowledgePointService.list(
                                new LambdaQueryWrapper<com.aicourse.entity.KnowledgePoint>()
                                        .eq(com.aicourse.entity.KnowledgePoint::getCourseId, cid));
                        for (var kp : kps) {
                            BigDecimal mastery = knowledgePointService.calculateMastery(kp.getId(), userId);
                            if (mastery != null && mastery.compareTo(new BigDecimal("70")) < 0) {
                                Map<String, Object> wp = new LinkedHashMap<>();
                                wp.put("name", kp.getName());
                                wp.put("mastery", mastery);
                                weakPoints.add(wp);
                            }
                        }
                    }
                    context.put("weakPoints", weakPoints);
                } catch (Exception e) {
                    log.debug("获取薄弱知识点失败（不影响推荐）: {}", e.getMessage());
                }

                // 4) 近期成绩
                try {
                    var stats = analyticsService.getUserDashboard(userId);
                    if (stats != null && stats.containsKey("avgScore")) {
                        context.put("recentScores", "平均成绩：" + stats.get("avgScore"));
                    }
                } catch (Exception ignore) {}

                String recommendation = aiService.recommendLearningWithContext(context);
                result.put("recommendation", recommendation);
                result.put("dataDriven", true);
            } else {
                result.put("recommendation", "请先登录");
            }
            return ApiResponse.success(result);
        };
    }

    /**
     * 作业题目推荐
     * GET /api/ai/recommendations/questions
     */
    @GetMapping("/recommendations/questions")
    public Callable<ApiResponse<Map<String, Object>>> questionRecommendations(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String difficulty) {
        final Long cid = courseId;
        final String diff = difficulty;
        return () -> {
            Map<String, Object> result = new HashMap<>();
            result.put("courseId", cid);
            result.put("difficulty", diff);
            List<Map<String, Object>> recommendations = new ArrayList<>();
            if (cid != null) {
                try {
                    String json = aiService.recommendQuestionsJson(cid, diff != null ? diff : "MEDIUM", 3);
                    recommendations = parseQuestions(json, diff);
                } catch (Exception e) {
                    throw new BusinessException("题目生成失败: " + e.getMessage());
                }
            }
            result.put("recommendations", recommendations);
            return ApiResponse.success(result);
        };
    }

    /**
     * 将题目推荐 JSON 健壮解析为列表（兼容 ```json ``` 代码块 / 裸 JSON）
     */
    private List<Map<String, Object>> parseQuestions(String jsonText, String diff) {
        List<Map<String, Object>> list = new ArrayList<>();
        String json = JsonUtils.extractJsonObject(jsonText);
        if (json == null) return list;
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            com.fasterxml.jackson.databind.JsonNode arr = root.has("questions") ? root.get("questions") : root;
            if (arr.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode n : arr) {
                    Map<String, Object> rec = new LinkedHashMap<>();
                    rec.put("content", n.has("content") ? n.get("content").asText() : n.asText());
                    rec.put("type", n.has("type") ? n.get("type").asText() : "SINGLE_CHOICE");
                    rec.put("difficulty", n.has("difficulty") ? n.get("difficulty").asText() : (diff != null ? diff : "MEDIUM"));
                    list.add(rec);
                }
            }
        } catch (Exception e) {
            log.warn("题目推荐 JSON 解析失败: {}", e.getMessage());
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("content", jsonText);
            rec.put("type", "SINGLE_CHOICE");
            rec.put("difficulty", diff != null ? diff : "MEDIUM");
            list.add(rec);
        }
        return list;
    }

    // ==================== 学情分析（1个接口） ====================

    /**
     * 知识点薄弱项分析
     * GET /api/ai/analysis/weak-points
     */
    @GetMapping("/analysis/weak-points")
    public Callable<ApiResponse<Map<String, Object>>> weakPointsAnalysis(
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        final Long cid = courseId;
        return () -> {
            Map<String, Object> result = new HashMap<>();
            result.put("courseId", cid);
            List<Map<String, String>> weakPoints = new ArrayList<>();
            if (userId != null) {
                Map<String, Object> userStats = analyticsService.getUserDashboard(userId);
                result.put("userStats", userStats);
                try {
                    String statsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(userStats);
                    String json = aiService.analyzeWeakPointsJson(statsJson);
                    weakPoints = aiService.parseWeakPoints(json);
                } catch (Exception e) {
                    Map<String, String> wp = new LinkedHashMap<>();
                    wp.put("description", "AI分析暂时不可用，请稍后重试");
                    wp.put("severity", "LOW");
                    weakPoints.add(wp);
                }
            }
            result.put("weakPoints", weakPoints);
            return ApiResponse.success(result);
        };
    }

    /**
     * 学情 AI 建议面板：聚合学情统计 + 薄弱点，生成结构化学习建议
     * GET /api/ai/advice/learning
     */
    @GetMapping("/advice/learning")
    public Callable<ApiResponse<Map<String, Object>>> learningAdvice(
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        return () -> {
            Map<String, Object> result = new LinkedHashMap<>();
            if (userId == null) {
                throw new BusinessException(ApiCode.UNAUTHORIZED, "未登录");
            }
            try {
                Map<String, Object> userStats = analyticsService.getUserDashboard(userId);
                String statsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(userStats);
                String weakJson = aiService.analyzeWeakPointsJson(statsJson);
                String adviceJson = aiService.generateLearningAdviceJson(statsJson, weakJson);
                String raw = JsonUtils.extractJsonObject(adviceJson);
                if (raw != null) {
                    com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
                    result.put("summary", root.has("summary") ? root.get("summary").asText() : "");
                    List<Map<String, Object>> items = new ArrayList<>();
                    if (root.has("items") && root.get("items").isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode it : root.get("items")) {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("title", it.has("title") ? it.get("title").asText() : "");
                            m.put("detail", it.has("detail") ? it.get("detail").asText() : "");
                            String lvl = it.has("level") ? it.get("level").asText().toUpperCase() : "MEDIUM";
                            if (!"HIGH".equals(lvl) && !"MEDIUM".equals(lvl) && !"LOW".equals(lvl)) lvl = "MEDIUM";
                            m.put("level", lvl);
                            items.add(m);
                        }
                    }
                    result.put("items", items);
                } else {
                    throw new BusinessException("AI 未返回有效建议");
                }
            } catch (Exception e) {
                throw new BusinessException("生成学习建议失败: " + e.getMessage());
            }
            return ApiResponse.success(result);
        };
    }

    // ==================== 学习路径（1个接口） ====================

    /**
     * 学习路径规划 - AI生成学习路径
     * POST /api/ai/learning-path/generate
     */
    @PostMapping("/learning-path/generate")
    public Callable<ApiResponse<Map<String, Object>>> generateLearningPath(
            @Valid @RequestBody LearningPathRequest body,
            Authentication auth) {
        final Long userId = (Long) auth.getPrincipal();
        final String courseId = body.getCourseId();
        final String goal = body.getGoal() != null ? body.getGoal() : "掌握课程核心知识";
        final Long cid = courseId != null ? Long.valueOf(courseId) : null;
        return () -> {
            Map<String, Object> result = new HashMap<>();
            if (cid != null && userId != null) {
                try {
                    String pathName = "AI学习路径";
                    learningPathService.generatePath(userId, cid, pathName, goal);
                    result.put("status", "created");
                    result.put("message", "学习路径已生成");
                } catch (Exception e) {
                    throw new BusinessException("学习路径生成失败: " + e.getMessage());
                }
            } else {
                throw new BusinessException("缺少必要参数：userId或courseId");
            }
            return ApiResponse.success(result);
        };
    }

    // ==================== 已废弃的兼容旧接口（请勿在新前端中使用） ====================
    // 计划于 2026.09 大版本移除。前端已实现 v2 替代接口（qaAsk/continue, essayCorrect/polish, 
    // recommendations/questions 等），旧前端应尽快迁移。

    /**
     * @deprecated 请使用 {@link #qaAsk(QaAskRequest, Authentication)}。将在 2026.09 移除。
     */
    @Deprecated(since = "2026.07", forRemoval = true)
    @PostMapping("/chat")
    @Operation(summary = "AI 对话（已废弃）", description = "旧版 AI 聊天接口，已废弃，请使用 /ai/qa/ask 替代")
    public Callable<ApiResponse<Map<String, String>>> chat(@RequestBody Map<String, String> body,
                                                          Authentication auth) {
        final String message = body.get("message");
        final String context = body.get("context");
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        return () -> {
            String response = aiService.chat(message, context, userId);
            return ApiResponse.success(Map.of("response", HtmlUtils.htmlEscape(response != null ? response : "")));
        };
    }

    /**
     * @deprecated 请使用 {@link #questionRecommendations(Long, String)}。将在 2026.09 移除。
     */
    @Deprecated(since = "2026.07", forRemoval = true)
    @PostMapping("/generate-question")
    @Operation(summary = "AI 智能出题（已废弃）", description = "旧版 AI 出题接口，已废弃，请使用 /ai/recommendations/questions 替代")
    public Callable<ApiResponse<Map<String, String>>> generateQuestion(
            @Valid @RequestBody QuestionGenerateDTO body) {
        final Long courseId = body.getCourseId();
        final String type = body.getType();
        final String difficulty = body.getDifficulty();
        final Integer count = body.getCount() != null ? body.getCount() : 5;
        return () -> {
            String response = aiService.generateQuestion(courseId, type, difficulty, count);
            return ApiResponse.success(Map.of("questions", response));
        };
    }

    /**
     * @deprecated 请使用 {@link #essayCorrect(EssayRequest)}。将在 2026.09 移除。
     */
    @Deprecated(since = "2026.07", forRemoval = true)
    @PostMapping("/generate-summary")
    @Operation(summary = "AI 摘要（已废弃）", description = "旧版 AI 摘要接口，已废弃，请使用 /ai/essay/correct 替代")
    public Callable<ApiResponse<Map<String, String>>> generateSummary(@RequestBody Map<String, String> body) {
        final String content = body.get("content");
        return () -> {
            String summary = aiService.generateSummary(content);
            return ApiResponse.success(Map.of("summary", summary));
        };
    }

    /**
     * @deprecated 请使用独立的查重接口 POST /submissions/{id}/check-plagiarism。将在 2026.09 移除。
     */
    @Deprecated(since = "2026.07", forRemoval = true)
    @PostMapping("/check-plagiarism")
    @Operation(summary = "AI 查重（已废弃）", description = "旧版查重接口，已废弃，请使用 /submissions/{id}/check-plagiarism 替代")
    public Callable<ApiResponse<Map<String, String>>> checkPlagiarism(@RequestBody Map<String, String> body) {
        final String text1 = body.get("text1");
        final String text2 = body.get("text2");
        return () -> {
            String result = aiService.checkPlagiarism(text1, text2);
            return ApiResponse.success(Map.of("result", result));
        };
    }

    /**
     * @deprecated 请使用 {@link #qaAsk(QaAskRequest, Authentication)}。将在 2026.09 移除。
     */
    @Deprecated(since = "2026.07", forRemoval = true)
    @GetMapping("/rag/ask")
    @Operation(summary = "RAG 问答（已废弃）", description = "旧版 RAG 问答接口，已废弃，请使用 /ai/qa/ask 替代")
    public Callable<ApiResponse<Map<String, String>>> ragAsk(
            @RequestParam @NotBlank(message = "提问内容不能为空") String question,
            @RequestParam(required = false) Long courseId) {
        final String q = question;
        final Long cid = courseId;
        return () -> {
            String answer = aiService.ragAsk(q, cid);
            return ApiResponse.success(Map.of("answer", answer));
        };
    }
}
