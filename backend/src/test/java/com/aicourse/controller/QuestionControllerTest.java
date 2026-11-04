package com.aicourse.controller;
import com.aicourse.controller.QuestionController;
import com.aicourse.common.ApiCode;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.AIService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.QuestionStatsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * QuestionController 接口测试（升级版）。
 * 将「伪覆盖弱断言」改造为真实业务断言：对每个 endpoint 依赖的 service 方法打桩返回合理业务对象，
 * 断言 HTTP 200 + ApiResponse.code=200 + 关键字段透传（jsonPath），并 verify 路由正确委托与参数透传。
 */
@WebMvcTest(QuestionController.class)
class QuestionControllerTest extends ControllerTestBase {

    @MockitoBean
    private QuestionService questionService;
    @MockitoBean
    private AIService aiService;
    @MockitoBean
    private com.aicourse.service.KnowledgePointService knowledgePointService;
    @MockitoBean
    private com.aicourse.service.component.ai.AiGenerationPolicy aiGenerationPolicy;
    @MockitoBean
    private CourseAccessControl courseAccessControl;

    /**
     * 治理开关缺省即「允许」（与 {@code AiGenerationPolicy} 口径一致，缺省必须等于现状）：
     * 既有出题用例的行为不因此改变；被禁用的用例在自身内部覆盖该桩。
     */
    @BeforeEach
    void allowAiGenerationByDefault() {
        lenient().when(aiGenerationPolicy.isTeacherQuestionGenerationEnabled()).thenReturn(true);
    }

    private Question sampleQuestion() {
        Question q = new Question();
        q.setId(1L);
        q.setCourseId(1L);
        q.setType("SINGLE");
        return q;
    }

