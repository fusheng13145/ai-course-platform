package com.aicourse.controller;
import com.aicourse.controller.ExamPaperController;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.Question;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.ExamPaperStatsPaperVO;
import com.aicourse.vo.ExamPaperStatsVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ExamPaperController 接口测试（升级版：真实业务断言）。
 * 对每个 endpoint 打桩 service 并断言 PagedResponse / 实体 / Map 字段透传，verify 确认路由委托。
 */
@WebMvcTest(ExamPaperController.class)
class ExamPaperControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.ExamPaperService examPaperService;
    @MockitoBean
    private com.aicourse.service.ExamQuestionService examQuestionService;
    @MockitoBean
    private com.aicourse.service.QuestionService questionService;
    @MockitoBean
    private com.aicourse.service.ExamSessionService examSessionService;
    @MockitoBean
    private com.aicourse.security.ExamPaperAccessControl examPaperAccessControl;
    @MockitoBean
    private com.aicourse.service.CourseService courseService;

    private Page<ExamPaper> examPaperPageWith(Long id) {
        ExamPaper p = new ExamPaper();
        p.setId(id);
        Page<ExamPaper> page = new Page<>();
        page.setRecords(List.of(p));
        page.setTotal(1L);
        return page;
    }

    @Test
    @DisplayName("GET /exam-papers - 分页查询试卷返回200，列表与总数透传")
    void listByParams_authorized_passes_security() throws Exception {
        when(examPaperService.page(any(), any())).thenReturn(examPaperPageWith(1L));
        mockMvc.perform(get("/exam-papers?page=1&size=1&courseId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].id").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
        verify(examPaperService).page(any(), any());
    }

    @Test
    @DisplayName("GET /exam-papers?page=1&size=1&courseId=1 - 未认证返回401")
    void listByParams_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-papers?page=1&size=1&courseId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /exam-papers/course/1 - 按课程查询试卷返回200，列表透传")
    void listByCourse_authorized_passes_security() throws Exception {
        when(examPaperService.page(any(), any())).thenReturn(examPaperPageWith(1L));
        mockMvc.perform(get("/exam-papers/course/1?page=1&size=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].id").value(1));
        verify(examPaperService).page(any(), any());
    }

    @Test
    @DisplayName("GET /exam-papers/course/1?page=1&size=1 - 未认证返回401")
    void listByCourse_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-papers/course/1?page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /exam-papers/1 - 查询试卷详情返回200，ID透传")
    void getById_authorized_passes_security() throws Exception {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        when(examPaperService.getById(eq(1L))).thenReturn(paper);
        mockMvc.perform(get("/exam-papers/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(examPaperService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /exam-papers/1 - 未认证返回401")
    void getById_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-papers/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-papers - 教师创建试卷返回200，creatorId透传")
    void create_authorized_passes_security() throws Exception {
        mockMvc.perform(post("/exam-papers").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"测试试卷\"}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.creatorId").value(1));
        verify(examPaperService).save(any());
    }

    @Test
    @DisplayName("POST /exam-papers - 未认证返回401")
    void create_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-papers").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-papers - STUDENT角色返回403")
    void create_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/exam-papers").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /exam-papers/1 - 教师更新试卷返回200，ID透传")
    void update_authorized_passes_security() throws Exception {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        when(examPaperService.getById(eq(1L))).thenReturn(paper);
        mockMvc.perform(put("/exam-papers/1").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"测试试卷\"}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(examPaperService).updateById(any());
    }

    @Test
    @DisplayName("PUT /exam-papers/1 - 未认证返回401")
    void update_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/exam-papers/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /exam-papers/1 - STUDENT角色返回403")
    void update_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/exam-papers/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /exam-papers/1 - 教师删除试卷返回200")
    void delete_authorized_passes_security() throws Exception {
        mockMvc.perform(delete("/exam-papers/1").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(examPaperService).removeById(eq(1L));
    }

    @Test
    @DisplayName("DELETE /exam-papers/1 - 未认证返回401")
    void delete_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/exam-papers/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /exam-papers/1 - STUDENT角色返回403")
    void delete_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/exam-papers/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /exam-papers/1/questions - 查询试卷题目返回200，数组透传")
    void getQuestions_authorized_passes_security() throws Exception {
        ExamQuestion eq = new ExamQuestion();
        eq.setId(5L);
        eq.setQuestionId(9L);
        when(examQuestionService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(eq));
        when(questionService.getById(eq(9L))).thenReturn(new Question());
        mockMvc.perform(get("/exam-papers/1/questions").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(5));
        verify(examQuestionService).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    @DisplayName("GET /exam-papers/1/questions - 未认证返回401")
    void getQuestions_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-papers/1/questions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /exam-papers/1/detail - 查询试卷完整详情返回200，字段透传")
    void getDetail_authorized_passes_security() throws Exception {
        com.aicourse.entity.ExamPaper p = new com.aicourse.entity.ExamPaper();
        p.setTitle("期中试卷");
        when(examPaperService.getExamPaperDetail(eq(1L)))
                .thenReturn(new com.aicourse.vo.ExamPaperDetailVO(p, java.util.Collections.emptyList(), 0));
        mockMvc.perform(get("/exam-papers/1/detail").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.paper.title").value("期中试卷"));
        verify(examPaperService).getExamPaperDetail(eq(1L));
    }

    @Test
    @DisplayName("GET /exam-papers/1/detail - 未认证返回401")
    void getDetail_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-papers/1/detail"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-papers/1/publish - 教师发布试卷返回200，ID透传")
    void publish_authorized_passes_security() throws Exception {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        when(examPaperService.getById(eq(1L))).thenReturn(paper);
        mockMvc.perform(post("/exam-papers/1/publish").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(examPaperService).publishExamPaper(eq(1L));
    }

    @Test
    @DisplayName("POST /exam-papers/1/publish - 未认证返回401")
    void publish_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-papers/1/publish"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-papers/1/publish - STUDENT角色返回403")
    void publish_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/exam-papers/1/publish").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /exam-papers/1/questions - 教师批量替换试卷题目返回200，清单透传")
    void replaceQuestions_authorized_passes_security() throws Exception {
        ExamQuestion saved = new ExamQuestion();
        saved.setId(77L);
        saved.setExamPaperId(1L);
        saved.setQuestionId(9L);
        saved.setOrderIndex(0);
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        when(examPaperService.getById(eq(1L))).thenReturn(paper);
        when(examPaperService.replaceExamQuestions(eq(1L), any()))
                .thenReturn(List.of(saved));
        mockMvc.perform(post("/exam-papers/1/questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questions\":[{\"questionId\":9,\"points\":5,\"orderIndex\":0}]}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(77))
                .andExpect(jsonPath("$.data[0].questionId").value(9));
        verify(examPaperService).replaceExamQuestions(eq(1L), any());
        verify(examPaperAccessControl).assertCanManage(any(), any());
    }

    @Test
    @DisplayName("POST /exam-papers/1/questions - 未认证返回401")
    void replaceQuestions_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-papers/1/questions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"questions\":[{\"questionId\":9}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-papers/1/questions - STUDENT角色返回403")
    void replaceQuestions_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/exam-papers/1/questions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"questions\":[{\"questionId\":9}]}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /exam-papers/auto-compose - 教师自动组卷返回200，ID透传")
    void autoCompose_authorized_passes_security() throws Exception {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        when(examPaperService.autoComposeExamPaper(any(), eq(1L))).thenReturn(paper);
        mockMvc.perform(post("/exam-papers/auto-compose").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(examPaperService).autoComposeExamPaper(any(), eq(1L));
    }

    @Test
    @DisplayName("POST /exam-papers/auto-compose - 未认证返回401")
    void autoCompose_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-papers/auto-compose").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-papers/auto-compose - STUDENT角色返回403")
    void autoCompose_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/exam-papers/auto-compose").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /exam-papers/1/copy - 教师复制试卷返回200，ID透传")
    void copy_authorized_passes_security() throws Exception {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        when(examPaperService.copyExamPaper(eq(1L), any())).thenReturn(paper);
        mockMvc.perform(post("/exam-papers/1/copy").contentType(MediaType.APPLICATION_JSON).content("{\"newTitle\":\"副本\"}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(examPaperService).copyExamPaper(eq(1L), any());
    }

    @Test
    @DisplayName("POST /exam-papers/1/copy - 未认证返回401")
    void copy_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-papers/1/copy").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-papers/1/copy - STUDENT角色返回403")
    void copy_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/exam-papers/1/copy").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /exam-papers/1/statistics - 查询试卷统计返回200，字段透传")
    void statistics_authorized_passes_security() throws Exception {
        when(examPaperService.getPaperStatistics(eq(1L))).thenReturn(
                new ExamPaperStatsVO(new ExamPaperStatsPaperVO(1L, "期中", "PUBLISHED"), 4,
                        new java.math.BigDecimal("56.00"), Map.of("SINGLE", 1), Map.of("1-5分", 1)));
        mockMvc.perform(get("/exam-papers/1/statistics").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.questionCount").value(4))
                .andExpect(jsonPath("$.data.paperInfo.title").value("期中"));
        verify(examPaperService).getPaperStatistics(eq(1L));
    }

    @Test
    @DisplayName("GET /exam-papers/1/statistics - 未认证返回401")
    void statistics_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-papers/1/statistics"))
                .andExpect(status().isUnauthorized());
    }
}
