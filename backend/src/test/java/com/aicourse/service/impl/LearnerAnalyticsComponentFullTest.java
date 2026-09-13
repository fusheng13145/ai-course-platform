package com.aicourse.service.impl;
import com.aicourse.service.component.analytics.LearnerAnalyticsComponent;

import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.LearningRecord;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.LearningRecordService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LearnerAnalyticsComponent 覆盖率补充测试，补充原 LearnerAnalyticsComponentTest 未覆盖分支：
 *  - getUserDashboard 待办>0（courseIds 非空、任务数>已提交）
 *  - getUserDashboard 无选课（courseIds 为空，不调用 taskService.count）
 *  - getUserDashboard 异常分支
 *  - sumLearningDuration 成功路径（getBaseMapper().selectObjs 返回真实 SUM）与空结果兜底
 *  - getLearningTrends 多日期聚合 + null createdAt 过滤
 *  - getLearningHeatmap 起止日期各分支 + 全部热力等级(0..5) + null createdAt 过滤
 */
@ExtendWith(MockitoExtension.class)
class LearnerAnalyticsComponentFullTest {

    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private LearningRecordService learningRecordService;
    @Mock private TaskService taskService;
    @Mock private SubmissionService submissionService;

    @SuppressWarnings("unchecked")
    @Mock private BaseMapper<LearningRecord> learningRecordMapper;

    @InjectMocks private LearnerAnalyticsComponent learnerAnalytics;

    @BeforeEach
    void setUp() {
        lenient().when(learningRecordService.getBaseMapper()).thenReturn(learningRecordMapper);
    }

    private CourseEnrollment enrollment(long courseId, BigDecimal progress, BigDecimal finalScore) {
        CourseEnrollment e = new CourseEnrollment();
        e.setStudentId(100L);
        e.setCourseId(courseId);
        e.setProgress(progress);
        e.setFinalScore(finalScore);
        return e;
    }

    private LearningRecord record(LocalDateTime createdAt, int duration) {
        LearningRecord r = new LearningRecord();
        r.setUserId(100L);
        r.setCreatedAt(createdAt);
        r.setDuration(duration);
        return r;
    }

