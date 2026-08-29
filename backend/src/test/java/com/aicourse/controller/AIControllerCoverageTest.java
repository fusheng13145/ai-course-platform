package com.aicourse.controller;

import com.aicourse.entity.AiChatMessage;
import com.aicourse.entity.AiChatSession;
import com.aicourse.entity.LearningPath;
import com.aicourse.exception.GlobalExceptionHandler;
import com.aicourse.service.AIService;
import com.aicourse.service.AiChatSessionService;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.LearningPathService;
import com.aicourse.service.LearningRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖率测试：AIController（独立 MockMvc，不加载 Spring 上下文）。
 * 覆盖全部 AI 接口（多为异步 Callable / Flux）的成功与错误/异常分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AIController 覆盖率测试")
class AIControllerCoverageTest {

    @Mock
    private AIService aiService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private LearningPathService learningPathService;
    @Mock
    private CourseEnrollmentService courseEnrollmentService;
    @Mock
    private CourseService courseService;
    @Mock
    private AiChatSessionService aiChatSessionService;
    @Mock
    private LearningRecommendationService learningRecommendationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        AIController controller = new AIController(aiService, analyticsService, learningPathService,
                courseEnrollmentService, courseService, aiChatSessionService, learningRecommendationService);
        // 独立 MockMvc 需手动注册全局异常处理器，使控制器抛出的 BusinessException
        // （如未登录、AI 生成异常）被统一转换为对应的 4xx，而非穿透为 500。
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        AiChatSession session = makeSession("s1", "t", 150);
        lenient().when(aiChatSessionService.getBySessionId(any())).thenReturn(null);
        lenient().when(aiChatSessionService.createSession(any(), any())).thenReturn(session);
        lenient().doNothing().when(aiChatSessionService).appendMessage(any(), any(), any());
        lenient().when(aiChatSessionService.listSessions(any())).thenReturn(List.of(session));
        lenient().when(aiChatSessionService.getMessages(any())).thenReturn(List.of(makeMessage("user", "hello world")));

        lenient().when(aiService.ragAsk(any(), any())).thenReturn("answer");
        lenient().when(aiService.chat(any(), any())).thenReturn("answer");
        lenient().when(aiService.streamChat(any())).thenReturn(Flux.just("a", "b"));
        lenient().when(aiService.generateContent(any(), any())).thenReturn("result");
        lenient().when(aiService.recommendQuestionsJson(any(), any(), anyInt())).thenReturn("[]");
        lenient().when(aiService.parseQuestions(any(), any())).thenReturn(List.of(Map.of("q", 1)));
        lenient().when(aiService.analyzeWeakPoints(any())).thenReturn(List.of(Map.of("k", "v")));
        lenient().when(aiService.generateLearningAdvice(any())).thenReturn(Map.of("advice", "x"));

