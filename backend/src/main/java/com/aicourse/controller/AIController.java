package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.AiChatMessage;
import com.aicourse.entity.AiChatSession;
import com.aicourse.dto.EssayDTO;
import com.aicourse.dto.LearningPathDTO;
import com.aicourse.dto.QaAskDTO;
import com.aicourse.dto.QaContinueDTO;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.AIService;
import com.aicourse.service.AiChatSessionService;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.LearningPathService;
import com.aicourse.service.LearningRecommendationService;
import com.aicourse.service.component.ai.AiCallContext;
import com.aicourse.vo.QaAnswerVO;
import com.aicourse.vo.QaSourceVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.Callable;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import lombok.extern.slf4j.Slf4j;
import com.aicourse.common.ApiCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.util.HtmlUtils;

/**
 * AI 功能接口控制器（需求文档对齐版）
 * 基础路径: /api/ai
 * <p>
 * 设计要点：
 * 1) AI 接口统一返回 Callable，由 MVC 异步线程池执行，释放 Tomcat 请求线程。
 * 2) 请求参数采用 DTO + jakarta.validation 校验，取代裸 Map。
 * 3) 薄弱点分析 / 题目推荐改用结构化 JSON 输出 + 健壮解析。
 * 4) 历史遗留的 /chat /generate-question /generate-summary /check-plagiarism /rag/ask
 *    五个端点已于 2026.07 彻底删除，请使用 /qa/ask /recommendations/questions /essay/correct
 *    等新端点。
 * <p>
 * 5) <b>跨课程 AI 统计的归属标注（2026-10-13 增）</b>：每个会真实触发大模型调用的入口，
 *    都用 {@link AiCallContext#run} 把「调用者 / 归属课程 / 业务场景」带进作用域；
 *    真正落库发生在 {@code AiCoreComponent}。作用域必须设在 {@code Callable} <b>内部</b>
 *    （异步线程），否则上下文不会跟随过去。
 */
@RestController
@RequestMapping("/ai")
@Slf4j
@Tag(name = "AI 助手")
@PreAuthorize("isAuthenticated()")
public class AIController {


    private final AIService aiService;
    private final AnalyticsService analyticsService;
    private final LearningPathService learningPathService;
    private final CourseEnrollmentService courseEnrollmentService;
    private final CourseService courseService;
    private final AiChatSessionService aiChatSessionService;
    private final LearningRecommendationService learningRecommendationService;

    public AIController(AIService aiService,
                        AnalyticsService analyticsService,
                        LearningPathService learningPathService,
                        CourseEnrollmentService courseEnrollmentService,
                        CourseService courseService,
                        AiChatSessionService aiChatSessionService,
                        LearningRecommendationService learningRecommendationService) {
        this.aiService = aiService;
        this.analyticsService = analyticsService;
        this.learningPathService = learningPathService;
        this.courseEnrollmentService = courseEnrollmentService;
        this.courseService = courseService;
        this.aiChatSessionService = aiChatSessionService;
        this.learningRecommendationService = learningRecommendationService;
    }

    // ==================== 智能问答（3个接口） ====================