    @Test
    @DisplayName("GET /questions?page=1&size=1&courseId=1&type=test&difficulty=test - 返回200且data.list为数组")
    void list_authorized_returns_success() throws Exception {
        when(questionService.page(any(), any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());
        mockMvc.perform(get("/questions?page=1&size=1&courseId=1&type=test&difficulty=test").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
        verify(questionService).page(any(), any());
    }

    @Test
    @DisplayName("GET /questions?page=1&size=1&courseId=1&type=test&difficulty=test - 未认证返回401")
    void list_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/questions?page=1&size=1&courseId=1&type=test&difficulty=test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /questions/1 - 返回200且data透传题目id")
    void getById_authorized_returns_success() throws Exception {
        when(questionService.getById(any())).thenReturn(sampleQuestion());
        mockMvc.perform(get("/questions/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(questionService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /questions/1 - 未认证返回401")
    void getById_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/questions/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /questions/create - TEACHER角色返回200且data透传创建题目")
    void create_authorized_returns_success() throws Exception {
        when(questionService.createQuestion(any())).thenReturn(sampleQuestion());
        mockMvc.perform(post("/questions/create").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"题干\",\"courseId\":1,\"type\":\"SINGLE_CHOICE\"}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(questionService).createQuestion(any(Question.class));
    }

    @Test
    @DisplayName("POST /questions/create - 未认证返回401")
    void create_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/questions/create").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /questions/create - STUDENT角色返回403")
    void create_student_forbidden_403() throws Exception {
        // 载荷必须是合法入参：DTO 校验在方法调用前执行，非法载荷会先得到 400，测不到角色闸门
        mockMvc.perform(post("/questions/create").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"题干\",\"courseId\":1,\"type\":\"SINGLE_CHOICE\"}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /questions/generate-by-ai - TEACHER角色返回200且data透传题目")
    void generateByAI_authorized_returns_success() throws Exception {
        when(questionService.generateByAI(any(), any(), any(), any())).thenReturn(sampleQuestion());
        mockMvc.perform(post("/questions/generate-by-ai").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"type\":\"SINGLE\",\"difficulty\":\"EASY\",\"knowledgePointNames\":\"x\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(questionService).generateByAI(eq(1L), eq("SINGLE"), eq("EASY"), any());
    }

    @Test
    @DisplayName("POST /questions/generate-by-ai - 未认证返回401")
    void generateByAI_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/questions/generate-by-ai").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /questions/generate-by-ai - STUDENT角色返回403")
    void generateByAI_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/questions/generate-by-ai").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /questions/1/review - TEACHER角色返回200且正确委托reviewQuestion")
    void review_authorized_returns_success() throws Exception {
        when(questionService.getById(1L)).thenReturn(sampleQuestion());
        doNothing().when(questionService).reviewQuestion(any(), anyBoolean());
        mockMvc.perform(post("/questions/1/review").contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(questionService).reviewQuestion(eq(1L), eq(true));
    }

    @Test
    @DisplayName("POST /questions/1/review - 未认证返回401")
    void review_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/questions/1/review").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /questions/1/review - STUDENT角色返回403")
    void review_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/questions/1/review").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /questions/1/copy - TEACHER角色返回200且data透传复制题目")
    void copy_authorized_returns_success() throws Exception {
        when(questionService.getById(1L)).thenReturn(sampleQuestion());
        when(questionService.copyQuestion(any())).thenReturn(sampleQuestion());
        mockMvc.perform(post("/questions/1/copy").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(questionService).copyQuestion(eq(1L));
    }

    @Test
    @DisplayName("POST /questions/1/copy - 未认证返回401")
    void copy_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/questions/1/copy"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /questions/1/copy - STUDENT角色返回403")
    void copy_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/questions/1/copy").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /questions/search?... - 返回200且data.list为数组")
    void search_authorized_returns_success() throws Exception {
        when(questionService.searchQuestions(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());
        mockMvc.perform(get("/questions/search?courseId=1&type=test&difficulty=test&keyword=test&page=1&size=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
        verify(questionService).searchQuestions(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("GET /questions/search?... - 未认证返回401")
    void search_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/questions/search?courseId=1&type=test&difficulty=test&keyword=test&page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /questions/random?... - 返回200且data为数组")
    void randomSelect_authorized_returns_success() throws Exception {
        when(questionService.randomSelect(any(), any(), any(), anyInt())).thenReturn(List.of(sampleQuestion()));
        mockMvc.perform(get("/questions/random?courseId=1&type=test&difficulty=test&count=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(questionService).randomSelect(eq(1L), any(), any(), eq(1));
    }

    @Test
    @DisplayName("GET /questions/random?... - 未认证返回401")
    void randomSelect_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/questions/random?courseId=1&type=test&difficulty=test&count=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /questions/kp/1 - 返回200且data为数组")
    void getByKnowledgePoint_authorized_returns_success() throws Exception {
        when(questionService.getByKnowledgePoint(any())).thenReturn(List.of(sampleQuestion()));
        mockMvc.perform(get("/questions/kp/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(questionService).getByKnowledgePoint(eq(1L));
    }

    @Test
    @DisplayName("GET /questions/kp/1 - 未认证返回401")
    void getByKnowledgePoint_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/questions/kp/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /questions/1/stats - 返回200且data透传统计")
    void getStats_authorized_returns_success() throws Exception {
        when(questionService.getQuestionStats(any())).thenReturn(
                new QuestionStatsVO(1L, 5, null, "MEDIUM", "SINGLE", null, true, null));
        mockMvc.perform(get("/questions/1/stats").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.questionId").value(1))
                .andExpect(jsonPath("$.data.usageCount").value(5));
        verify(questionService).getQuestionStats(eq(1L));
    }

    @Test
    @DisplayName("GET /questions/1/stats - 未认证返回401")
    void getStats_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/questions/1/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /questions/error-prone?courseId=1&limit=1 - 返回200且data为数组")
    void errorProne_authorized_returns_success() throws Exception {
        when(questionService.getErrorProneQuestions(any(), anyInt())).thenReturn(List.of(sampleQuestion()));
        mockMvc.perform(get("/questions/error-prone?courseId=1&limit=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(questionService).getErrorProneQuestions(eq(1L), eq(1));
    }

    @Test
    @DisplayName("GET /questions/error-prone?courseId=1&limit=1 - 未认证返回401")
    void errorProne_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/questions/error-prone?courseId=1&limit=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /questions - TEACHER角色返回200且正确委托save")
    void createBasic_authorized_returns_success() throws Exception {
        when(questionService.save(any())).thenReturn(true);
        mockMvc.perform(post("/questions").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"题干\",\"courseId\":1,\"type\":\"SINGLE_CHOICE\"}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
        verify(questionService).save(any(Question.class));
    }

    @Test
    @DisplayName("POST /questions - 纯文本答案/数组选项被规范化成 JSON 文本后落库（B-13 ⑩）")
    void createBasic_normalizesJsonColumns() throws Exception {
        when(questionService.save(any())).thenReturn(true);
        mockMvc.perform(post("/questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"题干\",\"courseId\":1,\"type\":\"MULTIPLE_CHOICE\","
                                + "\"correctAnswers\":\"A,B\",\"options\":[\"选项1\",\"选项2\"],"
                                + "\"knowledgePoints\":[\"闭包\"]}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionService).save(captor.capture());
        Question saved = captor.getValue();
        assertEquals("[\"A\",\"B\"]", saved.getCorrectAnswers(),
                "多选简写必须拆成 JSON 数组，否则 MySQL JSON 列会判非法");
        assertEquals("[\"选项1\",\"选项2\"]", saved.getOptions(), "数组选项应序列化为 JSON 文本");
        assertEquals("[\"闭包\"]", saved.getKnowledgePoints());
    }

    @Test
    @DisplayName("POST /questions - 缺 type 返回 400 且不落库（DTO 必填校验）")
    void createBasic_missingType_returns400() throws Exception {
        mockMvc.perform(post("/questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"题干\",\"courseId\":1}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verify(questionService, never()).save(any(Question.class));
    }

    @Test
    @DisplayName("POST /questions - options 非数组返回 400（形状不对不得静默落库）")
    void createBasic_optionsNotArray_returns400() throws Exception {
        mockMvc.perform(post("/questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"题干\",\"courseId\":1,\"type\":\"SINGLE_CHOICE\",\"options\":\"A. 选项一\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verify(questionService, never()).save(any(Question.class));
    }

    @Test
    @DisplayName("PUT /questions/1 - 局部更新不采用客户端传来的 courseId（B-13 ⑩）")
    void update_keepsStoredCourseOwnership() throws Exception {
        Question existing = sampleQuestion();
        existing.setCourseId(9L);
        when(questionService.getById(1L)).thenReturn(existing);
        when(questionService.updateById(any())).thenReturn(true);

        mockMvc.perform(put("/questions/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":123,\"explanation\":\"解析\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk());

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionService).updateById(captor.capture());
        assertEquals(9L, captor.getValue().getCourseId(), "课程归属必须取库内值");
        assertEquals("解析", captor.getValue().getExplanation());
    }

    @Test
    @DisplayName("POST /questions - 未认证返回401")
    void createBasic_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/questions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /questions - STUDENT角色返回403")
    void createBasic_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/questions").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"题干\",\"courseId\":1,\"type\":\"SINGLE_CHOICE\"}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /questions/1 - TEACHER角色返回200且data透传更新题目")
    void update_authorized_returns_success() throws Exception {
        when(questionService.updateById(any())).thenReturn(true);
        when(questionService.getById(any())).thenReturn(sampleQuestion());
        mockMvc.perform(put("/questions/1").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"题干\"}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(questionService).updateById(any(Question.class));
        verify(questionService, times(2)).getById(eq(1L));
    }

    @Test
    @DisplayName("PUT /questions/1 - 未认证返回401")
    void update_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/questions/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /questions/1 - STUDENT角色返回403")
    void update_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/questions/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /questions/1 - TEACHER角色返回200且正确委托removeById")
    void delete_authorized_returns_success() throws Exception {
        when(questionService.getById(1L)).thenReturn(sampleQuestion());
        when(questionService.removeById(any(Long.class))).thenReturn(true);
        mockMvc.perform(delete("/questions/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(questionService).removeById(eq(1L));
    }

    @Test
    @DisplayName("DELETE /questions/1 - 未认证返回401")
    void delete_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/questions/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /questions/1 - STUDENT角色返回403")
    void delete_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/questions/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /questions/ai-generate - TEACHER角色返回200且data透传生成结果")
    void generateByAIBasic_authorized_returns_success() throws Exception {
        when(aiService.generateQuestion(any(), any(), any(), anyInt())).thenReturn("generated-text");
        mockMvc.perform(post("/questions/ai-generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"type\":\"SINGLE\",\"difficulty\":\"EASY\"}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.questions").value("generated-text"));
        verify(aiService).generateQuestion(eq(1L), eq("SINGLE"), eq("EASY"), eq(1));
    }

    @Test
    @DisplayName("POST /questions/ai-generate - 未认证返回401")
    void generateByAIBasic_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/questions/ai-generate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /questions/ai-generate - STUDENT角色返回403")
    void generateByAIBasic_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/questions/ai-generate").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /questions/generate-by-ai/batch - TEACHER角色返回200且data为数组")
    void generateBatchByAI_authorized_returns_success() throws Exception {
        when(questionService.generateBatchByAI(any(), any(), any(), any(), anyInt(), any())).thenReturn(List.of(sampleQuestion()));
        mockMvc.perform(post("/questions/generate-by-ai/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"type\":\"SINGLE\",\"difficulty\":\"EASY\",\"count\":1}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(questionService).generateBatchByAI(eq(1L), eq("SINGLE"), eq("EASY"), any(), eq(1), eq(1L));
    }

    @Test
    @DisplayName("POST /questions/generate-by-ai/batch - 未认证返回401")
    void generateBatchByAI_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/questions/generate-by-ai/batch").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /questions/generate-by-ai/batch - STUDENT角色返回403")
    void generateBatchByAI_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/questions/generate-by-ai/batch").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    // ==================== 角色矩阵：限制教师出题权限（治理开关） ====================

    @Test
    @DisplayName("治理开关关闭：TEACHER 出题返回 403，且不得委托生成（阴性对照：不只是状态码）")
    void generateByAIBasic_disabledForTeacher_returns403() throws Exception {
        when(aiGenerationPolicy.isTeacherQuestionGenerationEnabled()).thenReturn(false);
        mockMvc.perform(post("/questions/ai-generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"type\":\"SINGLE\",\"difficulty\":\"EASY\"}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(aiService, never()).generateQuestion(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("治理开关关闭：ADMIN 不受限，仍可出题（治理者保留能力）")
    void generateByAIBasic_disabledButAdmin_returns200() throws Exception {
        when(aiGenerationPolicy.isTeacherQuestionGenerationEnabled()).thenReturn(false);
        when(aiService.generateQuestion(any(), any(), any(), anyInt())).thenReturn("admin-text");
        mockMvc.perform(post("/questions/ai-generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"type\":\"SINGLE\",\"difficulty\":\"EASY\"}").with(withUser(9L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.questions").value("admin-text"));
    }

    @Test
    @DisplayName("治理开关关闭：批量出题入口同样 403，且不得落库（两个入口必须同口径）")
    void generateBatchByAI_disabledForTeacher_returns403() throws Exception {
        when(aiGenerationPolicy.isTeacherQuestionGenerationEnabled()).thenReturn(false);
        mockMvc.perform(post("/questions/generate-by-ai/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"type\":\"SINGLE\",\"difficulty\":\"EASY\",\"count\":1}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(questionService, never()).generateBatchByAI(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("逐人名单（V10 修订）：被点名限制的教师出题 403，且不得委托生成")
    void generateByAIBasic_userRestricted_returns403() throws Exception {
        when(aiGenerationPolicy.isUserRestricted(1L)).thenReturn(true);
        mockMvc.perform(post("/questions/ai-generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"type\":\"SINGLE\",\"difficulty\":\"EASY\"}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(aiService, never()).generateQuestion(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("逐人名单：即使被点名，ADMIN 仍不受限（治理者保留能力，判定优先级如实钉住）")
    void generateByAIBasic_restrictedAdmin_returns200() throws Exception {
        when(aiGenerationPolicy.isUserRestricted(9L)).thenReturn(true);
        when(aiService.generateQuestion(any(), any(), any(), anyInt())).thenReturn("admin-text");
        mockMvc.perform(post("/questions/ai-generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"type\":\"SINGLE\",\"difficulty\":\"EASY\"}").with(withUser(9L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions").value("admin-text"));
    }

    @Test
    @DisplayName("POST /questions/batch-import - TEACHER角色返回200且data透传导入数量")
    void batchImport_authorized_returns_success() throws Exception {
        when(questionService.batchImport(any(), any())).thenReturn(1);
        mockMvc.perform(post("/questions/batch-import").contentType(MediaType.APPLICATION_JSON).content("[]").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.imported").value(1));
        verify(questionService).batchImport(any(), eq(1L));
    }

    @Test
    @DisplayName("POST /questions/batch-import - 未认证返回401")
    void batchImport_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/questions/batch-import").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /questions/batch-import - STUDENT角色返回403")
    void batchImport_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/questions/batch-import").contentType(MediaType.APPLICATION_JSON).content("[]").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /questions - 跨教师课程创建返回403")
    void createBasic_crossTeacher_forbidden_403() throws Exception {
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权操作其他教师的课程"))
                .when(courseAccessControl).assertCanManageCourse(eq(1L), any());
        mockMvc.perform(post("/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"题干\",\"courseId\":1,\"type\":\"SINGLE_CHOICE\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
        verify(questionService, never()).save(any(Question.class));
    }

    @Test
    @DisplayName("PUT /questions/1 - 跨教师更新返回403")
    void update_crossTeacher_forbidden_403() throws Exception {
        when(questionService.getById(1L)).thenReturn(sampleQuestion());
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权操作其他教师的课程"))
                .when(courseAccessControl).assertCanManageCourse(eq(1L), any());
        mockMvc.perform(put("/questions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"越权更新\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
        verify(questionService, never()).updateById(any(Question.class));
    }

    @Test
    @DisplayName("DELETE /questions/1 - 跨教师删除返回403")
    void delete_crossTeacher_forbidden_403() throws Exception {
        when(questionService.getById(1L)).thenReturn(sampleQuestion());
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权操作其他教师的课程"))
                .when(courseAccessControl).assertCanManageCourse(eq(1L), any());
        mockMvc.perform(delete("/questions/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
        verify(questionService, never()).removeById(1L);
    }

    @Test
    @DisplayName("POST /questions/1/review - 跨教师审核返回403")
    void review_crossTeacher_forbidden_403() throws Exception {
        when(questionService.getById(1L)).thenReturn(sampleQuestion());
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权操作其他教师的课程"))
                .when(courseAccessControl).assertCanManageCourse(eq(1L), any());
        mockMvc.perform(post("/questions/1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
        verify(questionService, never()).reviewQuestion(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("POST /questions/1/copy - 跨教师复制返回403")
    void copy_crossTeacher_forbidden_403() throws Exception {
        when(questionService.getById(1L)).thenReturn(sampleQuestion());
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权操作其他教师的课程"))
                .when(courseAccessControl).assertCanManageCourse(eq(1L), any());
        mockMvc.perform(post("/questions/1/copy").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
        verify(questionService, never()).copyQuestion(1L);
    }
}
