package com.aicourse.application.exam;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.ExamSessionAccessControl;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.UserService;
import com.aicourse.vo.ActiveExamSessionVO;
import com.aicourse.vo.ExamPaperSessionVO;
import com.aicourse.vo.ExamSessionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 考试会话用例编排（3E-03B）。
 *
 * <p>本用例盯「动作序列」与「口径唯一」，不重复测 HTTP 形状：
 * 授权断言必须先于领域写入；交卷后必须重读一次拿最新状态；结果查询复用归属校验时
 * 已加载的对象而不是再查一遍；教师监考名单无课程时必须是空名单、不能回退成全量。</p>
 */
class ExamSessionApplicationServiceTest {

    private ExamSessionService examSessionService;
    private ExamSessionAccessControl accessControl;
    private CourseAccessControl courseAccessControl;
    private ExamPaperService examPaperService;
    private UserService userService;

    private ExamSessionApplicationService service;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        examSessionService = mock(ExamSessionService.class);
        accessControl = mock(ExamSessionAccessControl.class);
        courseAccessControl = mock(CourseAccessControl.class);
        examPaperService = mock(ExamPaperService.class);
        userService = mock(UserService.class);
        service = new ExamSessionApplicationService(examSessionService, accessControl, courseAccessControl,
                examPaperService, userService);
        auth = new UsernamePasswordAuthenticationToken(7L, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
    }

    // ------------------------------------------------ 阅卷台名单

    @Test
    @DisplayName("阅卷台：试卷不存在返回 404，未关联课程返回 400，且都不查会话")
    void paperSessions_guards() {
        when(examPaperService.getById(1L)).thenReturn(null);
        BusinessException notFound = assertThrows(BusinessException.class,
                () -> service.paperSessions(1L, auth));
        assertEquals(ApiCode.NOT_FOUND, notFound.getCode());

        ExamPaper noCourse = new ExamPaper();
        noCourse.setId(2L);
        when(examPaperService.getById(2L)).thenReturn(noCourse);
        BusinessException badRequest = assertThrows(BusinessException.class,
                () -> service.paperSessions(2L, auth));
        assertEquals(ApiCode.BAD_REQUEST, badRequest.getCode());

        verify(examSessionService, never()).list(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<ExamSession>>any());
    }

    @Test
    @DisplayName("阅卷台：学生信息一次批量取回（不逐行回查），行序沿用会话排序")
    void paperSessions_batchesStudentLookup() {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        paper.setCourseId(100L);
        when(examPaperService.getById(1L)).thenReturn(paper);

        ExamSession first = session(11L, 5L, "GRADED");
        first.setTotalScore(new BigDecimal("88.5"));
        first.setPercentage(new BigDecimal("88.5"));
        first.setSubmitTime(LocalDateTime.of(2026, 11, 24, 10, 0));
        ExamSession second = session(12L, 6L, "SUBMITTED");

        when(examSessionService.list(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<ExamSession>>any()))
                .thenReturn(List.of(first, second));

        User u5 = new User();
        u5.setId(5L);
        u5.setUsername("zhangsan");
        u5.setFullName("张三");
        u5.setStudentId("2023001");
        User u6 = new User();
        u6.setId(6L);
        u6.setUsername("lisi");
        u6.setFullName("李四");
        u6.setStudentId("2023002");
        when(userService.listByIds(any())).thenReturn(List.of(u5, u6));

        List<ExamPaperSessionVO> rows = service.paperSessions(1L, auth);

        assertEquals(2, rows.size());
        assertEquals(11L, rows.get(0).getSessionId().longValue());
        assertEquals("张三", rows.get(0).getFullName());
        assertEquals("2023001", rows.get(0).getStudentNo());
        assertEquals("2026-11-24T10:00", rows.get(0).getSubmitTime());
        assertEquals(12L, rows.get(1).getSessionId().longValue());
        assertEquals("李四", rows.get(1).getFullName());
        // 一次批量查询即可，学生数再多也不 N+1
        verify(userService).listByIds(any());
        verify(courseAccessControl).assertCanManageCourse(eq(100L), any());
    }

    @Test
    @DisplayName("阅卷台：学生记录缺失时姓名/学号回落为 null，不抛异常")
    void paperSessions_missingStudent_isNullFilled() {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        paper.setCourseId(100L);
        when(examPaperService.getById(1L)).thenReturn(paper);
        when(examSessionService.list(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<ExamSession>>any()))
                .thenReturn(List.of(session(11L, 5L, "GRADED")));
        when(userService.listByIds(any())).thenReturn(List.of());

        ExamPaperSessionVO row = service.paperSessions(1L, auth).get(0);

        assertNull(row.getUsername());
        assertNull(row.getFullName());
        assertNull(row.getStudentNo());
        assertEquals(5L, row.getStudentId().longValue());
    }

    // ------------------------------------------------ 开考

    @Test
    @DisplayName("开考：先做选课校验，再落会话；IP/UA 原样透传")
    void start_authorizesBeforeCreatingSession() {
        ExamSession created = session(21L, 7L, "IN_PROGRESS");
        when(examSessionService.startExam(1L, 7L, "1.1.1.1", "JUnit")).thenReturn(created);

        ExamSessionVO vo = service.start(1L, 7L, "1.1.1.1", "JUnit", auth);

        assertEquals(21L, vo.getId().longValue());
        InOrder order = inOrder(accessControl, examSessionService);
        order.verify(accessControl).assertExamStartable(eq(1L), any());
        order.verify(examSessionService).startExam(1L, 7L, "1.1.1.1", "JUnit");
    }