    /**
     * 智能答疑 - RAG增强专业问题解答
     * POST /api/ai/qa/ask
     * <p>响应含 {@code sources}（检索命中的课程资料出处），供界面溯源展示；
     * 降级为纯 LLM 直答时为空数组。出处文本与答案同口径做 HTML 转义后下发。</p>
     */
    @PostMapping("/qa/ask")
    public Callable<ApiResponse<Map<String, Object>>> qaAsk(@Valid @RequestBody QaAskDTO body,
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
                // ★ 2026-10-13 修复：不再丢弃入口已收到的 courseId（原先固定传 null，会话无法归属课程）
                session = aiChatSessionService.createSession(userId, null, cid);
            }
            final String sessionId = session.getSessionId();
            return AiCallContext.run(userId, cid, "QA_ASK", () -> {
                // XSS防护：对用户输入进行HTML转义
                String escapedQuestion = HtmlUtils.htmlEscape(question);
                aiChatSessionService.appendMessage(sessionId, "user", escapedQuestion);
                QaAnswerVO qa = aiService.ragAsk(question, cid);
                String answer = qa != null && qa.getAnswer() != null ? qa.getAnswer() : "";
                // XSS防护：对AI返回内容进行HTML转义
                String escapedAnswer = HtmlUtils.htmlEscape(answer);
                aiChatSessionService.appendMessage(sessionId, "assistant", escapedAnswer);
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("answer", escapedAnswer);
                resp.put("sessionId", sessionId);
                resp.put("sources", escapeSources(qa != null ? qa.getSources() : null));
                return ApiResponse.success(resp);
            });
        };
    }

    /**
     * 出处片段的下发前处理：逐条复制并对 {@code documentName}/{@code snippet} 做 HTML 转义，
     * 与 {@code answer} 同口径（转义只用于展示，不写回引擎侧存储）。
     */
    private List<QaSourceVO> escapeSources(List<QaSourceVO> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        List<QaSourceVO> escaped = new ArrayList<>(sources.size());
        for (QaSourceVO s : sources) {
            escaped.add(new QaSourceVO(
                    s.getDocumentId(),
                    HtmlUtils.htmlEscape(s.getDocumentName() != null ? s.getDocumentName() : ""),
                    s.getRelevanceScore(),
                    HtmlUtils.htmlEscape(s.getSnippet() != null ? s.getSnippet() : "")));
        }
        return escaped;
    }

    /**
     * 继续对话 - 多轮上下文连续对话
     * POST /api/ai/qa/continue
     * <p>归属课程取自会话本身：会话由 {@link #qaAsk} 建立时已写入 courseId，
     * 故"继续对话"不会因为 DTO 里没有 courseId 就退化成未绑定课程。</p>
     */
    @PostMapping("/qa/continue")
    public Callable<ApiResponse<Map<String, Object>>> qaContinue(@Valid @RequestBody QaContinueDTO body,
                                                                Authentication auth) {
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        final String message = body.getMessage();
        final String providedSid = body.getSessionId();
        return () -> {
            AiChatSession session = (providedSid != null) ? aiChatSessionService.getBySessionId(providedSid) : null;
            if (session == null) {
                session = aiChatSessionService.createSession(userId, null, null);
            }
            final String sessionId = session.getSessionId();
            final Long sessionCourseId = session.getCourseId();
            return AiCallContext.run(userId, sessionCourseId, "QA_CONTINUE", () -> {
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
            });
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
     * <p>⚠️ 该路径<b>不落</b> {@code ai_call_logs}：实际调用发生在 SSE 订阅线程上，
     * 而调用归属是线程级作用域，跨线程即失效——少记一条好过记错归属（见 {@code AiCoreComponent} 注释）。
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
     * <p>作文批改不带课程维度（DTO 无 courseId），统计中归入"未绑定课程"。</p>
     */
    @PostMapping("/essay/correct")
    public Callable<ApiResponse<Map<String, String>>> essayCorrect(@Valid @RequestBody EssayDTO body,
                                                                   Authentication auth) {
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        final String content = body.getContent();
        return () -> AiCallContext.run(userId, null, "ESSAY_CORRECT", () -> {
            String result = aiService.generateContent("correct", content);
            return ApiResponse.success(Map.of("result", result));
        });
    }

    /**
     * 作文润色 - AI优化作文表达
     * POST /api/ai/essay/polish
     */
    @PostMapping("/essay/polish")
    public Callable<ApiResponse<Map<String, String>>> essayPolish(@Valid @RequestBody EssayDTO body,
                                                                  Authentication auth) {
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        final String content = body.getContent();
        return () -> AiCallContext.run(userId, null, "ESSAY_POLISH", () -> {
            String result = aiService.generateContent("polish", content);
            return ApiResponse.success(Map.of("result", result));
        });
    }

    // ==================== 智能推荐（2个接口） ====================

    /**
     * 个性化学习推荐
     * GET /api/ai/recommendations/learning
     */
    @GetMapping("/recommendations/learning")
    public Callable<ApiResponse<Map<String, Object>>> learningRecommendations(Authentication auth) {
        // 业务编排已下沉至 LearningRecommendationService（学情聚合 + 数据驱动上下文 + LLM 推荐）
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        return () -> AiCallContext.run(userId, null, "LEARNING_RECOMMEND",
                () -> ApiResponse.success(learningRecommendationService.recommendLearning(userId)));
    }

    /**
     * 作业题目推荐
     * GET /api/ai/recommendations/questions
     */
    @GetMapping("/recommendations/questions")
    public Callable<ApiResponse<Map<String, Object>>> questionRecommendations(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String difficulty,
            Authentication auth) {
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        final Long cid = courseId;
        final String diff = difficulty;
        return () -> AiCallContext.run(userId, cid, "QUESTION_RECOMMEND", () -> {
            Map<String, Object> result = new HashMap<>();
            result.put("courseId", cid);
            result.put("difficulty", diff);
            List<Map<String, Object>> recommendations = new ArrayList<>();
            if (cid != null) {
                try {
                    String json = aiService.recommendQuestionsJson(cid, diff != null ? diff : "MEDIUM", 3);
                    recommendations = aiService.parseQuestions(json, diff);
                } catch (Exception e) {
                    log.error("题目推荐生成失败: {}", e.getMessage(), e);
                    throw new BusinessException("题目生成失败: " + e.getMessage());
                }
            }
            result.put("recommendations", recommendations);
            return ApiResponse.success(result);
        });
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
        return () -> AiCallContext.run(userId, cid, "WEAK_POINTS", () -> {
            Map<String, Object> result = new HashMap<>();
            result.put("courseId", cid);
            List<Map<String, String>> weakPoints = new ArrayList<>();
            if (userId != null) {
                Map<String, Object> userStats = analyticsService.getUserDashboard(userId);
                result.put("userStats", userStats);
                weakPoints = aiService.analyzeWeakPoints(userStats);
            }
            result.put("weakPoints", weakPoints);
            return ApiResponse.success(result);
        });
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
        final Long cid = courseId;
        return () -> AiCallContext.run(userId, cid, "LEARNING_ADVICE", () -> {
            if (userId == null) {
                throw new BusinessException(ApiCode.UNAUTHORIZED, "未登录");
            }
            try {
                Map<String, Object> userStats = analyticsService.getUserDashboard(userId);
                Map<String, Object> advice = aiService.generateLearningAdvice(userStats);
                return ApiResponse.success(advice);
            } catch (Exception e) {
                log.error("生成学习建议失败: {}", e.getMessage(), e);
                throw new BusinessException("生成学习建议失败: " + e.getMessage());
            }
        });
    }

    // ==================== 学习路径（1个接口） ====================

    /**
     * 学习路径规划 - AI生成学习路径
     * POST /api/ai/learning-path/generate
     */
    @PostMapping("/learning-path/generate")
    public Callable<ApiResponse<Map<String, Object>>> generateLearningPath(
            @Valid @RequestBody LearningPathDTO body,
            Authentication auth) {
        // DR-32：与本控制器其余端点同口径做空值防护——Authentication 参数为 null 时
        // 直接解引用会抛 NPE 并回落 500，掩盖「缺少必要参数」这一业务 400。
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        final String courseId = body.getCourseId();
        final String goal = body.getGoal() != null ? body.getGoal() : "掌握课程核心知识";
        final Long cid = courseId != null ? Long.valueOf(courseId) : null;
        return () -> AiCallContext.run(userId, cid, "LEARNING_PATH", () -> {
            Map<String, Object> result = new HashMap<>();
            if (cid != null && userId != null) {
                try {
                    String pathName = "AI学习路径";
                    // 传 null 掌握度 → 服务层自动从错题学情推导；无学情时退化为标准路径
                    learningPathService.generatePathPersonalized(userId, cid, pathName, goal, null);
                    result.put("status", "created");
                    result.put("message", "学习路径已生成");
                } catch (Exception e) {
                    log.error("学习路径生成失败: {}", e.getMessage(), e);
                    throw new BusinessException("学习路径生成失败: " + e.getMessage());
                }
            } else {
                throw new BusinessException("缺少必要参数：userId或courseId");
            }
            return ApiResponse.success(result);
        });
    }

    /**
     * 个性化学习路径规划 - 掌握度驱动版
     * POST /api/ai/learning-path/generate-personalized
     * 接收学生的知识点掌握度数据，生成薄弱优先 + 难度梯度排序的个性化路径。
     */
    @PostMapping("/learning-path/generate-personalized")
    public Callable<ApiResponse<Map<String, Object>>> generatePersonalizedLearningPath(
            @Valid @RequestBody LearningPathDTO body,
            Authentication auth) {
        // DR-32：同 generateLearningPath，null Authentication 不再解引用抛 NPE
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        final String courseId = body.getCourseId();
        final String goal = body.getGoal() != null ? body.getGoal() : "针对性补强薄弱点";
        final Long cid = courseId != null ? Long.valueOf(courseId) : null;
        // 掌握度数据：{知识点名称: 掌握度(0-100)}
        final Map<String, Double> mastery;
        if (body.getMasteryData() != null && !body.getMasteryData().isEmpty()) {
            mastery = new HashMap<>();
            for (Map.Entry<String, Object> e : body.getMasteryData().entrySet()) {
                if (e.getValue() instanceof Number) {
                    mastery.put(e.getKey(), ((Number) e.getValue()).doubleValue());
                }
            }
        } else {
            mastery = null;
        }
        return () -> AiCallContext.run(userId, cid, "PERSONALIZED_PATH", () -> {
            Map<String, Object> result = new HashMap<>();
            if (cid != null && userId != null) {
                try {
                    String pathName = mastery != null && !mastery.isEmpty()
                            ? "个性化学习路径（掌握度驱动）" : "AI学习路径";
                    learningPathService.generatePathPersonalized(userId, cid, pathName, goal, mastery);
                    result.put("status", "created");
                    result.put("message", "个性化学习路径已生成");
                    result.put("personalized", mastery != null && !mastery.isEmpty());
                } catch (Exception e) {
                    log.error("个性化学习路径生成失败: {}", e.getMessage(), e);
                    throw new BusinessException("学习路径生成失败: " + e.getMessage());
                }
            } else {
                throw new BusinessException("缺少必要参数：userId或courseId");
            }
            return ApiResponse.success(result);
        });
    }

}
