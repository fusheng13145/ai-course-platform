package com.aicourse.controller;

import com.aicourse.entity.Course;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.TaskMapper;
import com.aicourse.security.SubmissionAccessControl;
import com.aicourse.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 提交控制器安全测试：在 HTTP 层验证 IDOR / 水平越权已闭环。
 * 复用 ControllerTestBase 的安全装配（withUser 注入 Long principal）。
 */
@WebMvcTest(SubmissionController.class)
@Import(SubmissionAccessControl.class)
class SubmissionControllerSecurityTest extends ControllerTestBase {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SubmissionService submissionService;
    @MockitoBean
    private TaskMapper taskMapper;
    @MockitoBean
    private CourseMapper courseMapper;
    @MockitoBean
    private com.aicourse.service.PlagiarismCheckService plagiarismCheckService;

    private Submission submission(Long id, Long studentId, Long taskId) {
        Submission s = new Submission();
        s.setId(id);
        s.setStudentId(studentId);
        s.setTaskId(taskId);
        return s;
    }

    @Test
    void studentReadsOwn_returns200() throws Exception {
        when(submissionService.getById(1L)).thenReturn(submission(1L, 10L, 5L));
        mvc.perform(get("/submissions/1").with(withUser(10L, "STUDENT")))
                .andExpect(status().isOk());
    }

    @Test
    void studentReadsOthers_returns403() throws Exception {
        when(submissionService.getById(1L)).thenReturn(submission(1L, 99L, 5L));
        mvc.perform(get("/submissions/1").with(withUser(10L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherReadsOwnCourse_returns200() throws Exception {
        when(submissionService.getById(1L)).thenReturn(submission(1L, 99L, 5L));
        when(taskMapper.selectById(5L)).thenReturn(taskOf(7L));
        when(courseMapper.selectById(7L)).thenReturn(courseOf(7L, 20L));
        mvc.perform(get("/submissions/1").with(withUser(20L, "TEACHER")))
                .andExpect(status().isOk());
    }

    @Test
    void teacherReadsOtherCourse_returns403() throws Exception {
        when(submissionService.getById(1L)).thenReturn(submission(1L, 99L, 5L));
        when(taskMapper.selectById(5L)).thenReturn(taskOf(7L));
        when(courseMapper.selectById(7L)).thenReturn(courseOf(7L, 20L));
        mvc.perform(get("/submissions/1").with(withUser(21L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherGradesOwnCourse_returns200() throws Exception {
        when(submissionService.getById(1L)).thenReturn(submission(1L, 99L, 5L));
        when(taskMapper.selectById(5L)).thenReturn(taskOf(7L));
        when(courseMapper.selectById(7L)).thenReturn(courseOf(7L, 20L));
        mvc.perform(post("/submissions/1/grade")
                        .with(withUser(20L, "TEACHER"))
                        .contentType("application/json")
                        .content("{\"score\":90,\"feedback\":\"good\"}"))
                .andExpect(status().isOk());
        verify(submissionService).updateById(any(Submission.class));
    }

    @Test
    void teacherGradesOtherCourse_returns403() throws Exception {
        when(submissionService.getById(1L)).thenReturn(submission(1L, 99L, 5L));
        when(taskMapper.selectById(5L)).thenReturn(taskOf(7L));
        when(courseMapper.selectById(7L)).thenReturn(courseOf(7L, 20L));
        mvc.perform(post("/submissions/1/grade")
                        .with(withUser(21L, "TEACHER"))
                        .contentType("application/json")
                        .content("{\"score\":90,\"feedback\":\"good\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentGrades_returns403() throws Exception {
        mvc.perform(post("/submissions/1/grade")
                        .with(withUser(10L, "STUDENT"))
                        .contentType("application/json")
                        .content("{\"score\":90,\"feedback\":\"good\"}"))
                .andExpect(status().isForbidden());
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
}