    @Test
    @DisplayName("getUserDashboard - 待办任务数=已发布任务数-已提交数")
    void testGetUserDashboard_PendingTasksPositive() {
        when(enrollmentService.count(any(LambdaQueryWrapper.class))).thenReturn(2L);
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                enrollment(1L, new BigDecimal("50"), new BigDecimal("80")),
                enrollment(2L, new BigDecimal("100"), new BigDecimal("90"))
        ));
        when(learningRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                record(LocalDateTime.now(), 30),
                record(LocalDateTime.now().minusDays(1), 60)
        ));
        when(learningRecordMapper.selectObjs(any())).thenReturn(List.of(120));
        when(taskService.count(any(LambdaQueryWrapper.class))).thenReturn(3L);
        when(submissionService.count(any(LambdaQueryWrapper.class))).thenReturn(1L);

        Map<String, Object> data = learnerAnalytics.getUserDashboard(100L);

        assertEquals(2L, data.get("enrolledCourses"));
        assertEquals(75.0, (Double) data.get("averageProgress"), 0.001);
        assertEquals(120, data.get("totalStudyTime")); // sumLearningDuration 成功路径
        assertEquals(85.0, (Double) data.get("averageScore"), 0.001);
        assertEquals(2, data.get("streakDays"));
        // 3 个待办 - 1 已提交 = 2
        assertEquals(2L, data.get("pendingTasks"));
        assertEquals(2L, data.get("todoCount"));
        assertFalse(data.containsKey("error"));
    }

    @Test
    @DisplayName("getUserDashboard - 无选课时课程ID为空，不调用任务计数")
    void testGetUserDashboard_NoCourses() {
        when(enrollmentService.count(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(learningRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(learningRecordMapper.selectObjs(any())).thenReturn(Collections.emptyList());
        when(submissionService.count(any(LambdaQueryWrapper.class))).thenReturn(0L);

        Map<String, Object> data = learnerAnalytics.getUserDashboard(100L);

        assertEquals(0L, data.get("enrolledCourses"));
        assertEquals(0, data.get("streakDays"));
        assertEquals(0L, data.get("pendingTasks"));
        // 确认未进入课程ID非空分支
        verify(taskService, never()).count(any());
    }

    @Test
    @DisplayName("getUserDashboard - 协作者抛错时返回 error 并填充默认值")
    void testGetUserDashboard_Exception() {
        when(enrollmentService.count(any(LambdaQueryWrapper.class))).thenThrow(new RuntimeException("boom"));

        Map<String, Object> data = learnerAnalytics.getUserDashboard(100L);

        assertTrue(data.containsKey("error"));
        assertEquals(0L, data.get("enrolledCourses"));
        assertEquals(0L, data.get("courseCount"));
        assertEquals(0.0, (Double) data.get("averageScore"), 0.001);
        assertEquals(0, data.get("avgScore"));
        assertEquals(0, data.get("streakDays"));
        assertEquals(0L, data.get("pendingTasks"));
        assertEquals(0L, data.get("todoCount"));
    }

    @Test
    @DisplayName("getLearningTrends - 按日期聚合且过滤 null createdAt")
    void testGetLearningTrends_GroupsByDate() {
        LocalDateTime today = LocalDateTime.now();
        LearningRecord r1 = record(today.withHour(10), 30);
        LearningRecord r2 = record(today.withHour(14), 60);
        LearningRecord r3 = record(today.minusDays(1).withHour(9), 15);
        LearningRecord rNull = new LearningRecord(); rNull.setDuration(5); // createdAt null -> 过滤
        when(learningRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(r1, r2, r3, rNull));

        List<Map<String, Object>> trends = learnerAnalytics.getLearningTrends(100L);

        assertEquals(2, trends.size());
        assertEquals(today.toLocalDate().toString(), trends.get(0).get("date"));
        assertEquals(90, trends.get(0).get("duration"));
        assertEquals(today.minusDays(1).toLocalDate().toString(), trends.get(1).get("date"));
        assertEquals(15, trends.get(1).get("duration"));
    }

    @Test
    @DisplayName("getLearningHeatmap - 起止日期均为 null 时不加范围条件")
    void testGetLearningHeatmap_NoDateRange() {
        LearningRecord r = record(LocalDateTime.now(), 25);
        when(learningRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(r));

        List<Map<String, Object>> heatmap = learnerAnalytics.getLearningHeatmap(100L, null, null);

        assertEquals(1, heatmap.size());
        assertEquals(25, heatmap.get(0).get("duration"));
        assertEquals(1, heatmap.get(0).get("level")); // <30 -> 1
    }

    @Test
    @DisplayName("getLearningHeatmap - 起止日期均指定时添加 ge/le 条件")
    void testGetLearningHeatmap_WithStartAndEnd() {
        LocalDate start = LocalDate.now().minusDays(2);
        LocalDate end = LocalDate.now();
        when(learningRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(record(start.atStartOfDay(), 45)));

        List<Map<String, Object>> heatmap = learnerAnalytics.getLearningHeatmap(100L, start, end);
        assertEquals(1, heatmap.size());
        assertEquals(2, heatmap.get(0).get("level")); // 30-60 -> 2
        verify(learningRecordService).list(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("getLearningHeatmap - 仅起始日期（ge 添加，le 不添加）")
    void testGetLearningHeatmap_StartOnly() {
        LocalDate start = LocalDate.now().minusDays(1);
        when(learningRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(record(LocalDateTime.now(), 65)));
        List<Map<String, Object>> heatmap = learnerAnalytics.getLearningHeatmap(100L, start, null);
        assertEquals(1, heatmap.size());
        assertEquals(3, heatmap.get(0).get("level")); // 60-120 -> 3
    }

    @Test
    @DisplayName("getLearningHeatmap - 仅结束日期（ge 不添加，le 添加）")
    void testGetLearningHeatmap_EndOnly() {
        LocalDate end = LocalDate.now();
        LearningRecord r = record(LocalDateTime.now(), 120);
        when(learningRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(r));
        List<Map<String, Object>> heatmap = learnerAnalytics.getLearningHeatmap(100L, null, end);
        assertEquals(1, heatmap.size());
        assertEquals(4, heatmap.get(0).get("level")); // 120 -> 4
        verify(learningRecordService).list(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("getLearningHeatmap - 覆盖全部热力等级(0..5)与 null createdAt 过滤")
    void testGetLearningHeatmap_AllHeatLevels() {
        LocalDate base = LocalDate.now();
        // 各记录使用不同日期，避免被生产代码按天聚合为单条（从而能分别验证 0..5 级）
        LearningRecord lvl0 = record(base.atTime(8, 0), 0);       // 0 -> 0
        LearningRecord lvl1 = record(base.minusDays(1).atTime(8, 0), 25);   // <30 -> 1
        LearningRecord lvl2 = record(base.minusDays(2).atTime(8, 0), 45);   // 30-60 -> 2
        LearningRecord lvl3 = record(base.minusDays(3).atTime(8, 0), 65);   // 60-120 -> 3
        LearningRecord lvl4 = record(base.minusDays(4).atTime(8, 0), 120);  // 120-180 -> 4
        LearningRecord lvl5 = record(base.minusDays(5).atTime(8, 0), 200);  // >180 -> 5
        LearningRecord lvlNull = new LearningRecord(); lvlNull.setDuration(999); // createdAt null -> 过滤
        when(learningRecordService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(lvl0, lvl1, lvl2, lvl3, lvl4, lvl5, lvlNull));

        List<Map<String, Object>> heatmap = learnerAnalytics.getLearningHeatmap(100L, null, null);

        int[] levels = heatmap.stream().mapToInt(m -> (Integer) m.get("level")).sorted().toArray();
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5}, levels);
        assertEquals(6, heatmap.size()); // 6 条不同日期各成一组（lvlNull 被过滤）
    }

    @Test
    @DisplayName("getUserDashboard - sumLearningDuration 聚合异常时降级为0")
    void testGetUserDashboard_SumDurationExceptionFallsBack() {
        when(enrollmentService.count(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(enrollmentService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(enrollment(1L, new BigDecimal("50"), new BigDecimal("80"))));
        when(learningRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(learningRecordMapper.selectObjs(any())).thenThrow(new RuntimeException("db error"));
        when(taskService.count(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(submissionService.count(any(LambdaQueryWrapper.class))).thenReturn(0L);

        Map<String, Object> data = learnerAnalytics.getUserDashboard(100L);
        assertEquals(0, data.get("totalStudyTime")); // 异常降级为 0
        assertFalse(data.containsKey("error"));        // 不影响整体仪表盘
    }
}
