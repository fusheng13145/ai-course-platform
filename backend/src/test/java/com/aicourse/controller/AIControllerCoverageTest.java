package com.aicourse.controller;

import com.aicourse.entity.AiChatMessage;
import com.aicourse.entity.AiChatSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.AiChatSessionService;
import com.aicourse.service.AIService;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.LearningPathService;
import com.aicourse.service.LearningRecommendationService;
import com.aicourse.vo.QaAnswerVO;
import com.aicourse.vo.QaSourceVO;
import com.aicourse.vo.QaStreamVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AIController 覆盖率补强测试（不改动既有 {@link AIControllerTest}，专攻其未触及的分支与异常包装路径）。
 *
 * <p>逐条枚举源码分支后设计的用例族：</p>
 * <ul>
 *     <li>会话定位分叉：sessionId 提供且命中已有会话 / 未提供而新建 / 未命中退化新建；</li>
 *     <li>空值分叉：courseId 为 null、AI 返回 null（转义为空串）、历史消息 content 为 null、
 *         会话 title/createdAt/updatedAt 有无、lastMessage 空与超长截断（&gt;100 加省略号）；</li>
 *     <li>推荐与学情：无 courseId 时跳过生成、difficulty 缺省回落 MEDIUM、生成异常包装为 400；</li>
 *     <li>未登录语义：principal 为 null 的已认证主体 → 列表类接口返回空、建议接口 401（ApiCode.UNAUTHORIZED）、
 *         路径生成接口 400「缺少必要参数」；</li>
 *     <li>学习路径（含个性化端点全量分支）：goal 缺省文案、masteryData 缺失/空 Map/非数值值、
 *         掌握度驱动路径名与 personalized 标志、服务抛错包装；</li>
 *     <li>XSS 防护：user 输入与 AI 返回均经 HTML 转义后落库/回显。</li>
 * </ul>
 *
 * <p>鉴权面断言沿用 SecurityConfig 规则：POST /ai/qa/*、/ai/essay/* 任意已登录角色可用；
 * 其余 POST /ai/** 仅 TEACHER/ADMIN；所有 /ai/** 未认证一律 401。</p>
 */
@WebMvcTest(AIController.class)
class AIControllerCoverageTest extends ControllerTestBase {

    @MockitoBean
    private AIService aiService;
    @MockitoBean
    private AnalyticsService analyticsService;
    @MockitoBean
    private LearningPathService learningPathService;
    @MockitoBean
    private CourseEnrollmentService courseEnrollmentService;
    @MockitoBean
    private CourseService courseService;
    @MockitoBean
    private AiChatSessionService aiChatSessionService;
    @MockitoBean
    private LearningRecommendationService learningRecommendationService;

    private static AiChatSession session(String sessionId) {
        AiChatSession s = new AiChatSession();
        s.setSessionId(sessionId);
        return s;
    }