    // ------------------------------------------------ 作答链

    @Test
    @DisplayName("交卷：提交后必须重读一次，返回的是判分后的最新状态")
    void submit_rereadsAfterSubmit() {
        ExamSession submitted = session(31L, 7L, "SUBMITTED");
        ExamSession graded = session(31L, 7L, "GRADED");
        graded.setTotalScore(new BigDecimal("90"));
        when(accessControl.assertOwnedByStudent(31L, auth)).thenReturn(submitted);
        when(examSessionService.submitExam(eq(31L), ArgumentMatchers.any())).thenReturn(submitted);
        when(examSessionService.getById(31L)).thenReturn(graded);

        ExamSessionVO vo = service.submit(31L, java.util.Map.of("1", "A"), auth);

        assertEquals("GRADED", vo.getStatus());
        assertEquals(new BigDecimal("90"), vo.getTotalScore());
        verify(examSessionService).getById(31L);
    }

    @Test
    @DisplayName("自动保存：先校验会话归属，再写答案")
    void autoSave_authorizesBeforeWriting() {
        service.autoSave(31L, java.util.Map.of("1", "A"), auth);

        InOrder order = inOrder(accessControl, examSessionService);
        order.verify(accessControl).assertOwnedByStudent(eq(31L), any());
        order.verify(examSessionService).autoSaveAnswers(eq(31L), ArgumentMatchers.any());
    }

    @Test
    @DisplayName("考试结果：复用归属校验返回的会话对象，不再额外查一次库")
    void result_reusesSessionLoadedByAccessControl() {
        ExamSession loaded = session(31L, 7L, "GRADED");
        when(accessControl.assertReadable(31L, auth)).thenReturn(loaded);

        ExamSessionVO vo = service.result(31L, auth);

        assertEquals(31L, vo.getId().longValue());
        verify(examSessionService, never()).getById(anyLong());
    }

    // ------------------------------------------------ 阅卷 / 监考

    @Test
    @DisplayName("人工评分：评语为 null 时归一成空串（与库内既有口径一致）")
    void manualGrade_nullCommentBecomesEmptyString() {
        service.manualGrade(31L, 9L, new BigDecimal("5"), null, auth);

        verify(accessControl).assertManageable(eq(31L), any());
        verify(examSessionService).manualGrade(31L, 9L, new BigDecimal("5"), "");
    }

    @Test
    @DisplayName("强制交卷：先校验可管理，再收卷")
    void forceSubmit_authorizesBeforeForcing() {
        when(examSessionService.forceSubmit(31L)).thenReturn(session(31L, 7L, "FORCE_SUBMITTED"));

        ExamSessionVO vo = service.forceSubmit(31L, auth);

        assertEquals("FORCE_SUBMITTED", vo.getStatus());
        InOrder order = inOrder(accessControl, examSessionService);
        order.verify(accessControl).assertManageable(eq(31L), any());
        order.verify(examSessionService).forceSubmit(31L);
    }

    @Test
    @DisplayName("监考列表：教师只留自己课程的会话")
    void activeExams_teacherSeesOwnCoursesOnly() {
        when(examSessionService.getActiveExams()).thenReturn(List.of(
                active(1L, 100L), active(2L, 200L), active(3L, null)));
        when(courseAccessControl.readableCourseIds(auth)).thenReturn(List.of(100L));

        List<ActiveExamSessionVO> rows = service.activeExams(auth);

        assertEquals(1, rows.size());
        assertEquals(100L, rows.get(0).getCourseId().longValue());
    }

    @Test
    @DisplayName("监考列表：教师无任何课程时为空名单，绝不回退全量")
    void activeExams_teacherWithoutCourses_emptyRatherThanAll() {
        when(examSessionService.getActiveExams()).thenReturn(List.of(active(1L, 100L), active(2L, 200L)));
        when(courseAccessControl.readableCourseIds(auth)).thenReturn(List.of());

        assertTrue(service.activeExams(auth).isEmpty());
    }

    @Test
    @DisplayName("监考列表：管理员（readableCourseIds=null）全量透传，含无课程归属的会话")
    void activeExams_adminSeesAll() {
        when(examSessionService.getActiveExams()).thenReturn(List.of(
                active(1L, 100L), active(3L, null)));
        when(courseAccessControl.readableCourseIds(auth)).thenReturn(null);

        assertEquals(2, service.activeExams(auth).size());
    }

    // ------------------------------------------------ helpers

    private ExamSession session(Long id, Long studentId, String status) {
        ExamSession s = new ExamSession();
        s.setId(id);
        s.setStudentId(studentId);
        s.setStatus(status);
        s.setAttemptNumber(1);
        return s;
    }

    private ActiveExamSessionVO active(Long id, Long courseId) {
        ActiveExamSessionVO vo = new ActiveExamSessionVO();
        vo.setId(id);
        vo.setCourseId(courseId);
        return vo;
    }
}
