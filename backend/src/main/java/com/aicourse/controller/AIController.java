package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.JsonUtils;
import com.aicourse.dto.EssayDTO;
import com.aicourse.dto.QaAskDTO;
import com.aicourse.dto.QaContinueDTO;
import com.aicourse.entity.AiChatMessage;
import com.aicourse.entity.AiChatSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.AIAccessSupport;
import com.aicourse.service.AIService;
import com.aicourse.service.AiChatSessionService;
import com.aicourse.service.component.ai.AiCallContext;
import com.aicourse.vo.QaAnswerVO;
import com.aicourse.vo.QaSourceVO;
import com.aicourse.vo.QaStreamVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * AI 问答与作文批改接口（2026-11-18 从 597 行 AIController 拆分）。
 *
 * <p>保留 RAG 问答、全局问答、继续对话、会话历史、SSE 流式问答与作文批改/润色；
 * 推荐与学情见 {@link AIInsightController}，学习路径见 {@link AILearningPathController}。
 * 基础路径仍为 /api/ai。</p>
 */
@RestController
@RequestMapping("/ai")
@Tag(name = "AI 助手-问答与作文")
@PreAuthorize("isAuthenticated()")
public class AIController {

    private final AIService aiService;
    private final AiChatSessionService aiChatSessionService;
    private final AIAccessSupport aiAccessSupport;

    public AIController(AIService aiService,
                        AiChatSessionService aiChatSessionService,
                        AIAccessSupport aiAccessSupport) {
        this.aiService = aiService;
        this.aiChatSessionService = aiChatSessionService;
        this.aiAccessSupport = aiAccessSupport;
    }

    // ==================== 智能问答（3个接口） ====================

    /**
     * 智能答疑 - RAG增强专业问题解答。
     * POST /api/ai/qa/ask
     */
    @PostMapping("/qa/ask")
    public Callable<ApiResponse<Map<String, Object>>> qaAsk(@Valid @RequestBody QaAskDTO body,
                                                            Authentication auth) {
        return qaAskInternal(body, auth, false);
    }

    /**
     * 全局 AI 助手：跨课程通用问答，不读取任何课程知识库/课程私有资料。
     * 与 {@link #qaAsk} 显式分离，避免“课程内问答”和“全局助手”共用同一入口后，
     * 调用方误以为不传 courseId 仍会命中课程 RAG。全局会话以 courseId=null 落库。
     */
    @PostMapping("/qa/global/ask")
    public Callable<ApiResponse<Map<String, Object>>> qaAskGlobal(@Valid @RequestBody QaAskDTO body,
                                                                  Authentication auth) {
        return qaAskInternal(body, auth, true);
    }

