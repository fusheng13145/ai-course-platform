package com.aicourse.service.impl;
import com.aicourse.service.component.analytics.AdminAnalyticsComponent;
import com.aicourse.service.component.analytics.CourseAnalyticsComponent;
import com.aicourse.service.component.analytics.LearnerAnalyticsComponent;
import com.aicourse.service.component.analytics.TeachingAnalyticsComponent;

import com.aicourse.entity.*;
import com.aicourse.mapper.TaskCompletionMapper;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AnalyticsServiceImpl（门面）覆盖率补充测试。
 * <p>getDashboard Redis 缓存分支已随逻辑迁移至 {@link AdminAnalyticsComponent}（见
 * {@code AdminAnalyticsComponentFullTest}）；本文件补充门面保留方法（课程级学情）的
 * 降级分支与异常路径，以及关键委派契约。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceImplBoostTest {

    @Mock private AdminAnalyticsComponent adminAnalyticsComponent;
    @Mock private CourseAnalyticsComponent courseAnalyticsComponent;
    @Mock private LearnerAnalyticsComponent learnerAnalyticsComponent;
    @Mock private TeachingAnalyticsComponent teachingAnalyticsComponent;

    @Mock private UserService userService;
    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private TaskService taskService;
    @Mock private TaskCompletionMapper taskCompletionMapper;
    @Mock private SubmissionService submissionService;
    @Mock private ExamSessionService examSessionService;
    @Mock private ExamPaperService examPaperService;
    @Mock private CourseChapterService courseChapterService;

    private AnalyticsServiceImpl analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsServiceImpl(
                adminAnalyticsComponent, courseAnalyticsComponent, learnerAnalyticsComponent, teachingAnalyticsComponent,
                userService, enrollmentService, taskService, taskCompletionMapper,
                submissionService, examSessionService, examPaperService, courseChapterService);
    }

    private CourseEnrollment enrollment(Long courseId, Long studentId, BigDecimal progress, BigDecimal finalScore) {
        CourseEnrollment e = new CourseEnrollment();
        e.setCourseId(courseId);
        e.setStudentId(studentId);
        e.setProgress(progress);
        e.setFinalScore(finalScore);
        e.setIsActive(true);
        return e;
    }

    @Test
    @DisplayName("getExamStatistics 委托 CourseAnalyticsComponent")
    void delegate_getExamStatistics() {
        Map<String, Object> expected = Map.of("totalParticipants", 3);
        when(courseAnalyticsComponent.getExamStatistics(5L)).thenReturn(expected);
        assertSame(expected, analyticsService.getExamStatistics(5L));
        verify(courseAnalyticsComponent).getExamStatistics(5L);
    }

    @Test
    @DisplayName("getUserDashboard / getTeacherDashboard 分别委托 Learner / Teaching 组件")
    void delegate_dashboards() {
        Map<String, Object> learner = Map.of("streakDays", 3);
        Map<String, Object> teacher = Map.of("courseCount", 2);
        when(learnerAnalyticsComponent.getUserDashboard(7L)).thenReturn(learner);
        when(teachingAnalyticsComponent.getTeacherDashboard(1L)).thenReturn(teacher);

        assertSame(learner, analyticsService.getUserDashboard(7L));
        assertSame(teacher, analyticsService.getTeacherDashboard(1L));
        verify(learnerAnalyticsComponent).getUserDashboard(7L);
        verify(teachingAnalyticsComponent).getTeacherDashboard(1L);
    }

    @Test
    @DisplayName("getStudentCourseProgressList - 聚合异常后返回空列表（不抛异常）")
    void progressList_exceptionDegrades() {
        when(enrollmentService.list(any(Wrapper.class))).thenThrow(new RuntimeException("db down"));
        List<Map<String, Object>> result = analyticsService.getStudentCourseProgressList(1L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getStudentCourseDetail - 考试会话所属试卷与课程不匹配时被跳过(continue)")
    void studentDetail_skipCrossCourseExam() {
        // 学生 10 有一场考试，其试卷属于课程 2（当前查询课程 1）-> 考试列表应为空
        when(userService.getById(10L)).thenReturn(user(10L, "甲"));
        when(enrollmentService.getOne(any(Wrapper.class))).thenReturn(enrollment(1L, 10L, new BigDecimal("50"), new BigDecimal("80")));
        when(courseChapterService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(taskService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());

        ExamSession session = new ExamSession();
        session.setId(1L);
        session.setStudentId(10L);
        session.setExamPaperId(99L);
        session.setPercentage(new BigDecimal("85"));
        when(examSessionService.list(any(Wrapper.class))).thenReturn(Collections.singletonList(session));
        ExamPaper paper = new ExamPaper();
        paper.setId(99L);
        paper.setCourseId(2L); // 属于课程 2，不属于查询的课程 1
        when(examPaperService.listByIds(anyCollection())).thenReturn(Collections.singletonList(paper));

        Map<String, Object> result = analyticsService.getStudentCourseDetail(1L, 10L);
        @SuppressWarnings("unchecked")
        List<?> exams = (List<?>) result.get("exams");
        assertEquals(0, exams.size());
    }

    @Test
    @DisplayName("getStudentCourseDetail - 聚合异常后返回部分结果（仅学生基础信息）")
    void studentDetail_exceptionPartialResult() {
        when(userService.getById(10L)).thenReturn(user(10L, "甲"));
        when(enrollmentService.getOne(any(Wrapper.class))).thenThrow(new RuntimeException("enroll down"));

        Map<String, Object> result = analyticsService.getStudentCourseDetail(1L, 10L);
        assertEquals(10L, result.get("studentId"));
        assertEquals("甲", result.get("studentName"));
    }

    private User user(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        return u;
    }
}