    private static AiChatMessage message(String role, String content) {
        AiChatMessage m = new AiChatMessage();
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    // ==================== /ai/qa/ask ====================

    @Test
    @DisplayName("POST /ai/qa/ask - 携带已存在 sessionId：复用会话不新建，AI 返回内容被 HTML 转义")
    void qaAsk_existingSession_reusedAndEscaped() throws Exception {
        when(aiChatSessionService.getBySessionId("s-9")).thenReturn(session("s-9"));
        when(aiService.ragAsk("什么是闭包", null))
                .thenReturn(new QaAnswerVO("<em>答案</em>",
                        List.of(new QaSourceVO(3L, "第<1>章", 0.8, "<script>危险片段</script>"))));

        MvcResult mvc = mockMvc.perform(post("/ai/qa/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"什么是闭包\",\"sessionId\":\"s-9\"}")
                        .with(withUser(5L, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.answer").value("&lt;em&gt;答案&lt;/em&gt;"))
                .andExpect(jsonPath("$.data.sessionId").value("s-9"))
                // 出处与答案同口径转义：资料名/片段里的标签与脚本不得原样下发
                .andExpect(jsonPath("$.data.sources[0].documentName").value("第&lt;1&gt;章"))
                .andExpect(jsonPath("$.data.sources[0].snippet").value("&lt;script&gt;危险片段&lt;/script&gt;"))
                .andExpect(jsonPath("$.data.sources[0].documentId").value(3))
                .andExpect(jsonPath("$.data.sources[0].relevanceScore").value(0.8));

        // 命中已有会话：不得再新建；courseId 未提供时以 null 透传 RAG
        verify(aiChatSessionService, never()).createSession(any(), any(), any());
        verify(aiService).ragAsk(eq("什么是闭包"), isNull());
        verify(aiChatSessionService).appendMessage("s-9", "user", "什么是闭包");
        verify(aiChatSessionService).appendMessage("s-9", "assistant", "&lt;em&gt;答案&lt;/em&gt;");
    }

    @Test
    @DisplayName("POST /ai/qa/ask - AI 返回 null：answer 回落空串而非 NPE")
    void qaAsk_nullAnswer_fallsBackToEmpty() throws Exception {
        when(aiChatSessionService.createSession(5L, null, null)).thenReturn(session("s-new"));
        when(aiService.ragAsk(anyString(), any())).thenReturn(null);

        MvcResult mvc = mockMvc.perform(post("/ai/qa/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"你好\"}").with(withUser(5L, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value(""))
                .andExpect(jsonPath("$.data.sessionId").value("s-new"));

        verify(aiChatSessionService, never()).getBySessionId(anyString());
    }

    // ==================== /ai/qa/continue ====================

    @Test
    @DisplayName("POST /ai/qa/continue - 无 sessionId 新建会话；历史 content 为 null 转空串；AI null 回落空串")
    void qaContinue_noSession_createsAndEscapesHistory() throws Exception {
        when(aiChatSessionService.createSession(2L, null, null)).thenReturn(session("s-2"));
        when(aiChatSessionService.getMessages("s-2"))
                .thenReturn(List.of(message("user", null), message("assistant", "ok")));
        when(aiService.chat(anyList(), eq(2L))).thenReturn(null);

        MvcResult mvc = mockMvc.perform(post("/ai/qa/continue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"<b>继续</b>\"}").with(withUser(2L, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value(""))
                .andExpect(jsonPath("$.data.sessionId").value("s-2"));

        ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.captor();
        verify(aiService).chat(captor.capture(), eq(2L));
        List<Map<String, String>> history = captor.getValue();
        assertEquals(3, history.size(), "两条历史 + 本轮用户消息");
        assertEquals("", history.get(0).get("content"), "null 历史内容应转义为空串");
        assertEquals("ok", history.get(1).get("content"));
        assertEquals("&lt;b&gt;继续&lt;/b&gt;", history.get(2).get("content"), "用户输入须 HTML 转义");
        verify(aiChatSessionService, never()).getBySessionId(anyString());
    }

    // ==================== /ai/qa/sessions ====================

    @Test
    @DisplayName("GET /ai/qa/sessions - 已认证但 principal 为 null：返回空列表且不查库")
    void getSessions_nullPrincipal_returnsEmpty() throws Exception {
        MvcResult mvc = mockMvc.perform(get("/ai/qa/sessions").with(withUser(null, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(aiChatSessionService, never()).listSessions(any());
    }

    @Test
    @DisplayName("GET /ai/qa/sessions - 各字段空值分叉：title null、时间戳有值、lastMessage 空/超长截断")
    void getSessions_richSessions_coversNullAndTruncation() throws Exception {
        AiChatSession a = session("a");
        a.setTitle(null);
        a.setMessageCount(1);
        a.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        a.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 3, 5));
        AiChatSession b = session("b");
        b.setTitle("标题B");
        b.setMessageCount(2);
        AiChatSession c = session("c");
        c.setTitle("标题C");
        when(aiChatSessionService.listSessions(7L)).thenReturn(List.of(a, b, c));
        when(aiChatSessionService.getMessages("a")).thenReturn(List.of(message("user", null)));
        when(aiChatSessionService.getMessages("b")).thenReturn(List.of(message("user", "x".repeat(120))));
        when(aiChatSessionService.getMessages("c")).thenReturn(List.of());

        MvcResult mvc = mockMvc.perform(get("/ai/qa/sessions").with(withUser(7L, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value(""))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-01-02T03:04"))
                .andExpect(jsonPath("$.data[0].updatedAt").value("2026-01-02T03:05"))
                .andExpect(jsonPath("$.data[0].lastMessage").value(""))
                .andExpect(jsonPath("$.data[1].lastMessage").value("x".repeat(100) + "…"))
                // application.yaml 的 spring.jackson.default-property-inclusion=non_null：
                // createdAt/updatedAt 为 null 时键整体不序列化，jsonPath 视为「无值」
                .andExpect(jsonPath("$.data[2].createdAt").doesNotExist())
                .andExpect(jsonPath("$.data[2].updatedAt").doesNotExist())
                .andExpect(jsonPath("$.data[2].lastMessage").value(""));
    }

    // ==================== /ai/qa/stream ====================

    @Test
    @DisplayName("GET /ai/qa/stream - SSE 逐段下发 JSON token 帧，末尾补空 sources 帧")
    void streamChat_returnsStreamedTokens() throws Exception {
        when(aiService.streamChat("hi", null))
                .thenReturn(new QaStreamVO(reactor.core.publisher.Flux.just("A", "B"), List.of()));

        MvcResult mvc = mockMvc.perform(get("/ai/qa/stream").param("question", "hi")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                // token 帧必须是 JSON 包装：裸文本里的换行会被 SSE 分行规则截断而失真
                .andExpect(content().string(containsString("{\"token\":\"A\"}")))
                .andExpect(content().string(containsString("{\"token\":\"B\"}")))
                // 无出处时仍要显式下发空 sources 末帧，前端据此判定检索为空而非缺帧
                .andExpect(content().string(containsString("{\"sources\":[]}")));

        // courseId 未提供：以 null 透传检索接地
        verify(aiService).streamChat(eq("hi"), isNull());
    }

    @Test
    @DisplayName("GET /ai/qa/stream - 携带 courseId 与出处：末帧下发转义后的 sources")
    void streamChat_withCourseId_emitsEscapedSources() throws Exception {
        when(aiService.streamChat("什么是闭包", 7L))
                .thenReturn(new QaStreamVO(reactor.core.publisher.Flux.just("答案"),
                        List.of(new QaSourceVO(3L, "第<1>章", 0.8, "<script>危险片段</script>"))));

        MvcResult mvc = mockMvc.perform(get("/ai/qa/stream").param("question", "什么是闭包")
                        .param("courseId", "7").with(withUser(1L, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult dispatched = mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andReturn();
        // 流内含中文，而 MockHttpServletResponse 默认按 ISO-8859-1 解码（content().string() 会失真），
        // 故直接按 UTF-8 取回原始字节（SSE 规范本就要求 UTF-8）。
        String body = new String(dispatched.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(body.contains("{\"token\":\"答案\"}"), body);
        // 出处与同步问答同口径转义：资料名/片段里的标签与脚本不得原样下发
        assertTrue(body.contains("第&lt;1&gt;章"), body);
        assertTrue(body.contains("&lt;script&gt;危险片段&lt;/script&gt;"), body);

        verify(aiService).streamChat(eq("什么是闭包"), eq(7L));
    }

    // ==================== /ai/recommendations/questions ====================

    @Test
    @DisplayName("GET /ai/recommendations/questions - 无 courseId：跳过生成，recommendations 为空数组")
    void questionRecommendations_noCourseId_skipsGeneration() throws Exception {
        MvcResult mvc = mockMvc.perform(get("/ai/recommendations/questions").with(withUser(1L, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").doesNotExist())
                .andExpect(jsonPath("$.data.recommendations").isArray())
                .andExpect(jsonPath("$.data.recommendations").isEmpty());

        verify(aiService, never()).recommendQuestionsJson(any(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("GET /ai/recommendations/questions - difficulty 缺省：回落 MEDIUM 生成")
    void questionRecommendations_missingDifficulty_defaultsMedium() throws Exception {
        when(aiService.recommendQuestionsJson(eq(8L), eq("MEDIUM"), eq(3))).thenReturn("{}");
        when(aiService.parseQuestions(eq("{}"), isNull()))
                .thenReturn(List.of(Map.of("content", "Q1")));

        MvcResult mvc = mockMvc.perform(get("/ai/recommendations/questions?courseId=8")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.difficulty").doesNotExist())
                .andExpect(jsonPath("$.data.recommendations[0].content").value("Q1"));

        verify(aiService).parseQuestions(eq("{}"), isNull());
    }

    @Test
    @DisplayName("GET /ai/recommendations/questions - 生成异常：包装为业务 400 且透出原因")
    void questionRecommendations_generationFailure_wrapped400() throws Exception {
        when(aiService.recommendQuestionsJson(any(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new RuntimeException("LLM连接超时"));

        MvcResult mvc = mockMvc.perform(get("/ai/recommendations/questions?courseId=8&difficulty=EASY")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("题目生成失败: LLM连接超时"));
    }

    // ==================== /ai/analysis/weak-points ====================

    @Test
    @DisplayName("GET /ai/analysis/weak-points - principal 为 null：跳过统计，weakPoints 空数组")
    void weakPoints_nullPrincipal_skipsStats() throws Exception {
        MvcResult mvc = mockMvc.perform(get("/ai/analysis/weak-points").with(withUser(null, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").doesNotExist())
                .andExpect(jsonPath("$.data.weakPoints").isEmpty());

        verify(analyticsService, never()).getUserDashboard(any());
        verify(aiService, never()).analyzeWeakPoints(any());
    }

    // ==================== /ai/advice/learning ====================

    @Test
    @DisplayName("GET /ai/advice/learning - principal 为 null：抛 ApiCode.UNAUTHORIZED 业务 401")
    void learningAdvice_nullPrincipal_401() throws Exception {
        MvcResult mvc = mockMvc.perform(get("/ai/advice/learning").with(withUser(null, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录"));

        verify(analyticsService, never()).getUserDashboard(any());
    }

    @Test
    @DisplayName("GET /ai/advice/learning - 统计/生成异常：包装为业务 400 且透出原因")
    void learningAdvice_serviceFailure_wrapped400() throws Exception {
        when(analyticsService.getUserDashboard(any())).thenThrow(new RuntimeException("统计服务不可用"));

        MvcResult mvc = mockMvc.perform(get("/ai/advice/learning?courseId=5").with(withUser(1L, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("生成学习建议失败: 统计服务不可用"));
    }

    // ==================== /ai/learning-path/generate ====================

    @Test
    @DisplayName("POST /ai/learning-path/generate - goal 缺省：回落默认文案「掌握课程核心知识」")
    void generateLearningPath_defaultGoal() throws Exception {
        MvcResult mvc = mockMvc.perform(post("/ai/learning-path/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"5\"}").with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("created"))
                .andExpect(jsonPath("$.data.message").value("学习路径已生成"));

        verify(learningPathService).generatePathPersonalized(
                eq(1L), eq(5L), eq("AI学习路径"), eq("掌握课程核心知识"), isNull());
    }

    @Test
    @DisplayName("POST /ai/learning-path/generate - principal 为 null：400 缺少必要参数")
    void generateLearningPath_nullPrincipal_missingParams400() throws Exception {
        MvcResult mvc = mockMvc.perform(post("/ai/learning-path/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"5\",\"goal\":\"g\"}").with(withUser(null, "TEACHER")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("缺少必要参数：userId或courseId"));

        verify(learningPathService, never()).generatePathPersonalized(
                any(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("POST /ai/learning-path/generate - 服务异常：包装为业务 400")
    void generateLearningPath_serviceFailure_wrapped400() throws Exception {
        when(learningPathService.generatePathPersonalized(any(), any(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("图数据缺失"));

        MvcResult mvc = mockMvc.perform(post("/ai/learning-path/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"5\",\"goal\":\"g\"}").with(withUser(1L, "ADMIN")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("学习路径生成失败: 图数据缺失"));
    }

    // ==================== /ai/learning-path/generate-personalized ====================

    @Test
    @DisplayName("POST /ai/learning-path/generate-personalized - 数值掌握度：走掌握度驱动路径名，personalized=true")
    void personalized_numericMastery_drivenPath() throws Exception {
        MvcResult mvc = mockMvc.perform(post("/ai/learning-path/generate-personalized")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"7\",\"goal\":\"补强\",\"masteryData\":{\"数据结构\":30.5,\"算法\":80}}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("created"))
                .andExpect(jsonPath("$.data.personalized").value(true));

        ArgumentCaptor<Map<String, Double>> captor = ArgumentCaptor.captor();
        verify(learningPathService).generatePathPersonalized(
                eq(1L), eq(7L), eq("个性化学习路径（掌握度驱动）"), eq("补强"), captor.capture());
        assertEquals(Map.of("数据结构", 30.5, "算法", 80.0), captor.getValue(), "非数值应被过滤、数值转 Double");
    }

    @Test
    @DisplayName("POST /ai/learning-path/generate-personalized - masteryData 为空 Map：退化标准路径且 goal 缺省")
    void personalized_emptyMastery_fallsBackStandard() throws Exception {
        MvcResult mvc = mockMvc.perform(post("/ai/learning-path/generate-personalized")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"7\",\"masteryData\":{}}").with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personalized").value(false));

        verify(learningPathService).generatePathPersonalized(
                eq(1L), eq(7L), eq("AI学习路径"), eq("针对性补强薄弱点"), isNull());
    }

    @Test
    @DisplayName("POST /ai/learning-path/generate-personalized - masteryData 仅含非数值：mastery 为空 Map，走标准路径")
    void personalized_nonNumericOnly_standardPath() throws Exception {
        MvcResult mvc = mockMvc.perform(post("/ai/learning-path/generate-personalized")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"7\",\"goal\":\"g\",\"masteryData\":{\"k\":\"abc\"}}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personalized").value(false));

        verify(learningPathService).generatePathPersonalized(
                eq(1L), eq(7L), eq("AI学习路径"), eq("g"), eq(Map.of()));
    }

    @Test
    @DisplayName("POST /ai/learning-path/generate-personalized - 缺失 masteryData：mastery=null 标准路径")
    void personalized_missingMastery_standardPath() throws Exception {
        MvcResult mvc = mockMvc.perform(post("/ai/learning-path/generate-personalized")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"7\",\"goal\":\"g\"}").with(withUser(2L, "ADMIN")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personalized").value(false));

        verify(learningPathService).generatePathPersonalized(
                eq(2L), eq(7L), eq("AI学习路径"), eq("g"), isNull());
    }

    @Test
    @DisplayName("POST /ai/learning-path/generate-personalized - principal 为 null：400 缺少必要参数")
    void personalized_nullPrincipal_missingParams400() throws Exception {
        MvcResult mvc = mockMvc.perform(post("/ai/learning-path/generate-personalized")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"7\"}").with(withUser(null, "TEACHER")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("缺少必要参数：userId或courseId"));
    }

    @Test
    @DisplayName("POST /ai/learning-path/generate-personalized - 服务异常：包装为业务 400")
    void personalized_serviceFailure_wrapped400() throws Exception {
        when(learningPathService.generatePathPersonalized(any(), any(), anyString(), anyString(), any()))
                .thenThrow(new BusinessException(500, "路径引擎不可用"));

        MvcResult mvc = mockMvc.perform(post("/ai/learning-path/generate-personalized")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"7\",\"goal\":\"g\"}").with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("学习路径生成失败: 路径引擎不可用"));
    }

    // ==================== 鉴权面（安全语义正证） ====================

    @Test
    @DisplayName("POST /ai/qa/ask - 未认证 401；GET /ai/qa/sessions - 未认证 401")
    void qaEndpoints_unauthenticated_401() throws Exception {
        mockMvc.perform(post("/ai/qa/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/ai/qa/sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /ai/learning-path/generate-personalized - STUDENT 角色不符 403（写操作仅教师/管理员）")
    void personalized_student_forbidden403() throws Exception {
        mockMvc.perform(post("/ai/learning-path/generate-personalized")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"7\"}").with(withUser(9L, "STUDENT")))
                .andExpect(status().isForbidden());
        verify(learningPathService, never()).generatePathPersonalized(
                any(), any(), anyString(), anyString(), any());
    }

    // ==================== Authentication 参数为 null（withUser(null, …) 注入的切片可复现） ====================

    @Test
    @DisplayName("POST /ai/qa/ask - Authentication 为 null：userId 以 null 建会话，链路不中断")
    void qaAsk_nullAuth_createsSessionWithNullUser() throws Exception {
        when(aiChatSessionService.createSession(null, null, null)).thenReturn(session("s-anon"));
        when(aiService.ragAsk("anon?", null)).thenReturn(new QaAnswerVO("ok", List.of()));

        MvcResult mvc = mockMvc.perform(post("/ai/qa/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"anon?\"}").with(withUser(null, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value("s-anon"))
                .andExpect(jsonPath("$.data.answer").value("ok"));

        verify(aiChatSessionService).createSession(isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("POST /ai/qa/continue - Authentication 为 null：新建会话并以 null userId 委托 chat")
    void qaContinue_nullAuth_createsSession() throws Exception {
        when(aiChatSessionService.createSession(null, null, null)).thenReturn(session("s-canon"));
        when(aiChatSessionService.getMessages("s-canon")).thenReturn(List.of());
        when(aiService.chat(anyList(), isNull())).thenReturn("done");

        MvcResult mvc = mockMvc.perform(post("/ai/qa/continue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"继续\"}").with(withUser(null, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value("s-canon"))
                .andExpect(jsonPath("$.data.answer").value("done"));

        verify(aiService).chat(anyList(), isNull());
    }

    @Test
    @DisplayName("GET /ai/recommendations/learning - Authentication 为 null：以 null 委托推荐服务")
    void learningRecommendations_nullAuth_delegatesNull() throws Exception {
        when(learningRecommendationService.recommendLearning(isNull()))
                .thenReturn(Map.of("recommendation", "兜底推荐"));

        MvcResult mvc = mockMvc.perform(get("/ai/recommendations/learning").with(withUser(null, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendation").value("兜底推荐"));

        verify(learningRecommendationService).recommendLearning(isNull());
    }
}
