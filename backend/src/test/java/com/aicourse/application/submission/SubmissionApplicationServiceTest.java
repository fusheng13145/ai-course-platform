package com.aicourse.application.submission;

import com.aicourse.common.ApiCode;
import com.aicourse.dto.AssignmentSubmitDTO;
import com.aicourse.dto.GradeDTO;
import com.aicourse.dto.SubmissionCreateDTO;
import com.aicourse.dto.SubmissionResubmitDTO;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.SubmissionAccessControl;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.UserService;
import com.aicourse.vo.UnsubmittedStudentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 作业提交与评分用例编排（3E-02）。
 *
 * <p>本用例盯的是「动作序列」而不是「HTTP 形状」：授权断言必须先于领域写入发生；
 * 提交后必须触发一次异步 AI 预批改；两条提交入口（{@code /submissions} 与
 * {@code /student/assignments/{id}/submit}）必须走同一段序列——这正是改造前双轨分叉、
 * 学生入口漏预批改的回归点。</p>
 */
class SubmissionApplicationServiceTest {

    private SubmissionService submissionService;
    private SubmissionAccessControl accessControl;
    private CourseAccessControl courseAccessControl;
    private TaskService taskService;
    private CourseEnrollmentService enrollmentService;
    private UserService userService;
    private NotificationService notificationService;

    private SubmissionApplicationService service;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        submissionService = mock(SubmissionService.class);
        accessControl = mock(SubmissionAccessControl.class);
        courseAccessControl = mock(CourseAccessControl.class);
        taskService = mock(TaskService.class);
        enrollmentService = mock(CourseEnrollmentService.class);
        userService = mock(UserService.class);
        notificationService = mock(NotificationService.class);
        // 同步执行器：让「异步预批改」在测试里变成可断言的确定性调用
        Executor direct = Runnable::run;
        service = new SubmissionApplicationService(submissionService, accessControl, courseAccessControl,
                taskService, enrollmentService, userService, notificationService, direct);
        auth = new UsernamePasswordAuthenticationToken(7L, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    // ------------------------------------------------ 提交：动作序列与双轨合一

    @Test
    @DisplayName("提交：先做对象级授权，再写库，最后触发一次异步预批改")
    void submit_runsAuthorizeThenWriteThenAiPreGrade() {
        SubmissionCreateDTO body = createBody(9L, "答案", List.of("u1"));
        Submission saved = submissionWithId(77L);
        when(submissionService.submitAssignment(9L, 7L, "答案", List.of("u1"))).thenReturn(saved);

        Submission result = service.submit(body, auth);

        assertEquals(77L, result.getId());
        verify(accessControl).assertCanSubmitTask(9L, auth);
        verify(submissionService, org.mockito.Mockito.times(1)).aiGradeSubmission(77L);
    }

    @Test
    @DisplayName("提交：授权失败时不得触达写库与预批改")
    void submit_authorizationFailure_stopsBeforeWrite() {
        SubmissionCreateDTO body = createBody(9L, "答案", List.of());
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权提交"))
                .when(accessControl).assertCanSubmitTask(9L, auth);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.submit(body, auth));

        assertEquals(ApiCode.FORBIDDEN, ex.getCode());
        verify(submissionService, never()).submitAssignment(anyLong(), anyLong(), any(), anyList());
        verify(submissionService, never()).aiGradeSubmission(anyLong());
    }

    @Test
    @DisplayName("提交：预批改抛异常不影响提交结果（副作用与主流程解耦）")
    void submit_aiPreGradeFailure_isSwallowed() {
        SubmissionCreateDTO body = createBody(9L, "答案", List.of());
        Submission saved = submissionWithId(88L);
        when(submissionService.submitAssignment(9L, 7L, "答案", List.of())).thenReturn(saved);
        when(submissionService.aiGradeSubmission(88L)).thenThrow(new IllegalStateException("模型超时"));

        Submission result = service.submit(body, auth);

        assertEquals(88L, result.getId(), "预批改失败不得让提交抛异常");
        verify(submissionService).aiGradeSubmission(88L);
    }

