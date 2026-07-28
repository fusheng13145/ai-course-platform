package com.aicourse.service.impl;

import com.aicourse.entity.*;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LearnerAnalyticsComponent 单元测试（由 AnalyticsServiceImpl 迁移而来）
 * 验证学生视角统计逻辑与原实现保持一致。
 */
@ExtendWith(MockitoExtension.class)
class LearnerAnalyticsComponentTest {

    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private LearningRecordService learningRecordService;
    @Mock private TaskService taskService;
    @Mock private SubmissionService submissionService;

    @InjectMocks private LearnerAnalyticsComponent learnerAnalytics;

    private CourseEnrollment testEnrollment;

    @BeforeEach
    void setUp() {
        testEnrollment = new CourseEnrollment();
        testEnrollment.setId(1L);
        testEnrollment.setStudentId(100L);
        testEnrollment.setCourseId(1L);
        testEnrollment.setFinalScore(new BigDecimal("85"));
        testEnrollment.setProgress(new BigDecimal("60.00"));
    }

    @Test
    @DisplayName("用户仪表盘 - 用户特定统计")
    void testGetUserDashboard_UserSpecificStats() {
        when(enrollmentService.count(any(LambdaQueryWrapper.class))).thenReturn(3L);
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(testEnrollment));

        LearningRecord record = new LearningRecord();
        record.setUserId(100L);
        record.setDuration(120);
        record.setCreatedAt(LocalDateTime.now());

        when(learningRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(record));
        when(taskService.count(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(submissionService.count(any())).thenReturn(0L);

        Map<String, Object> userDashboard = learnerAnalytics.getUserDashboard(100L);

        assertNotNull(userDashboard);
        assertNotNull(userDashboard.get("enrolledCourses"));
        assertNotNull(userDashboard.get("averageProgress"));
        assertNotNull(userDashboard.get("totalStudyTime"));
        assertNotNull(userDashboard.get("averageScore"));
        assertNotNull(userDashboard.get("streakDays"));
        assertNotNull(userDashboard.get("pendingTasks"));
    }
}
