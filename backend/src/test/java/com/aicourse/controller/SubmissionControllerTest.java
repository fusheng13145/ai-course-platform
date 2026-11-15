package com.aicourse.controller;
import com.aicourse.controller.SubmissionController;
import com.aicourse.entity.Submission;
import com.aicourse.service.SubmissionService;
import com.aicourse.security.SubmissionAccessControl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SubmissionController 接口测试（升级版：真实业务断言）。
 * 对每个 endpoint 打桩 service 并断言实体 / 分页字段透传，verify 确认路由委托。
 * @PreAuthorize 限定的 grade/return/aiGrade/checkPlagiarism 均包含越权 403 测试。
 */
@WebMvcTest(SubmissionController.class)
class SubmissionControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.SubmissionService submissionService;

    @MockitoBean
    private com.aicourse.security.SubmissionAccessControl accessControl;

    @MockitoBean
    private com.aicourse.service.PlagiarismCheckService plagiarismCheckService;

    private Page<Submission> submissionPageWith(Long id) {
        Submission s = new Submission();
        s.setId(id);
        Page<Submission> p = new Page<>();
        p.setRecords(List.of(s));
        p.setTotal(1L);
        return p;
    }

    @Test
    @DisplayName("POST /submissions - 学生提交作业返回200，收口到 submitAssignment 且 studentId 取自登录态")
    void submit_authorized_passes_security() throws Exception {
        Submission saved = new Submission();
        saved.setId(10L);
        saved.setStudentId(1L);
        saved.setTaskId(1L);
        when(submissionService.submitAssignment(eq(1L), eq(1L), any(), any())).thenReturn(saved);
        mockMvc.perform(post("/submissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":1,\"content\":\"作答\"}").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.studentId").value(1));
        // D4：提交必须走服务层统一收口（含存在性/发布/逾期/次数校验），而非裸 save
        verify(submissionService).submitAssignment(eq(1L), eq(1L), any(), any());
        verify(submissionService, never()).save(any());
    }

    @Test
    @DisplayName("POST /submissions - 任务不存在时按业务异常拒绝，不落库（D4 回归）")
    void submit_taskNotFound_rejected() throws Exception {
        when(submissionService.submitAssignment(any(), any(), any(), any()))
                .thenThrow(new com.aicourse.exception.BusinessException("作业任务不存在"));
        mockMvc.perform(post("/submissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":999}").with(withUser(1L, "STUDENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("作业任务不存在"));
        verify(submissionService, never()).save(any());
    }

    @Test
    @DisplayName("POST /submissions - 未发布作业被服务层拒绝（D4 回归）")
    void submit_unpublished_rejected() throws Exception {
        when(submissionService.submitAssignment(any(), any(), any(), any()))
                .thenThrow(new com.aicourse.exception.BusinessException("该作业尚未发布"));
        mockMvc.perform(post("/submissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":1}").with(withUser(1L, "STUDENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该作业尚未发布"));
        verify(submissionService, never()).save(any());
    }

    @Test
    @DisplayName("POST /submissions - 超过最大提交次数被拒绝（D4 幂等/次数防护回归）")
    void submit_exceedMaxSubmissions_rejected() throws Exception {
        when(submissionService.submitAssignment(any(), any(), any(), any()))
                .thenThrow(new com.aicourse.exception.BusinessException("已达到最大提交次数限制（3次），无法再次提交"));
        mockMvc.perform(post("/submissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":1}").with(withUser(1L, "STUDENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("最大提交次数")));
        verify(submissionService, never()).save(any());
    }

    @Test
    @DisplayName("POST /submissions - 逾期按既有策略迟交放行并回传 isLate（D4 逾期策略回归）")
    void submit_late_allowedWithPenalty() throws Exception {
        Submission late = new Submission();
        late.setId(20L);
        late.setStudentId(1L);
        late.setTaskId(1L);
        late.setIsLate(true);
        late.setLatePenalty(new java.math.BigDecimal("10"));
        when(submissionService.submitAssignment(eq(1L), eq(1L), any(), any())).thenReturn(late);
        mockMvc.perform(post("/submissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":1,\"content\":\"迟交\"}").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isLate").value(true))
                .andExpect(jsonPath("$.data.latePenalty").value(10));
    }

    @Test
    @DisplayName("POST /submissions - 未认证返回401")
    void submit_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/submissions").contentType(MediaType.APPLICATION_JSON).content("{\"taskId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /submissions - 查询我的提交返回200，列表透传")
    void mySubmissions_authorized_passes_security() throws Exception {
        when(submissionService.page(any(), any())).thenReturn(submissionPageWith(1L));
        mockMvc.perform(get("/submissions?page=1&size=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].id").value(1));
        verify(submissionService).page(any(), any());
    }

    @Test
    @DisplayName("GET /submissions?page=1&size=1 - 未认证返回401")
    void mySubmissions_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/submissions?page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /submissions/1 - 查询提交详情返回200，ID透传")
    void getById_authorized_passes_security() throws Exception {
        Submission s = new Submission();
        s.setId(1L);
        when(submissionService.getById(eq(1L))).thenReturn(s);
        mockMvc.perform(get("/submissions/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(submissionService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /submissions/1 - 未认证返回401")
    void getById_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/submissions/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /submissions/task/1 - 按任务查询提交返回200，列表透传")
    void list_authorized_passes_security() throws Exception {
        when(submissionService.page(any(), any())).thenReturn(submissionPageWith(1L));
        mockMvc.perform(get("/submissions/task/1?page=1&size=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].id").value(1));
        verify(submissionService).page(any(), any());
    }

    @Test
    @DisplayName("GET /submissions/task/1?page=1&size=1 - 未认证返回401")
    void list_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/submissions/task/1?page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /submissions/1/grade - 教师评分委托服务层 gradeSubmission（P1-2 收口）")
    void grade_authorized_passes_security() throws Exception {
        Submission s = new Submission();
        s.setId(1L);
        s.setStatus("GRADED");
        when(submissionService.gradeSubmission(eq(1L), any(), any(), any())).thenReturn(s);
        mockMvc.perform(post("/submissions/1/grade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":95,\"feedback\":\"good\"}").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("GRADED"));
        // 2026-11-14 P1-2：控制器不再直写实体，统一委托服务层（逾期扣分/百分比/reviewCount 单一口径）
        verify(submissionService).gradeSubmission(eq(1L), any(), any(), any());
        verify(submissionService, never()).updateById(any());
    }

    @Test
    @DisplayName("POST /submissions/1/grade - 未认证返回401")
    void grade_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/submissions/1/grade").contentType(MediaType.APPLICATION_JSON).content("{\"score\":95}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /submissions/1/grade - STUDENT角色返回403")
    void grade_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/submissions/1/grade").contentType(MediaType.APPLICATION_JSON).content("{\"score\":95}")
                        .with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /submissions/1/resubmit - 学生重新提交返回200，ID透传")
    void resubmit_authorized_passes_security() throws Exception {
        Submission s = new Submission();
        s.setId(1L);
        when(submissionService.resubmit(eq(1L), any(), any())).thenReturn(s);
        mockMvc.perform(post("/submissions/1/resubmit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"重交\"}").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(submissionService).resubmit(eq(1L), any(), any());
    }

    @Test
    @DisplayName("POST /submissions/1/resubmit - 未认证返回401")
    void resubmit_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/submissions/1/resubmit").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /submissions/1/return - 教师退回作业返回200，ID透传")
    void returnSubmission_authorized_passes_security() throws Exception {
        Submission s = new Submission();
        s.setId(1L);
        when(submissionService.returnSubmission(eq(1L), any())).thenReturn(s);
        mockMvc.perform(post("/submissions/1/return").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"不合格\"}").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(submissionService).returnSubmission(eq(1L), any());
    }

    @Test
    @DisplayName("POST /submissions/1/return - 未认证返回401")
    void returnSubmission_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/submissions/1/return").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /submissions/1/return - STUDENT角色返回403")
    void returnSubmission_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/submissions/1/return").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /submissions/1/ai-grade - 教师AI评分返回200，ID透传")
    void aiGrade_authorized_passes_security() throws Exception {
        Submission s = new Submission();
        s.setId(1L);
        when(submissionService.aiGradeSubmission(eq(1L))).thenReturn(s);
        mockMvc.perform(post("/submissions/1/ai-grade").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(submissionService).aiGradeSubmission(eq(1L));
    }

    @Test
    @DisplayName("POST /submissions/1/ai-grade - 未认证返回401")
    void aiGrade_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/submissions/1/ai-grade"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /submissions/1/ai-grade - STUDENT角色返回403")
    void aiGrade_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/submissions/1/ai-grade").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /submissions/1/plagiarism - 查询查重提示返回200，数据透传")
    void getPlagiarismResult_authorized_passes_security() throws Exception {
        mockMvc.perform(get("/submissions/1/plagiarism").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("查重")));
        verify(accessControl).assertReadable(eq(1L), any());
    }

    @Test
    @DisplayName("GET /submissions/1/plagiarism - 未认证返回401")
    void getPlagiarismResult_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/submissions/1/plagiarism"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /submissions/1/check-plagiarism - 教师触发查重返回200，数据透传")
    void checkPlagiarism_authorized_passes_security() throws Exception {
        when(submissionService.triggerPlagiarismCheck(eq(1L))).thenReturn(new com.aicourse.entity.PlagiarismCheck());
        mockMvc.perform(post("/submissions/1/check-plagiarism").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
        verify(submissionService).triggerPlagiarismCheck(eq(1L));
    }

    @Test
    @DisplayName("POST /submissions/1/check-plagiarism - 未认证返回401")
    void checkPlagiarism_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/submissions/1/check-plagiarism"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /submissions/1/check-plagiarism - STUDENT角色返回403")
    void checkPlagiarism_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/submissions/1/check-plagiarism").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /submissions/1/view - 记录查看返回200")
    void recordView_authorized_passes_security() throws Exception {
        mockMvc.perform(post("/submissions/1/view").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(submissionService).incrementViewCount(eq(1L));
    }

    @Test
    @DisplayName("POST /submissions/1/view - 未认证返回401")
    void recordView_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/submissions/1/view"))
                .andExpect(status().isUnauthorized());
    }
}