        lenient().when(analyticsService.getUserDashboard(any())).thenReturn(Map.of("score", 1));
        lenient().when(learningRecommendationService.recommendLearning(any())).thenReturn(Map.of("r", 1));
        lenient().when(learningPathService.generatePathPersonalized(any(), any(), any(), any(), any()))
                .thenReturn(new LearningPath());
    }

    private AiChatSession makeSession(String sid, String title, int msgLen) {
        AiChatSession s = new AiChatSession();
        s.setSessionId(sid);
        s.setTitle(title);
        s.setMessageCount(msgLen);
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        return s;
    }

    private AiChatMessage makeMessage(String role, String content) {
        AiChatMessage m = new AiChatMessage();
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    private static RequestPostProcessor withAuth(Long userId, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        Authentication auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        return request -> {
            request.setUserPrincipal(auth);
            if (userId != null) request.setRemoteUser(String.valueOf(userId));
            return request;
        };
    }

    private void dispatch(MvcResult mvc) throws Exception {
        dispatch(mvc, status().isOk());
    }

    private void dispatch(MvcResult mvc, org.springframework.test.web.servlet.ResultMatcher statusMatcher) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(mvc)).andExpect(statusMatcher);
    }

    // ===================== 智能问答 =====================

    @Test
    @DisplayName("POST /ai/qa/ask 成功(新建会话) / 复用会话 / 未登录")
    void qaAsk() throws Exception {
        // 新建会话
        MvcResult m1 = mockMvc.perform(MockMvcRequestBuilders.post("/ai/qa/ask").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json").content("{\"question\":\"什么是Java?\"}"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m1);

        // 复用会话
        AiChatSession existing = makeSession("s9", "old", 3);
        when(aiChatSessionService.getBySessionId("s9")).thenReturn(existing);
        MvcResult m2 = mockMvc.perform(MockMvcRequestBuilders.post("/ai/qa/ask").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json").content("{\"question\":\"继续\",\"sessionId\":\"s9\"}"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m2);

        // 未登录（userId=null）
        MvcResult m3 = mockMvc.perform(MockMvcRequestBuilders.post("/ai/qa/ask")
                        .contentType("application/json").content("{\"question\":\"hi\"}"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m3);
    }

    @Test
    @DisplayName("POST /ai/qa/continue 成功 / 复用会话 / 未登录")
    void qaContinue() throws Exception {
        MvcResult m1 = mockMvc.perform(MockMvcRequestBuilders.post("/ai/qa/continue").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json").content("{\"message\":\"继续聊\"}"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m1);

        AiChatSession existing = makeSession("s9", "old", 3);
        when(aiChatSessionService.getBySessionId("s9")).thenReturn(existing);
        MvcResult m2 = mockMvc.perform(MockMvcRequestBuilders.post("/ai/qa/continue").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json").content("{\"message\":\"更多\",\"sessionId\":\"s9\"}"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m2);

        MvcResult m3 = mockMvc.perform(MockMvcRequestBuilders.post("/ai/qa/continue")
                        .contentType("application/json").content("{\"message\":\"hi\"}"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m3);
    }

    @Test
    @DisplayName("GET /ai/qa/sessions 有登录(长/短消息分支) / 未登录")
    void getSessions() throws Exception {
        AiChatSession longMsg = makeSession("s-long", "t", 200);
        AiChatSession shortMsg = makeSession("s-short", "t", 5);
        when(aiChatSessionService.listSessions(1L)).thenReturn(List.of(longMsg, shortMsg));
        MvcResult m1 = mockMvc.perform(MockMvcRequestBuilders.get("/ai/qa/sessions").with(withAuth(1L, "STUDENT")))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m1);

        MvcResult m2 = mockMvc.perform(MockMvcRequestBuilders.get("/ai/qa/sessions"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m2);
    }

    @Test
    @DisplayName("GET /ai/qa/stream 成功 / 空内容(校验失败)")
    void streamChat() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/ai/qa/stream").param("question", "hi"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/ai/qa/stream")).andExpect(status().is4xxClientError());
    }

    // ===================== 作文 =====================

    @Test
    @DisplayName("POST /ai/essay/correct / polish")
    void essay() throws Exception {
        MvcResult c = mockMvc.perform(MockMvcRequestBuilders.post("/ai/essay/correct")
                        .contentType("application/json").content("{\"content\":\"我的作文\"}"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(c);
        MvcResult p = mockMvc.perform(MockMvcRequestBuilders.post("/ai/essay/polish")
                        .contentType("application/json").content("{\"content\":\"我的作文\"}"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(p);
    }

    // ===================== 推荐 =====================

    @Test
    @DisplayName("GET /ai/recommendations/learning")
    void learningRecommendations() throws Exception {
        MvcResult m = mockMvc.perform(MockMvcRequestBuilders.get("/ai/recommendations/learning").with(withAuth(1L, "STUDENT")))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m);
    }

    @Test
    @DisplayName("GET /ai/recommendations/questions 有课程 / 无课程 / 生成异常")
    void questionRecommendations() throws Exception {
        MvcResult m1 = mockMvc.perform(MockMvcRequestBuilders.get("/ai/recommendations/questions")
                        .param("courseId", "10").param("difficulty", "HARD"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m1);
        MvcResult m2 = mockMvc.perform(MockMvcRequestBuilders.get("/ai/recommendations/questions"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m2);
        when(aiService.recommendQuestionsJson(any(), any(), anyInt())).thenThrow(new RuntimeException("llm down"));
        MvcResult m3 = mockMvc.perform(MockMvcRequestBuilders.get("/ai/recommendations/questions")
                        .param("courseId", "10"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m3, status().isBadRequest());
    }

    // ===================== 学情 =====================

    @Test
    @DisplayName("GET /ai/analysis/weak-points 有登录 / 未登录")
    void weakPointsAnalysis() throws Exception {
        MvcResult m1 = mockMvc.perform(MockMvcRequestBuilders.get("/ai/analysis/weak-points")
                        .param("courseId", "10").with(withAuth(1L, "STUDENT")))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m1);
        MvcResult m2 = mockMvc.perform(MockMvcRequestBuilders.get("/ai/analysis/weak-points"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m2);
    }

    @Test
    @DisplayName("GET /ai/advice/learning 成功 / 未登录(异常) / 生成异常")
    void learningAdvice() throws Exception {
        MvcResult m1 = mockMvc.perform(MockMvcRequestBuilders.get("/ai/advice/learning")
                        .param("courseId", "10").with(withAuth(1L, "STUDENT")))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m1);
        // 未登录 -> throw BusinessException(UNAUTHORIZED) -> 401
        MvcResult m2 = mockMvc.perform(MockMvcRequestBuilders.get("/ai/advice/learning"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m2, status().isUnauthorized());
        // 生成异常 -> catch -> BusinessException -> 400
        when(aiService.generateLearningAdvice(any())).thenThrow(new RuntimeException("boom"));
        MvcResult m3 = mockMvc.perform(MockMvcRequestBuilders.get("/ai/advice/learning")
                .param("courseId", "10").with(withAuth(1L, "STUDENT")))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m3, status().isBadRequest());
    }

    // ===================== 学习路径 =====================

    @Test
    @DisplayName("POST /ai/learning-path/generate 成功 / 缺课程(校验失败400) / 生成异常")
    void generateLearningPath() throws Exception {
        MvcResult m1 = mockMvc.perform(MockMvcRequestBuilders.post("/ai/learning-path/generate")
                        .with(withAuth(1L, "STUDENT"))
                        .contentType("application/json").content("{\"courseId\":\"10\",\"goal\":\"掌握\"}"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m1);
        // 缺 courseId -> LearningPathRequest.courseId @NotBlank 校验失败 -> 同步 400（不触发异步）
        mockMvc.perform(MockMvcRequestBuilders.post("/ai/learning-path/generate")
                        .with(withAuth(1L, "STUDENT"))
                        .contentType("application/json").content("{\"goal\":\"掌握\"}"))
                .andExpect(status().isBadRequest());
        // 生成异常 -> 控制器 catch 后抛 BusinessException -> 400
        when(learningPathService.generatePathPersonalized(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("fail"));
        MvcResult m3 = mockMvc.perform(MockMvcRequestBuilders.post("/ai/learning-path/generate")
                        .with(withAuth(1L, "STUDENT"))
                        .contentType("application/json").content("{\"courseId\":\"10\"}"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m3, status().isBadRequest());
    }

    @Test
    @DisplayName("POST /ai/learning-path/generate-personalized 有掌握度(数值/非数值) / 无掌握度 / 异常")
    void generatePersonalizedLearningPath() throws Exception {
        // 掌握度含数值与非数值
        String body = "{\"courseId\":\"10\",\"goal\":\"补强\",\"masteryData\":{\"A\":0.8,\"B\":\"high\"}}";
        MvcResult m1 = mockMvc.perform(MockMvcRequestBuilders.post("/ai/learning-path/generate-personalized")
                        .with(withAuth(1L, "STUDENT")).contentType("application/json").content(body))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m1);
        // 无掌握度
        MvcResult m2 = mockMvc.perform(MockMvcRequestBuilders.post("/ai/learning-path/generate-personalized")
                        .with(withAuth(1L, "STUDENT"))
                        .contentType("application/json").content("{\"courseId\":\"10\"}"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m2);
        // 生成异常
        when(learningPathService.generatePathPersonalized(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("fail"));
        MvcResult m3 = mockMvc.perform(MockMvcRequestBuilders.post("/ai/learning-path/generate-personalized")
                        .with(withAuth(1L, "STUDENT"))
                        .contentType("application/json").content("{\"courseId\":\"10\"}"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        dispatch(m3, status().isBadRequest());
    }
}
