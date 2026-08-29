package com.aicourse.service.impl;
import com.aicourse.service.component.*;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.TaskService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CourseAnalyticsComponent 覆盖率补充测试，补充原 CourseAnalyticsComponentTest 未覆盖分支：
 *  - getGradeDistributionHistogram 空列表（totalWithScore=0 → passRate/excellentRate=0）
 *  - getCourseRanking 超过 20 门课时的 limit(20) 截断与字段完整性
 *  - getExamStatistics 会话为空分支（else 返回全零）
 *  - getExamStatistics 比例(percentage)为 null 且试卷 totalScore 为 null 的兜底分支
 */
@ExtendWith(MockitoExtension.class)
class CourseAnalyticsComponentFullTest {

    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private CourseService courseService;
    @Mock private TaskService taskService;
    @Mock private ExamPaperService examPaperService;
    @Mock private ExamSessionService examSessionService;

    @InjectMocks private CourseAnalyticsComponent courseAnalytics;

    private Course course(long id) {
        Course c = new Course();
        c.setId(id);
        c.setName("课程" + id);
        c.setCode("C" + id);
        c.setCategory("技术");
        c.setDifficulty("MEDIUM");
        c.setTeacherId(10L);
        c.setIsActive(true);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    @Test
    @DisplayName("getGradeDistributionHistogram - 空选课列表时各项为零")
    void testHistogram_EmptyList() {
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        Map<String, Object> histogram = courseAnalytics.getGradeDistributionHistogram(1L);

        assertEquals(0, histogram.get("total"));
        assertEquals(0.0, (Double) histogram.get("average"), 0.001);
        // 空列表时 DoubleSummaryStatistics 的 max/min 为 ±Infinity，经 Math.round 得 Long 极值，除以 100 后约为 ±9.22e16（生产真实行为）
        assertEquals(-9.223372036854776E16, (Double) histogram.get("maxScore"), 1.0);
        assertEquals(9.223372036854776E16, (Double) histogram.get("minScore"), 1.0);
        assertEquals(0.0, (Double) histogram.get("passRate"), 0.001);
        assertEquals(0.0, (Double) histogram.get("excellentRate"), 0.001);
        @SuppressWarnings("unchecked")
        Map<String, Long> dist = (Map<String, Long>) histogram.get("distribution");
        assertTrue(dist.isEmpty());
    }

    @Test
    @DisplayName("getCourseRanking - 超过20门课时截断为前20并按选课数降序")
    void testCourseRanking_Limit20() {
        List<Course> courses = new ArrayList<>();
        for (long i = 1; i <= 25; i++) courses.add(course(i));
        when(courseService.list(any(LambdaQueryWrapper.class))).thenReturn(courses);
        when(enrollmentService.count(any(LambdaQueryWrapper.class))).thenReturn(5L);

        List<Map<String, Object>> ranking = courseAnalytics.getCourseRanking();

        assertEquals(20, ranking.size());
        Map<String, Object> first = ranking.get(0);
        assertNotNull(first.get("courseId"));
        assertNotNull(first.get("courseName"));
        assertNotNull(first.get("courseCode"));
        assertNotNull(first.get("teacherId"));
        assertNotNull(first.get("enrollmentCount"));
        assertNotNull(first.get("category"));
        assertNotNull(first.get("difficulty"));
    }

    @Test
    @DisplayName("getCourseRanking - 无课程返回空")
    void testCourseRanking_Empty() {
        when(courseService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertTrue(courseAnalytics.getCourseRanking().isEmpty());
    }

    @Test
    @DisplayName("getExamStatistics - 会话为空时返回全零与空分布")
    void testExamStatistics_EmptySessions() {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        paper.setTitle("期末");
        paper.setTotalScore(new BigDecimal("100"));
        paper.setQuestionCount(10);
        paper.setDuration(60);
        when(examPaperService.getById(1L)).thenReturn(paper);
        when(examSessionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        Map<String, Object> stats = courseAnalytics.getExamStatistics(1L);

        assertEquals(0, stats.get("totalParticipants"));
        assertEquals(0, stats.get("averageScore"));
        assertEquals(0, stats.get("maxScore"));
        assertEquals(0, stats.get("minScore"));
        assertEquals(0.0, ((Number) stats.get("passRate")).doubleValue(), 0.001);
        assertEquals(0.0, ((Number) stats.get("excellentRate")).doubleValue(), 0.001);
        @SuppressWarnings("unchecked")
        Map<String, Long> dist = (Map<String, Long>) stats.get("scoreDistribution");
        assertTrue(dist.isEmpty());
        assertEquals(0, stats.get("avgTimeSpentMinutes"));
    }

    @Test
    @DisplayName("getExamStatistics - 比例(percentage)为null且试卷totalScore为null时使用兜底计算")
    void testExamStatistics_PercentageNullAndTotalScoreNull() {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        paper.setTitle("期末");
        paper.setTotalScore(null); // 兜底 100
        paper.setQuestionCount(10);
        paper.setDuration(60);
        when(examPaperService.getById(1L)).thenReturn(paper);

        ExamSession s = new ExamSession();
        s.setTotalScore(new BigDecimal("90")); // 90/100 = 90% -> 优秀
        s.setPercentage(null);                 // 走兜底百分比
        s.setTimeSpent(1200);
        when(examSessionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));

        Map<String, Object> stats = courseAnalytics.getExamStatistics(1L);

        assertEquals(1, stats.get("totalParticipants"));
        assertEquals(90.0, (Double) stats.get("averageScore"), 0.001);
        assertEquals(90.0, (Double) stats.get("maxScore"), 0.001);
        assertEquals(90.0, (Double) stats.get("minScore"), 0.001);
        assertEquals(100.0, (Double) stats.get("passRate"), 0.001);
        assertEquals(100.0, (Double) stats.get("excellentRate"), 0.001);
        assertEquals(1200L, stats.get("avgTimeSpentMinutes"));
        @SuppressWarnings("unchecked")
        Map<String, Long> dist = (Map<String, Long>) stats.get("scoreDistribution");
        assertEquals(1L, dist.get("90-100（优秀）"));
    }

    @Test
    @DisplayName("getExamStatistics - 试卷不存在返回错误")
    void testExamStatistics_NotFound() {
        when(examPaperService.getById(999L)).thenReturn(null);
        Map<String, Object> stats = courseAnalytics.getExamStatistics(999L);
        assertEquals("试卷不存在", stats.get("error"));
    }
}
