package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.LearningRecord;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.mapper.TaskCompletionMapper;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.LearningRecordService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.SignInService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.VideoLearningRecordService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.service.component.student.StudentDashboardComponent;
import com.aicourse.service.component.student.StudentLearningCenterComponent;
import com.aicourse.service.component.student.StudentTaskComponent;
import com.aicourse.service.component.student.StudentTaskCompletionComponent;
import com.aicourse.service.component.student.StudentVisualizationComponent;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 学生端学情可视化聚合单元测试（D7）。
 * 覆盖 getVisualizationData 的：时长趋势按日聚合 / 课程薄弱率雷达 / 活跃热力图 / 数据源异常兜底。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentLearningVisualizationTest {

    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private CourseService courseService;
    @Mock private CourseChapterService courseChapterService;
    @Mock private TaskService taskService;
    @Mock private SubmissionService submissionService;
    @Mock private TaskCompletionMapper taskCompletionMapper;
    @Mock private NotificationService notificationService;
    @Mock private SignInService signInService;
    @Mock private ExamPaperService examPaperService;
    @Mock private ExamSessionService examSessionService;
    @Mock private AnalyticsService analyticsService;
    @Mock private LearningRecordService learningRecordService;
    @Mock private QuestionService questionService;
    @Mock private WrongQuestionService wrongQuestionService;
    @Mock private VideoLearningRecordService videoLearningRecordService;

    private StudentLearningServiceImpl service;

    @BeforeEach
    void setUp() {
        // 原兼容构造器已移除：内联其组件组装逻辑，再走主构造器（5 个组件）
        StudentTaskCompletionComponent taskCompletion =
                new StudentTaskCompletionComponent(taskService, taskCompletionMapper, enrollmentService);
        service = new StudentLearningServiceImpl(
                new StudentDashboardComponent(enrollmentService, courseService, taskService, submissionService,
                        examPaperService, examSessionService, signInService, notificationService,
                        learningRecordService, analyticsService),
                new StudentLearningCenterComponent(courseService, courseChapterService, taskService,
                        submissionService, taskCompletionMapper, taskCompletion),
                new StudentTaskComponent(taskService, videoLearningRecordService, questionService,
                        wrongQuestionService, taskCompletion),
                new StudentVisualizationComponent(learningRecordService, wrongQuestionService, courseService),
                taskCompletion);
    }

    private LearningRecord record(Long courseId, int duration, LocalDateTime at) {
        LearningRecord r = new LearningRecord();
        r.setUserId(9L);
        r.setCourseId(courseId);
        r.setDuration(duration);
        r.setCreatedAt(at);
        return r;
    }

    private WrongQuestion wrong(Long courseId, boolean mastered) {
        WrongQuestion w = new WrongQuestion();
        w.setStudentId(9L);
        w.setCourseId(courseId);
        w.setMastered(mastered);
        return w;
    }

    @Test
    @DisplayName("时长趋势-按日聚合且近14天全量返回")
    void durationTrend_aggregatesByDay() {
        LocalDate today = LocalDate.now();
        when(learningRecordService.list(any(Wrapper.class))).thenReturn(List.of(
                record(1L, 600, today.minusDays(1).atTime(10, 0)),
                record(2L, 300, today.minusDays(1).atTime(15, 0)),
                record(1L, 120, today.minusDays(3).atTime(9, 0))));
        when(wrongQuestionService.list(any(Wrapper.class))).thenReturn(List.of());
        when(courseService.getById(any())).thenReturn(null);

        Map<String, Object> data = service.getVisualizationData(9L);

        List<?> trend = (List<?>) data.get("durationTrend");
        assertEquals(14, trend.size());
        // 昨天（index 12）= 600 + 300 = 900 秒
        @SuppressWarnings("unchecked")
        Map<String, Object> yesterday = (Map<String, Object>) trend.get(12);
        assertEquals(900L, yesterday.get("seconds"));
        // 前天（index 13）= 0
        @SuppressWarnings("unchecked")
        Map<String, Object> todayEntry = (Map<String, Object>) trend.get(13);
        assertEquals(0L, todayEntry.get("seconds"));
    }

    @Test
    @DisplayName("雷达-按课程聚合未掌握率")
    void radarData_weakRatePerCourse() {
        when(learningRecordService.list(any(Wrapper.class))).thenReturn(List.of());
        when(wrongQuestionService.list(any(Wrapper.class))).thenReturn(List.of(
                wrong(1L, false),
                wrong(1L, false),
                wrong(1L, true),
                wrong(2L, false))); // 课程2：1/1 = 100%
        // N+1 修复后 buildRadarData 改用 listByIds 批量取课程名（见 StudentVisualizationComponent），
        // 故此处桩需覆盖 listByIds 而非 getById
        when(courseService.listByIds(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Collection<Long> ids = inv.getArgument(0);
            return ids.stream().map(id -> {
                Course c = new Course();
                c.setId(id);
                c.setName(id.equals(1L) ? "计算机网络" : "课程" + id);
                return c;
            }).collect(Collectors.toList());
        });

        Map<String, Object> data = service.getVisualizationData(9L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> radar = (List<Map<String, Object>>) data.get("radarData");
        assertEquals(2, radar.size());
        // 按薄弱率降序（百分比口径）：课程2 在前（100），课程1 = 2/3 ≈ 66.67
        assertEquals("课程2", radar.get(0).get("dimension"));
        assertEquals(100.0, (double) radar.get(0).get("weakRate"), 0.001);
        assertEquals(66.67, (double) radar.get(1).get("weakRate"), 0.01);
    }

    @Test
    @DisplayName("热力图-课程×日期聚合时长")
    void heatmapData_courseByDate() {
        LocalDate today = LocalDate.now();
        when(learningRecordService.list(any(Wrapper.class))).thenReturn(List.of(
                record(1L, 500, today.atTime(10, 0)),
                record(1L, 100, today.atTime(14, 0)))); // 同课程同日累加 600
        when(wrongQuestionService.list(any(Wrapper.class))).thenReturn(List.of());

        Map<String, Object> data = service.getVisualizationData(9L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> heat = (List<Map<String, Object>>) data.get("heatmapData");
        assertEquals(1, heat.size());
        assertEquals(1L, heat.get(0).get("courseId"));
        assertEquals(today.toString(), heat.get(0).get("date"));
        assertEquals(600L, heat.get(0).get("seconds"));
    }

    @Test
    @DisplayName("数据源异常-各子项降级为空列表，整体不抛异常")
    void visualization_dataSourceError_degrades() {
        when(learningRecordService.list(any(Wrapper.class))).thenThrow(new RuntimeException("db down"));
        when(wrongQuestionService.list(any(Wrapper.class))).thenThrow(new RuntimeException("db down"));

        Map<String, Object> data = service.getVisualizationData(9L);

        assertNotNull(data.get("durationTrend"));
        assertNotNull(data.get("radarData"));
        assertNotNull(data.get("heatmapData"));
        assertTrue(((List<?>) data.get("durationTrend")).isEmpty());
    }
}