    private Callable<ApiResponse<Map<String, Object>>> qaAskInternal(QaAskDTO body,
                                                                    Authentication auth,
                                                                    boolean globalOnly) {
        // 在 Tomcat 线程提取所需参数，避免异步线程读取 SecurityContext
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        final String question = body.getQuestion();
        final Long cid = aiAccessSupport.parseCourseId(body.getCourseId());
        final String providedSid = body.getSessionId();
        return () -> {
            if (globalOnly && cid != null) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "全局 AI 助手不能指定课程");
            }
            if (!globalOnly && cid != null && userId != null) {
                aiAccessSupport.assertCourseReadable(cid, auth);
            }
            AiChatSession session = (providedSid != null) ? aiChatSessionService.getBySessionId(providedSid) : null;
            assertSessionOwned(session, userId);
            if (session != null) {
                // 全局助手、课程问答、课程继续对话三种上下文不得互相串用：
                // 课程会话可能已包含课程私有资料，不能在全局入口继续读取；课程入口也不得切换课程。
                if (globalOnly && session.getCourseId() != null) {
                    throw new BusinessException(ApiCode.BAD_REQUEST, "全局 AI 助手不能继续课程会话");
                }
                if (cid == null && session.getCourseId() != null) {
                    throw new BusinessException(ApiCode.BAD_REQUEST, "课程会话必须携带课程ID");
                }
                if (cid != null && session.getCourseId() != null && !cid.equals(session.getCourseId())) {
                    throw new BusinessException(ApiCode.BAD_REQUEST, "会话所属课程与本次请求不一致");
                }
            }
            if (session == null) {
                session = aiChatSessionService.createSession(userId, null, cid);
            }
            final String sessionId = session.getSessionId();
            final String scene = cid == null ? "QA_GLOBAL" : "QA_ASK";
            return AiCallContext.run(userId, cid, scene, () -> {
                // 纯文本契约：用户输入与 AI 回答均按原文落库、原文下发，不做 HTML 转义
                aiChatSessionService.appendMessage(sessionId, "user", question);
                QaAnswerVO qa = aiService.ragAsk(question, cid);
                String answer = qa != null && qa.getAnswer() != null ? qa.getAnswer() : "";
                aiChatSessionService.appendMessage(sessionId, "assistant", answer);
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("answer", answer);
                resp.put("sessionId", sessionId);
                resp.put("sources", qa != null && qa.getSources() != null ? qa.getSources() : List.of());
                // mode：ENGINE=真实引擎召回 + 本端大模型生成，LOCAL=引擎不可用时的接地降级。
                resp.put("mode", qa != null ? qa.getMode() : null);
                return ApiResponse.success(resp);
            });
        };
    }

    /**
     * 继续对话 - 多轮上下文连续对话。
     * POST /api/ai/qa/continue
     */
    @PostMapping("/qa/continue")
    public Callable<ApiResponse<Map<String, Object>>> qaContinue(@Valid @RequestBody QaContinueDTO body,
                                                                Authentication auth) {
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        final String message = body.getMessage();
        final String providedSid = body.getSessionId();
        return () -> {
            AiChatSession session = (providedSid != null) ? aiChatSessionService.getBySessionId(providedSid) : null;
            assertSessionOwned(session, userId);
            if (session == null) {
                session = aiChatSessionService.createSession(userId, null, null);
            }
            final String sessionId = session.getSessionId();
            final Long sessionCourseId = session.getCourseId();
            // 会话归属只解决“不能读别人的会话”；课程权限是另一层：课程权限被收回后，
            // 已存在的课程会话不能继续成为绕过 ACL 的通道。全局会话（courseId=null）不受影响。
            if (sessionCourseId != null) {
                aiAccessSupport.assertCourseReadable(sessionCourseId, auth);
            }
            return AiCallContext.run(userId, sessionCourseId, "QA_CONTINUE", () -> {
                // 纯文本契约：历史消息、用户输入与 AI 回答均按原文传递，不做 HTML 转义
                List<Map<String, String>> history = new ArrayList<>();
                for (AiChatMessage m : aiChatSessionService.getMessages(sessionId)) {
                    history.add(Map.of("role", m.getRole(), "content", m.getContent() != null ? m.getContent() : ""));
                }
                history.add(Map.of("role", "user", "content", message));
                aiChatSessionService.appendMessage(sessionId, "user", message);
                String answer = aiService.chat(history, userId);
                aiChatSessionService.appendMessage(sessionId, "assistant", answer != null ? answer : "");
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("answer", answer != null ? answer : "");
                resp.put("sessionId", sessionId);
                return ApiResponse.success(resp);
            });
        };
    }

    /**
     * 获取会话历史（基于真实持久化的 AI 会话表）。
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
                    m.put("title", s.getTitle() != null ? s.getTitle() : "");
                    m.put("messageCount", s.getMessageCount());
                    m.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
                    m.put("updatedAt", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null);
                    List<AiChatMessage> msgs = aiChatSessionService.getMessages(s.getSessionId());
                    String last = msgs.isEmpty() ? "" : msgs.get(msgs.size() - 1).getContent();
                    String safeLast = last != null ? last : "";
                    m.put("lastMessage", safeLast.length() > 100 ? safeLast.substring(0, 100) + "…" : safeLast);
                    sessions.add(m);
                }
            }
            return ApiResponse.success(sessions);
        };
    }

    /**
     * 流式智能问答 - SSE 逐 token 推送（含课程资料溯源）。
     * GET /api/ai/qa/stream
     */
    @GetMapping(value = "/qa/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam @NotBlank(message = "提问内容不能为空") String question,
                                   @RequestParam(required = false) Long courseId,
                                   Authentication auth) {
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        // courseId 可选：null 表示全局助手流式通道（不读课程资料）；非空必须先过课程 ACL，
        // 否则任何登录用户都能借流式端点读取他课知识库。
        if (courseId != null) {
            aiAccessSupport.assertCourseReadable(courseId, auth);
        }
        // 作用域仅覆盖 Tomcat 线程内的服务编排：AiDialogueComponent 在其中捕获归属快照供流式落库
        QaStreamVO stream = AiCallContext.run(userId, courseId, "QA_STREAM",
                () -> aiService.streamChat(question, courseId));
        // 出处与 /qa/ask 同口径：原文下发，不做 HTML 转义
        List<QaSourceVO> sources = stream.getSources() != null ? stream.getSources() : List.of();
        return stream.getTokens()
                .map(this::tokenFrame)
                .concatWith(Flux.just(sourcesFrame(sources)));
    }

    /**
     * 会话归属断言（B-05 对象级 ACL）。
     * sessionId 不可枚举不等于不需要校验：一次抓包、一条被转发的链接即失控。
     */
    private void assertSessionOwned(AiChatSession session, Long userId) {
        if (session == null) {
            return;
        }
        if (userId == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权访问他人的 AI 会话");
        }
    }

    /** 增量帧：{"token":"..."}；序列化失败（理论不可达）时降级为空增量，不打断整条流。 */
    private String tokenFrame(String token) {
        try {
            return JsonUtils.toJson(Map.of("token", token != null ? token : ""));
        } catch (Exception e) {
            return "{\"token\":\"\"}";
        }
    }

    /** 出处末帧：{"sources":[...]}；无出处时为空数组，前端据此不渲染溯源区块。 */
    private String sourcesFrame(List<QaSourceVO> sources) {
        try {
            return JsonUtils.toJson(Map.of("sources", sources == null ? List.of() : sources));
        } catch (Exception e) {
            return "{\"sources\":[]}";
        }
    }

    // ==================== 作文批改（2个接口） ====================

    /** 作文批改 - AI自动批改作文。POST /api/ai/essay/correct */
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

    /** 作文润色 - AI优化作文表达。POST /api/ai/essay/polish */
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
}
