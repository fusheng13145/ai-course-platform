package com.aicourse.controller;

import com.aicourse.entity.Course;
import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.TaskMapper;
import com.aicourse.security.SubmissionAccessControl;
import com.aicourse.service.PlagiarismCheckService;
import com.aicourse.service.SubmissionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 查重控制器对象级授权（B2）端到端回归：使用**真实** {@link SubmissionAccessControl}
 * （{@code @Import}），在 HTTP 层验证「学生读他人查重结果 → 403」已闭环，
 * 而非仅在 mock 上验证调用发生。
 */
@WebMvcTest(PlagiarismCheckController.class)
@Import(SubmissionAccessControl.class)
class PlagiarismCheckControllerAclTest extends ControllerTestBase {

    @MockitoBean
    private PlagiarismCheckService plagiarismCheckService;
    @MockitoBean
    private SubmissionService submissionService;
    @MockitoBean
    private TaskMapper taskMapper;
    @MockitoBean
    private CourseMapper courseMapper;
    // 修复 B2 残留：控制器新增选课服务依赖（教师—学生归属判定），Web 切片需显式 mock 以满足装配。
    @MockitoBean
    private com.aicourse.service.CourseEnrollmentService courseEnrollmentService;

    private PlagiarismCheck checkOf(Long id, Long submissionId) {
        PlagiarismCheck c = new PlagiarismCheck();
        c.setId(id);
        c.setSubmissionId(submissionId);
        return c;
    }

    private Submission submission(Long id, Long studentId, Long taskId) {
        Submission s = new Submission();
        s.setId(id);
        s.setStudentId(studentId);
        s.setTaskId(taskId);
        return s;
    }

    private Task taskOf(Long courseId) {
        Task t = new Task();
        t.setCourseId(courseId);
        return t;
    }

    private Course courseOf(Long id, Long teacherId) {
        Course c = new Course();
        c.setId(id);
        c.setTeacherId(teacherId);
        return c;
    }

    @Test
    @DisplayName("GET /plagiarism-checks/{id}：学生读本人提交的查重结果 -> 200")
    void getCheckResult_ownSubmission_ok() throws Exception {
        when(plagiarismCheckService.getById(1L)).thenReturn(checkOf(1L, 5L));
        when(submissionService.getById(5L)).thenReturn(submission(5L, 10L, 7L));

        mvc(get("/plagiarism-checks/1").with(withUser(10L, "STUDENT")));
    }

    @Test
    @DisplayName("GET /plagiarism-checks/{id}：学生读他人提交的查重结果 -> 403")
    void getCheckResult_otherSubmission_forbidden() throws Exception {
        when(plagiarismCheckService.getById(1L)).thenReturn(checkOf(1L, 5L));
        when(submissionService.getById(5L)).thenReturn(submission(5L, 99L, 7L));

        mvcForbidden(get("/plagiarism-checks/1").with(withUser(10L, "STUDENT")));
    }

    @Test
    @DisplayName("GET /plagiarism-checks/{id}/matches：学生读他人匹配明细 -> 403")
    void getMatches_otherSubmission_forbidden() throws Exception {
        when(plagiarismCheckService.getById(1L)).thenReturn(checkOf(1L, 5L));
        when(submissionService.getById(5L)).thenReturn(submission(5L, 99L, 7L));

        mvcForbidden(get("/plagiarism-checks/1/matches").with(withUser(10L, "STUDENT")));
    }

    @Test
    @DisplayName("GET /plagiarism-checks/submission/{id}：学生读他人提交历史 -> 403")
    void getSubmissionHistory_otherSubmission_forbidden() throws Exception {
        when(submissionService.getById(5L)).thenReturn(submission(5L, 99L, 7L));

        mvcForbidden(get("/plagiarism-checks/submission/5").with(withUser(10L, "STUDENT")));
    }

    @Test
    @DisplayName("GET /plagiarism-checks/{id}：非本课程教师 -> 403")
    void getCheckResult_otherTeacher_forbidden() throws Exception {
        when(plagiarismCheckService.getById(1L)).thenReturn(checkOf(1L, 5L));
        when(submissionService.getById(5L)).thenReturn(submission(5L, 99L, 7L));
        when(taskMapper.selectById(7L)).thenReturn(taskOf(3L));
        when(courseMapper.selectById(3L)).thenReturn(courseOf(3L, 20L));

        mvcForbidden(get("/plagiarism-checks/1").with(withUser(21L, "TEACHER")));
    }

    private void mvc(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) throws Exception {
        mockMvc.perform(req).andExpect(status().isOk());
    }

    private void mvcForbidden(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) throws Exception {
        mockMvc.perform(req).andExpect(status().isForbidden());
    }
}