    @Test
    @DisplayName("学生端口提交：与 /submissions 同一段序列（含预批改，修复双轨遗漏）")
    void submitForAssignment_sharesSamePipeline() {
        AssignmentSubmitDTO body = new AssignmentSubmitDTO();
        body.setContent("学生端答案");
        body.setFileUrls(List.of("f1"));
        Submission saved = submissionWithId(99L);
        when(submissionService.submitAssignment(5L, 7L, "学生端答案", List.of("f1"))).thenReturn(saved);

        Submission result = service.submitForAssignment(5L, body, auth);

        assertEquals(99L, result.getId());
        verify(accessControl).assertCanSubmitTask(5L, auth);
        verify(submissionService).aiGradeSubmission(99L);
    }

    @Test
    @DisplayName("草稿：同样先做对象级授权，写库用任务真实 id")
    void saveDraft_assertsAndUsesTaskId() {
        SubmissionCreateDTO body = createBody(9L, "草稿", List.of());
        Task task = taskWith(9L, 3L, "期中作业", LocalDateTime.now().plusDays(3));
        when(accessControl.assertCanSubmitTask(9L, auth)).thenReturn(task);
        Submission draft = submissionWithId(11L);
        when(submissionService.saveDraft(9L, 7L, "草稿", List.of())).thenReturn(draft);

        Submission result = service.saveDraft(body, auth);

        assertEquals(11L, result.getId());
        verify(accessControl).assertCanSubmitTask(9L, auth);
        verify(submissionService, never()).aiGradeSubmission(anyLong());
    }

    // ------------------------------------------------ 评分/退回/AI评分/重交

    @Test
    @DisplayName("评分：管理权限断言在前，评分人取登录身份")
    void grade_usesPrincipalAsGrader() {
        GradeDTO grade = new GradeDTO();
        grade.setScore(new BigDecimal("88"));
        grade.setFeedback("不错");
        Submission graded = submissionWithId(12L);
        when(submissionService.gradeSubmission(12L, new BigDecimal("88"), "不错", 7L)).thenReturn(graded);

        Submission result = service.grade(12L, grade, auth);

        assertEquals(12L, result.getId());
        verify(accessControl).assertManageable(12L, auth);
    }

    @Test
    @DisplayName("退回：管理权限断言在前")
    void returnSubmission_assertsManageable() {
        Submission returned = submissionWithId(13L);
        when(submissionService.returnSubmission(13L, "重做")).thenReturn(returned);

        Submission result = service.returnSubmission(13L, "重做", auth);

        assertEquals(13L, result.getId());
        verify(accessControl).assertManageable(13L, auth);
    }

    @Test
    @DisplayName("AI 评分：管理权限断言在前")
    void aiGrade_assertsManageable() {
        Submission graded = submissionWithId(14L);
        when(submissionService.aiGradeSubmission(14L)).thenReturn(graded);

        Submission result = service.aiGrade(14L, auth);

        assertEquals(14L, result.getId());
        verify(accessControl).assertManageable(14L, auth);
    }

    @Test
    @DisplayName("重新提交：读权限断言（学生本人或任教教师）")
    void resubmit_assertsReadable() {
        SubmissionResubmitDTO body = new SubmissionResubmitDTO();
        body.setContent("改后");
        body.setFileUrls(List.of());
        Submission resubmitted = submissionWithId(15L);
        when(submissionService.resubmit(15L, "改后", List.of())).thenReturn(resubmitted);

        Submission result = service.resubmit(15L, body, auth);

        assertEquals(15L, result.getId());
        verify(accessControl).assertReadable(15L, auth);
    }

    // ------------------------------------------------ 未提交名单与催交

