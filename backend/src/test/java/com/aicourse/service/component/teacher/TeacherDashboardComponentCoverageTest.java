package com.aicourse.service.component.teacher;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.Question;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.AIService;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.LearningRecordService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
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
 * TeacherDashboardComponent 分支补测：驾驶舱聚合、预警分级、我的课程指标、跨课程仪表盘与 AI 缓存。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeacherDashboardComponentCoverageTest {

    @Mock private CourseService courseService;
    @Mock private CourseChapterService chapterService;
    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private TaskService taskService;
    @Mock private SubmissionService submissionService;
    @Mock private ExamPaperService examPaperService;
    @Mock private QuestionService questionService;
    @Mock private LearningRecordService learningRecordService;
    @Mock private AIService aiService;
    @Mock private TeacherCourseComponent support;
    @Mock private TeacherChapterComponent chapterComponent;

    private TeacherDashboardComponent component;

    @BeforeEach
    void setUp() {
        component = new TeacherDashboardComponent(courseService, chapterService, enrollmentService, taskService,
                submissionService, examPaperService, questionService, learningRecordService, aiService, support,
                chapterComponent);
        when(support.round2(anyDouble())).thenAnswer(inv -> {
            double v = inv.getArgument(0);
            return Math.round(v * 100.0) / 100.0;
        });
        when(support.userNameMap(any())).thenReturn(Map.of(1L, "张三", 2L, "李四"));
        when(support.tryParseJson(anyString())).thenReturn(null);
    }

    private Course course(Long id, String name) {
        Course c = new Course();
        c.setId(id); c.setName(name); c.setCode("C" + id); c.setCoverImage("cover.png");
        c.setStatus("ACTIVE"); c.setCategory("必修"); c.setTeacherId(7L);
        return c;
    }

    private CourseEnrollment enrollment(Long sid, Long cid, BigDecimal progress, BigDecimal score) {
        CourseEnrollment e = new CourseEnrollment();
        e.setStudentId(sid); e.setCourseId(cid); e.setProgress(progress); e.setFinalScore(score);
        e.setIsActive(true); e.setApprovalStatus("APPROVED");
        return e;
    }

    private Task task(Long id, Long cid, boolean published) {
        Task t = new Task();
        t.setId(id); t.setCourseId(cid); t.setIsPublished(published); t.setChapterId(100L);
        return t;
    }

    private Submission submission(BigDecimal score) {
        Submission s = new Submission();
        s.setScore(score);
        return s;
    }

    @Test
    @DisplayName("驾驶舱：课程不存在抛异常")
    void dashboardCourseMissing() {
        when(courseService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> component.getCourseTeachingDashboard(1L, 7L));
    }

    @Test
    @DisplayName("驾驶舱：完整聚合、14 天趋势、预警排序与 AI 解读")
    void dashboardAggregates() {
        Course c = course(1L, "计网");
        when(courseService.getById(1L)).thenReturn(c);
        when(enrollmentService.count(any())).thenReturn(4L);
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(
                enrollment(1L, 1L, new BigDecimal("100"), new BigDecimal("95")),
                enrollment(2L, 1L, new BigDecimal("50"), new BigDecimal("55")),
                enrollment(3L, 1L, null, null),
                enrollment(4L, 1L, new BigDecimal("10"), new BigDecimal("50"))));
        when(chapterService.count(any())).thenReturn(3L);
        when(taskService.count(any())).thenReturn(2L);
        when(examPaperService.count(any())).thenReturn(1L);
        when(questionService.count(any())).thenReturn(20L);
        when(taskService.list(any(Wrapper.class))).thenReturn(List.of(task(11L, 1L, true), task(12L, 1L, false), task(null, 1L, true)));
        when(submissionService.count(any())).thenReturn(2L);
        when(learningRecordService.dailyActiveByCourse(eq(1L), any()))
                .thenReturn(List.of(Map.of("date", java.time.LocalDate.now().minusDays(1).toString(), "active", 3)));
        when(submissionService.dailySubmissionsByTasks(anyList(), any()))
                .thenReturn(List.of(Map.of("date", java.time.LocalDate.now().minusDays(2).toString(), "subs", 5)));
        when(chapterComponent.getFullChapterTree(1L)).thenReturn(List.of(Map.of("id", 1)));
        when(aiService.generateLearningAdviceJson(anyString(), anyString()))
                .thenReturn("{\"summary\":\"整体良好\"}");
        when(support.tryParseJson(anyString())).thenReturn(Map.of("summary", "整体良好"));

        Map<String, Object> result = component.getCourseTeachingDashboard(1L, 7L);

        assertEquals("计网", result.get("courseName"));
        assertEquals(2, result.get("atRiskCount"));
        assertEquals(14, ((List<?>) result.get("trend")).size());
        Map<?, ?> learning = (Map<?, ?>) result.get("learning");
        assertEquals(40.0, learning.get("avgProgress"));
        assertEquals(33.33, learning.get("passRate"));
        assertEquals(33.33, learning.get("excellentRate"));
        assertEquals("整体良好", ((Map<?, ?>) result.get("aiInsight")).get("summary"));
        assertEquals(2, ((List<?>) result.get("atRiskStudents")).size());
        assertEquals("HIGH", ((Map<?, ?>) ((List<?>) result.get("atRiskStudents")).get(0)).get("level"));
        verify(aiService).generateLearningAdviceJson(anyString(), anyString());
    }

    @Test
    @DisplayName("驾驶舱：内部异常落 error 字段而不抛出")
    void dashboardExceptionIsContained() {
        when(courseService.getById(1L)).thenReturn(course(1L, "计网"));
        when(enrollmentService.count(any())).thenThrow(new RuntimeException("db down"));

        Map<String, Object> result = component.getCourseTeachingDashboard(1L, 7L);

        assertTrue(String.valueOf(result.get("error")).contains("db down"));
    }

    @Test
    @DisplayName("驾驶舱：成功结果命中 10 分钟缓存，不重复调 LLM")
    void dashboardSuccessCache() {
        when(courseService.getById(1L)).thenReturn(course(1L, "计网"));
        when(enrollmentService.count(any())).thenReturn(1L);
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(enrollment(1L, 1L, new BigDecimal("80"), new BigDecimal("80"))));
        when(chapterService.count(any())).thenReturn(0L);
        when(taskService.count(any())).thenReturn(0L);
        when(examPaperService.count(any())).thenReturn(0L);
        when(questionService.count(any())).thenReturn(0L);
        when(taskService.list(any(Wrapper.class))).thenReturn(List.of());
        when(submissionService.count(any())).thenReturn(0L);
        when(learningRecordService.dailyActiveByCourse(anyLong(), any())).thenReturn(List.of());
        when(chapterComponent.getFullChapterTree(anyLong())).thenReturn(List.of());
        when(aiService.generateLearningAdviceJson(anyString(), anyString()))
                .thenReturn("{\"summary\":\"缓存\"}");
        when(support.tryParseJson(anyString())).thenReturn(Map.of("summary", "缓存"));

        component.getCourseTeachingDashboard(1L, 7L);
        component.getCourseTeachingDashboard(1L, 7L);

        verify(aiService, times(1)).generateLearningAdviceJson(anyString(), anyString());
    }

    @Test
    @DisplayName("驾驶舱：AI 失败走兜底并负缓存 60 秒，不重复撞模型")
    void dashboardFailureCache() {
        when(courseService.getById(1L)).thenReturn(course(1L, "计网"));
        when(enrollmentService.count(any())).thenReturn(0L);
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of());
        when(chapterService.count(any())).thenReturn(0L);
        when(taskService.count(any())).thenReturn(0L);
        when(examPaperService.count(any())).thenReturn(0L);
        when(questionService.count(any())).thenReturn(0L);
        when(taskService.list(any(Wrapper.class))).thenReturn(List.of());
        when(submissionService.count(any())).thenReturn(0L);
        when(learningRecordService.dailyActiveByCourse(anyLong(), any())).thenReturn(List.of());
        when(chapterComponent.getFullChapterTree(anyLong())).thenReturn(List.of());
        when(aiService.generateLearningAdviceJson(anyString(), anyString())).thenThrow(new RuntimeException("402"));

        Map<String, Object> first = component.getCourseTeachingDashboard(1L, 7L);
        component.getCourseTeachingDashboard(1L, 7L);

        assertEquals("暂无可用的 AI 学情解读（模型未配置或调用失败）。",
                ((Map<?, ?>) first.get("aiInsight")).get("summary"));
        verify(aiService, times(1)).generateLearningAdviceJson(anyString(), anyString());
    }

    @Test
    @DisplayName("预警：进度/成绩空值、单维与双维风险分级")
    void atRiskBranches() {
        List<CourseEnrollment> list = List.of(
                enrollment(1L, 1L, new BigDecimal("10"), new BigDecimal("95")),
                enrollment(2L, 1L, new BigDecimal("90"), new BigDecimal("50")),
                enrollment(3L, 1L, new BigDecimal("10"), new BigDecimal("50")),
                enrollment(4L, 1L, null, null),
                enrollment(5L, 1L, new BigDecimal("90"), new BigDecimal("90")));

        List<Map<String, Object>> risk = component.computeAtRisk(list);

        assertEquals(3, risk.size());
        assertEquals("HIGH", risk.get(0).get("level"));
        assertEquals(3L, risk.get(0).get("studentId"));
        assertEquals("3", risk.get(0).get("studentName"));
        assertEquals("MEDIUM", risk.get(1).get("level"));
        assertFalse(risk.get(2).containsKey("courseName"));
    }

    @Test
    @DisplayName("我的课程：课程列表与任务/提交指标聚合；空列表返回空")
    void myCourseSummaries() {
        when(courseService.list(any(Wrapper.class))).thenReturn(List.of(course(1L, "计网"), course(2L, "Python")));
        when(enrollmentService.count(any())).thenReturn(2L);
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(
                enrollment(1L, 1L, new BigDecimal("100"), new BigDecimal("88")),
                enrollment(2L, 1L, null, null), enrollment(3L, 2L, new BigDecimal("50"), null)));
        when(taskService.list(any(Wrapper.class))).thenReturn(List.of(task(11L, 1L, true)), List.of());
        when(submissionService.count(any())).thenReturn(3L);

        List<Map<String, Object>> result = component.getMyCourseSummaries(7L);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).get("studentCount"));
        assertEquals(50.0, result.get(0).get("avgProgress"));
        assertEquals(50.0, result.get(0).get("completionRate"));
        assertEquals(3L, result.get(0).get("pendingGrading"));
        assertEquals(0L, result.get(1).get("pendingGrading"));

        when(courseService.list(any(Wrapper.class))).thenReturn(List.of());
        assertTrue(component.getMyCourseSummaries(8L).isEmpty());
    }

    @Test
    @DisplayName("教师仪表盘：有课程聚合、课程名回填、提交率与高于平台趋势")
    void teacherDashboardAggregates() {
        when(support.courseIdsOfTeacher(7L)).thenReturn(List.of(1L, 2L));
        when(enrollmentService.count(any())).thenReturn(5L);
        when(taskService.list(any(Wrapper.class))).thenReturn(List.of(task(11L, 1L, true), task(12L, 2L, true)));
        when(submissionService.count(any())).thenReturn(2L);
        when(submissionService.list(any(Wrapper.class))).thenReturn(
                List.of(submission(new BigDecimal("100")), submission(new BigDecimal("90")),
                        submission(null)),
                List.of(submission(new BigDecimal("30")), submission(new BigDecimal("40"))));
        when(courseService.list(any(Wrapper.class))).thenReturn(List.of(course(1L, "计网"), course(2L, "Python")));
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(
                enrollment(1L, 1L, new BigDecimal("10"), new BigDecimal("50")),
                enrollment(2L, 2L, new BigDecimal("90"), new BigDecimal("90"))));

        Map<String, Object> result = component.getTeacherDashboard(7L);

        assertEquals(2L, result.get("courseCount"));
        assertEquals(5L, result.get("studentCount"));
        assertEquals(2L, result.get("pendingGrading"));
        assertEquals(63.33, result.get("avgScore"));
        assertEquals(1, result.get("atRiskCount"));
        assertEquals("计网", ((Map<?, ?>) ((List<?>) result.get("atRiskStudents")).get(0)).get("courseName"));
        assertEquals(2L, ((Map<?, ?>) result.get("taskPointOverview")).get("publishedTasks"));
        assertEquals(20.0, ((Map<?, ?>) result.get("taskPointOverview")).get("submissionRate"));
        assertEquals("ABOVE", ((Map<?, ?>) result.get("platformComparison")).get("trend"));
    }

    @Test
    @DisplayName("教师仪表盘：无课程时全部归零，平台均分 0 记 ABOVE")
    void teacherDashboardEmpty() {
        when(support.courseIdsOfTeacher(7L)).thenReturn(List.of());
        when(courseService.list(any(Wrapper.class))).thenReturn(List.of());
        when(submissionService.list(any(Wrapper.class))).thenReturn(List.of());

        Map<String, Object> result = component.getTeacherDashboard(7L);

        assertEquals(0L, result.get("courseCount"));
        assertEquals(0L, result.get("studentCount"));
        assertEquals(0L, result.get("pendingGrading"));
        assertEquals(0.0, ((Map<?, ?>) result.get("platformComparison")).get("scoreGap"));
        assertEquals("ABOVE", ((Map<?, ?>) result.get("platformComparison")).get("trend"));
    }

    @Test
    @DisplayName("教师仪表盘：聚合异常时返回 error 与零值兜底")
    void teacherDashboardException() {
        when(support.courseIdsOfTeacher(7L)).thenThrow(new RuntimeException("boom"));

        Map<String, Object> result = component.getTeacherDashboard(7L);

        assertTrue(String.valueOf(result.get("error")).contains("boom"));
        assertEquals(0L, result.get("courseCount"));
        assertEquals(0L, result.get("studentCount"));
        assertEquals(0L, result.get("pendingGrading"));
        assertEquals(0D, result.get("avgScore"));
        assertTrue(((List<?>) result.get("courseCards")).isEmpty());
        assertTrue(((List<?>) result.get("atRiskStudents")).isEmpty());
    }

    @Test
    @DisplayName("教师 AI 总览：解析成功返回结构化结果")
    void teacherAIOverviewParsed() {
        when(support.courseIdsOfTeacher(7L)).thenReturn(List.of());
        when(courseService.list(any(Wrapper.class))).thenReturn(List.of());
        when(aiService.generateLearningAdviceJson(anyString(), anyString())).thenReturn("{\"summary\":\"总览\"}");
        when(support.tryParseJson(anyString())).thenReturn(Map.of("summary", "总览"));

        Map<String, Object> result = component.getTeacherAIOverview(7L);

        assertEquals("总览", result.get("summary"));
    }

    @Test
    @DisplayName("教师 AI 总览：解析失败回落原始文本，聚合异常回落固定文案")
    void teacherAIOverviewFallbacks() {
        when(support.courseIdsOfTeacher(7L)).thenReturn(List.of());
        when(courseService.list(any(Wrapper.class))).thenReturn(List.of());
        when(aiService.generateLearningAdviceJson(anyString(), anyString())).thenReturn("raw advice");
        when(support.tryParseJson(anyString())).thenReturn(null);

        Map<String, Object> raw = component.getTeacherAIOverview(7L);
        assertEquals("raw advice", raw.get("summary"));

        when(support.courseIdsOfTeacher(7L)).thenThrow(new RuntimeException("boom"));
        Map<String, Object> fallback = component.getTeacherAIOverview(7L);
        assertEquals("暂无可用的 AI 教学总览（模型未配置或调用失败）。", fallback.get("summary"));
    }
}
