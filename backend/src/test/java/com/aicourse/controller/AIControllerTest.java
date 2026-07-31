package com.aicourse.controller;
import com.aicourse.controller.AIController;
import com.aicourse.entity.AiChatSession;
import com.aicourse.entity.LearningPath;
import com.aicourse.service.AIService;
import com.aicourse.service.AiChatSessionService;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.LearningPathService;
import com.aicourse.service.LearningRecordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * AIController 接口测试。
 * 升级自生成器产出的弱断言（status != 401 && status != 403），改为真实业务断言：
 * 对 service 方法打桩并用 jsonPath 校验响应体关键字段透传，verify 确认路由委托与参数透传。
 * 所有接口返回 Callable（异步），故采用 asyncDispatch 解析最终响应。
 * 安全规则（SecurityConfig）：POST/PUT/DELETE /ai/** 需 TEACHER/ADMIN；GET /ai/** 任意已认证角色。
 * 既有 401/403 测试保持不变（POST 接口 STUDENT 越权 403 符合 URL 级安全规则）。
 */
@WebMvcTest(AIController.class)
class AIControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.AIService aiService;
    @MockitoBean
    private com.aicourse.service.AnalyticsService analyticsService;
    @MockitoBean
    private com.aicourse.service.LearningPathService learningPathService;
    @MockitoBean
    private com.aicourse.service.CourseEnrollmentService courseEnrollmentService;
    @MockitoBean
    private com.aicourse.service.LearningRecordService learningRecordService;
    @MockitoBean
    private com.aicourse.service.CourseService courseService;
    @MockitoBean
    private com.aicourse.service.AiChatSessionService aiChatSessionService;
    @MockitoBean
    private com.aicourse.service.KnowledgePointService knowledgePointService;
    @MockitoBean
    private com.aicourse.service.LearningRecommendationService learningRecommendationService;

    // ==================== 智能问答 ====================

    @Test
    @DisplayName("POST /ai/qa/ask - TEACHER 智能答疑返回 code=200 且字段透传")
    void qaAsk_authorized_passes_security() throws Exception {
        AiChatSession session = new AiChatSession();
        session.setSessionId("sess-1");
        when(aiChatSessionService.createSession(anyLong(), any())).thenReturn(session);
        when(aiChatSessionService.getMessages(anyString())).thenReturn(Collections.emptyList());
        when(aiService.ragAsk(anyString(), any())).thenReturn("RAG答案");

        MvcResult mvc = mockMvc.perform(post("/ai/qa/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"什么是闭包\",\"courseId\":\"2\"}").with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.answer").value("RAG答案"))
                .andExpect(jsonPath("$.data.sessionId").value("sess-1"));

        verify(aiService).ragAsk(eq("什么是闭包"), eq(2L));
        verify(aiChatSessionService).createSession(eq(1L), any());
    }


    @Test
    @DisplayName("POST /ai/qa/ask - 未认证返回401")
    void qaAsk_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/ai/qa/ask").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /ai/qa/ask - STUDENT角色返回403")
    void qaAsk_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/ai/qa/ask").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /ai/qa/continue - TEACHER 继续对话返回 code=200 且字段透传")
    void qaContinue_authorized_passes_security() throws Exception {
        AiChatSession session = new AiChatSession();
        session.setSessionId("sess-1");
        when(aiChatSessionService.getBySessionId(anyString())).thenReturn(session);
        when(aiChatSessionService.getMessages(anyString())).thenReturn(Collections.emptyList());
        when(aiService.chat(anyList(), anyLong())).thenReturn("继续答案");

        MvcResult mvc = mockMvc.perform(post("/ai/qa/continue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"继续\",\"sessionId\":\"sess-1\"}").with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.answer").value("继续答案"))
                .andExpect(jsonPath("$.data.sessionId").value("sess-1"));

        verify(aiChatSessionService).getBySessionId(eq("sess-1"));
        verify(aiService).chat(anyList(), eq(1L));
    }


    @Test
    @DisplayName("POST /ai/qa/continue - 未认证返回401")
    void qaContinue_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/ai/qa/continue").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /ai/qa/continue - STUDENT角色返回403")
    void qaContinue_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/ai/qa/continue").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /ai/qa/sessions - STUDENT 获取会话列表返回 code=200 且为数组")
    void getSessions_authorized_passes_security() throws Exception {
        AiChatSession session = new AiChatSession();
        session.setSessionId("s1");
        session.setTitle("标题");
        session.setMessageCount(3);
        when(aiChatSessionService.listSessions(anyLong())).thenReturn(List.of(session));
        when(aiChatSessionService.getMessages(anyString())).thenReturn(Collections.emptyList());

        MvcResult mvc = mockMvc.perform(get("/ai/qa/sessions").with(withUser(1L, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].sessionId").value("s1"));

        verify(aiChatSessionService).listSessions(eq(1L));
    }


    @Test
    @DisplayName("GET /ai/qa/sessions - 未认证返回401")
    void getSessions_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/ai/qa/sessions"))
                .andExpect(status().isUnauthorized());
    }


    // ==================== 作文批改 ====================

    @Test
    @DisplayName("POST /ai/essay/correct - TEACHER 作文批改返回 code=200 且字段透传")
    void essayCorrect_authorized_passes_security() throws Exception {
        when(aiService.generateContent(anyString(), anyString())).thenReturn("批改结果");

        MvcResult mvc = mockMvc.perform(post("/ai/essay/correct").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"我的作文\"}").with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.result").value("批改结果"));

        verify(aiService).generateContent(eq("correct"), eq("我的作文"));
    }


    @Test
    @DisplayName("POST /ai/essay/correct - 未认证返回401")
    void essayCorrect_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/ai/essay/correct").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /ai/essay/correct - STUDENT角色返回403")
    void essayCorrect_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/ai/essay/correct").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /ai/essay/polish - TEACHER 作文润色返回 code=200 且字段透传")
    void essayPolish_authorized_passes_security() throws Exception {
        when(aiService.generateContent(anyString(), anyString())).thenReturn("润色结果");

        MvcResult mvc = mockMvc.perform(post("/ai/essay/polish").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"我的作文\"}").with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.result").value("润色结果"));

        verify(aiService).generateContent(eq("polish"), any());
    }


    @Test
    @DisplayName("POST /ai/essay/polish - 未认证返回401")
    void essayPolish_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/ai/essay/polish").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /ai/essay/polish - STUDENT角色返回403")
    void essayPolish_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/ai/essay/polish").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    // ==================== 智能推荐 ====================

    @Test
    @DisplayName("GET /ai/recommendations/learning - STUDENT 学习推荐返回 code=200 且字段透传")
    void learningRecommendations_authorized_passes_security() throws Exception {
        // 业务编排已下沉至 LearningRecommendationService，Controller 仅做委托
        Map<String, Object> recommendationResult = new HashMap<>();
        recommendationResult.put("recommendation", "推荐文本");
        recommendationResult.put("dataDriven", true);
        when(learningRecommendationService.recommendLearning(anyLong())).thenReturn(recommendationResult);

        MvcResult mvc = mockMvc.perform(get("/ai/recommendations/learning").with(withUser(1L, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.recommendation").value("推荐文本"))
                .andExpect(jsonPath("$.data.dataDriven").value(true));

        verify(learningRecommendationService).recommendLearning(anyLong());
    }


    @Test
    @DisplayName("GET /ai/recommendations/learning - 未认证返回401")
    void learningRecommendations_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/ai/recommendations/learning"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /ai/recommendations/questions - STUDENT 题目推荐返回 code=200 且字段透传")
    void questionRecommendations_authorized_passes_security() throws Exception {
        when(aiService.recommendQuestionsJson(anyLong(), anyString(), anyInt()))
                .thenReturn("{\"questions\":[{\"content\":\"Q1\",\"type\":\"SINGLE_CHOICE\",\"difficulty\":\"EASY\"}]}");
        when(aiService.parseQuestions(anyString(), any()))
                .thenReturn(List.of(Map.of("content", "Q1", "type", "SINGLE_CHOICE", "difficulty", "EASY")));

        MvcResult mvc = mockMvc.perform(get("/ai/recommendations/questions?courseId=5&difficulty=EASY").with(withUser(1L, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.courseId").value(5))
                .andExpect(jsonPath("$.data.recommendations[0].content").value("Q1"));

        verify(aiService).recommendQuestionsJson(eq(5L), eq("EASY"), eq(3));
        verify(aiService).parseQuestions(anyString(), eq("EASY"));
    }


    @Test
    @DisplayName("GET /ai/recommendations/questions?courseId=1&difficulty=test - 未认证返回401")
    void questionRecommendations_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/ai/recommendations/questions?courseId=1&difficulty=test"))
                .andExpect(status().isUnauthorized());
    }


    // ==================== 学情分析 ====================

    @Test
    @DisplayName("GET /ai/analysis/weak-points - STUDENT 薄弱点分析返回 code=200 且字段透传")
    void weakPointsAnalysis_authorized_passes_security() throws Exception {
        when(analyticsService.getUserDashboard(anyLong())).thenReturn(Map.of("score", 80));
        when(aiService.analyzeWeakPoints(any()))
                .thenReturn(List.of(Map.of("description", "薄弱点描述", "severity", "LOW")));

        MvcResult mvc = mockMvc.perform(get("/ai/analysis/weak-points?courseId=5").with(withUser(1L, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.weakPoints[0].description").value("薄弱点描述"));

        verify(analyticsService).getUserDashboard(eq(1L));
        verify(aiService).analyzeWeakPoints(any());
    }


    @Test
    @DisplayName("GET /ai/analysis/weak-points?courseId=1 - 未认证返回401")
    void weakPointsAnalysis_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/ai/analysis/weak-points?courseId=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /ai/advice/learning - STUDENT 学习建议返回 code=200 且字段透传")
    void learningAdvice_authorized_passes_security() throws Exception {
        when(analyticsService.getUserDashboard(anyLong())).thenReturn(Map.of("score", 80));
        when(aiService.generateLearningAdvice(any())).thenReturn(Map.of(
                "summary", "学习建议",
                "items", List.of(Map.of("title", "标题1", "detail", "详情1", "level", "HIGH"))));

        MvcResult mvc = mockMvc.perform(get("/ai/advice/learning?courseId=5").with(withUser(1L, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.summary").value("学习建议"))
                .andExpect(jsonPath("$.data.items[0].title").value("标题1"));

        verify(aiService).generateLearningAdvice(any());
    }


    @Test
    @DisplayName("GET /ai/advice/learning?courseId=1 - 未认证返回401")
    void learningAdvice_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/ai/advice/learning?courseId=1"))
                .andExpect(status().isUnauthorized());
    }


    // ==================== 学习路径 ====================

    @Test
    @DisplayName("POST /ai/learning-path/generate - TEACHER 生成学习路径返回 code=200 且字段透传")
    void generateLearningPath_authorized_passes_security() throws Exception {
        LearningPath lp = new LearningPath();
        lp.setId(99L);
        when(learningPathService.generatePathPersonalized(eq(1L), eq(5L), anyString(), anyString(), isNull()))
                .thenReturn(lp);

        MvcResult mvc = mockMvc.perform(post("/ai/learning-path/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"5\",\"goal\":\"掌握核心\"}").with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("created"));

        verify(learningPathService).generatePathPersonalized(eq(1L), eq(5L), anyString(), eq("掌握核心"), isNull());
    }


    @Test
    @DisplayName("POST /ai/learning-path/generate - 未认证返回401")
    void generateLearningPath_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/ai/learning-path/generate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /ai/learning-path/generate - STUDENT角色返回403")
    void generateLearningPath_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/ai/learning-path/generate").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    // ==================== 已废弃兼容接口（端点已移除，仅保留安全行为断言） ====================
    // 历史遗留端点 /chat /generate-question /generate-summary /check-plagiarism /rag/ask
    // 已从 AIController 移除（见类注释），对应成功路径测试一并删除；
    // 未认证 401 / 越权 403 断言仍有效（安全过滤器先于路由匹配生效）。

    @Test
    @DisplayName("POST /ai/chat - 未认证返回401")
    void chat_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /ai/chat - STUDENT角色返回403")
    void chat_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /ai/generate-question - 未认证返回401")
    void generateQuestion_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/ai/generate-question").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /ai/generate-question - STUDENT角色返回403")
    void generateQuestion_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/ai/generate-question").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /ai/generate-summary - 未认证返回401")
    void generateSummary_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/ai/generate-summary").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /ai/generate-summary - STUDENT角色返回403")
    void generateSummary_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/ai/generate-summary").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /ai/check-plagiarism - 未认证返回401")
    void checkPlagiarism_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/ai/check-plagiarism").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /ai/check-plagiarism - STUDENT角色返回403")
    void checkPlagiarism_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/ai/check-plagiarism").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /ai/rag/ask?courseId=1 - 未认证返回401")
    void ragAsk_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/ai/rag/ask?courseId=1"))
                .andExpect(status().isUnauthorized());
    }

}