    @Test
    @DisplayName("未提交名单：在册学生减去已有非草稿提交，按账号排序")
    void unsubmittedStudents_filtersAndSorts() {
        Task task = taskWith(20L, 3L, "作业一", null);
        when(taskService.getById(20L)).thenReturn(task);
        when(enrollmentService.list(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<CourseEnrollment>>any())).thenReturn(List.of(
                enrollment(101L),
                enrollment(102L)));
        Submission submitted = new Submission();
        submitted.setTaskId(20L);
        submitted.setStudentId(101L);
        when(submissionService.list(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<Submission>>any())).thenReturn(List.of(submitted));
        when(userService.listByIds(any())).thenReturn(List.of(user(102L, "zhao"), user(105L, "null-guard")));

        List<UnsubmittedStudentVO> rows = service.unsubmittedStudents(20L, auth);

        verify(courseAccessControl).assertCanManageCourse(3L, auth);
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(r -> r.getStudentNo() != null || r.getUsername() != null));
        assertEquals("null-guard", rows.get(0).getUsername(), "按账号升序");
    }

    @Test
    @DisplayName("催交：没有未提交学生时不发通知，返回 0")
    void remindUnsubmitted_noStudents_sendsNothing() {
        Task task = taskWith(20L, 3L, "作业一", null);
        when(taskService.getById(20L)).thenReturn(task);
        when(enrollmentService.list(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<CourseEnrollment>>any())).thenReturn(List.of());
        when(submissionService.list(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<Submission>>any())).thenReturn(List.of());

        SubmissionApplicationService.ReminderOutcome outcome = service.remindUnsubmitted(20L, auth);

        assertEquals(0, outcome.count());
        assertEquals("没有未提交的学生", outcome.message());
        verify(notificationService, never()).batchSendNotifications(any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("催交：有未提交学生时按 dueDate 组装文案并批量投递")
    void remindUnsubmitted_sendsBatchNotification() {
        Task task = taskWith(20L, 3L, "作业一", LocalDateTime.of(2026, 12, 1, 10, 0));
        when(taskService.getById(20L)).thenReturn(task);
        when(enrollmentService.list(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<CourseEnrollment>>any())).thenReturn(List.of(enrollment(101L)));
        when(submissionService.list(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<Submission>>any())).thenReturn(List.of());
        when(userService.listByIds(any())).thenReturn(List.of(user(101L, "zhao")));

        SubmissionApplicationService.ReminderOutcome outcome = service.remindUnsubmitted(20L, auth);

        assertEquals(1, outcome.count());
        assertTrue(outcome.message().contains("1 名学生"));
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(notificationService).batchSendNotifications(eq(List.of(101L)), eq("作业催交：作业一"),
                content.capture(), eq("SYSTEM"), eq("TASK_REMIND"), eq("MEDIUM"), eq("/assignment/20"), isNull());
        assertTrue(content.getValue().contains("2026-12-01 10:00"), "文案须带截止时间且去掉 ISO 的 T");
    }

    @Test
    @DisplayName("未提交名单：任务不存在时 404，不去查名单")
    void unsubmittedStudents_taskMissing_404() {
        when(taskService.getById(404L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.unsubmittedStudents(404L, auth));

        assertEquals(ApiCode.NOT_FOUND, ex.getCode());
        verify(enrollmentService, never()).list(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<CourseEnrollment>>any());
    }

    @Test
    @DisplayName("未提交名单：任务未关联课程时 400")
    void unsubmittedStudents_taskWithoutCourse_400() {
        Task task = new Task();
        task.setId(21L);
        when(taskService.getById(21L)).thenReturn(task);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.unsubmittedStudents(21L, auth));

        assertEquals(ApiCode.BAD_REQUEST, ex.getCode());
    }

    // ------------------------------------------------ helpers

    private static SubmissionCreateDTO createBody(Long taskId, String content, List<String> fileUrls) {
        SubmissionCreateDTO dto = new SubmissionCreateDTO();
        dto.setTaskId(taskId);
        dto.setContent(content);
        dto.setFileUrls(fileUrls);
        return dto;
    }

    private static Submission submissionWithId(Long id) {
        Submission submission = new Submission();
        submission.setId(id);
        return submission;
    }

    private static Task taskWith(Long id, Long courseId, String title, LocalDateTime dueDate) {
        Task task = new Task();
        task.setId(id);
        task.setCourseId(courseId);
        task.setTitle(title);
        task.setDueDate(dueDate);
        return task;
    }

    private static CourseEnrollment enrollment(Long studentId) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudentId(studentId);
        enrollment.setIsActive(true);
        return enrollment;
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setFullName("姓名-" + username);
        user.setStudentId("SN-" + id);
        return user;
    }
}
