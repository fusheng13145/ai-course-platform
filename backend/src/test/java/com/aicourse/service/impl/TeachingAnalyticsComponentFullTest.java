package com.aicourse.service.impl;
import com.aicourse.service.component.*;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Task;
import com.aicourse.entity.User;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TeachingAnalyticsComponent 覆盖率补充测试，补充原 TeachingAnalyticsComponentTest 未覆盖分支：
 *  - getTeacherDashboard 无课程路径（courseIds 为空）与异常分支
 *  - exportReport 课程不存在(早返回) 与 完整成功路径(分数分布/提交率/学生排名)
 *  - getAtRiskStudents：school/department/major/class/course 各维度、进度&成绩双低(HIGH)/单低(MEDIUM)、
 *    用户画像缺失回退、courseId 过滤、院系/专业/班级 profile 过滤、details>200 截断
 */
@ExtendWith(MockitoExtension.class)
class TeachingAnalyticsComponentFullTest {

    @Mock private CourseService courseService;
    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private TaskService taskService;
    @Mock private SubmissionService submissionService;
    @Mock private UserService userService;
    @Mock private CourseAnalyticsComponent courseAnalytics;

    @InjectMocks private TeachingAnalyticsComponent teachingAnalytics;

    private CourseEnrollment enr(long courseId, long studentId, BigDecimal progress, BigDecimal score) {
        CourseEnrollment e = new CourseEnrollment();
        e.setCourseId(courseId);
        e.setStudentId(studentId);
        e.setProgress(progress);
        e.setFinalScore(score);
        return e;
    }

    private User user(long id, String department, String major, String className) {
        User u = new User();
        u.setId(id);
        u.setFullName("用户" + id);
        u.setDepartment(department);
        u.setMajor(major);
        u.setClassName(className);
        return u;
    }

    // ===== getTeacherDashboard：无课程路径 =====
    @Test
    @DisplayName("getTeacherDashboard - 教师无课程时各项为零且趋势7点")
    void testGetTeacherDashboard_NoCourses() {
        when(courseService.count(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(courseService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        Map<String, Object> data = teachingAnalytics.getTeacherDashboard(9L);

        assertEquals(0L, data.get("courseCount"));
        assertEquals(0L, data.get("totalCourses"));
        assertEquals(0L, data.get("studentCount"));
        assertEquals(0L, data.get("totalStudents"));
        assertEquals(0L, data.get("pendingGradeCount"));
        assertEquals(0L, data.get("pendingGrading"));
        assertEquals(0.0, (Double) data.get("avgScore"), 0.001);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dist = (List<Map<String, Object>>) data.get("courseDistribution");
        assertTrue(dist.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trend = (List<Map<String, Object>>) data.get("trendData");
        assertEquals(7, trend.size());
        verify(taskService, never()).count(any());
        verify(submissionService, never()).count(any());
    }

    // ===== getTeacherDashboard：异常分支 =====
    @Test
    @DisplayName("getTeacherDashboard - 协作者抛错返回 error 并填充默认值")
    void testGetTeacherDashboard_Exception() {
        when(courseService.count(any(LambdaQueryWrapper.class))).thenThrow(new RuntimeException("boom"));

        Map<String, Object> data = teachingAnalytics.getTeacherDashboard(9L);

        assertTrue(data.containsKey("error"));
        assertEquals(0L, data.get("courseCount"));
        assertEquals(0L, data.get("studentCount"));
        assertEquals(0L, data.get("pendingGradeCount"));
        assertEquals(0.0, (Double) data.get("avgScore"), 0.001);
        @SuppressWarnings("unchecked")
        List<Object> dist = (List<Object>) data.get("courseDistribution");
        assertTrue(dist.isEmpty());
    }

    // ===== exportReport：课程不存在早返回 =====
    @Test
    @DisplayName("exportReport - 课程不存在时直接返回 getCourseAnalysis 结果")
    void testExportReport_CourseNotFound() {
        Map<String, Object> base = new HashMap<>();
        base.put("courseInfo", null);
        when(courseAnalytics.getCourseAnalysis(99L)).thenReturn(base);
        when(courseService.getById(99L)).thenReturn(null);

        Map<String, Object> report = teachingAnalytics.exportReport(99L);

        assertSame(base, report);
        assertFalse(report.containsKey("scoreDistribution"));
    }

    // ===== exportReport：完整成功路径 =====
    @Test
    @DisplayName("exportReport - 完整聚合分数分布/提交率/学生排名")
    void testExportReport_Success() {
        when(courseAnalytics.getCourseAnalysis(1L)).thenReturn(new HashMap<>());
        Course course = new Course(); course.setId(1L); course.setName("Java");
        when(courseService.getById(1L)).thenReturn(course);

        CourseEnrollment e1 = enr(1L, 1L, new BigDecimal("100"), new BigDecimal("95"));
        CourseEnrollment e2 = enr(1L, 2L, new BigDecimal("50"), new BigDecimal("55"));
        CourseEnrollment e3 = enr(1L, 3L, new BigDecimal("80"), null);
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(e1, e2, e3));

        Task t1 = new Task(); t1.setId(11L);
        Task t2 = new Task(); t2.setId(12L);
        when(taskService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(t1, t2));
        when(submissionService.count(any(LambdaQueryWrapper.class))).thenReturn(3L);

        Map<String, Object> report = teachingAnalytics.exportReport(1L);

        @SuppressWarnings("unchecked")
        Map<String, Long> scoreDist = (Map<String, Long>) report.get("scoreDistribution");
        assertEquals(1L, scoreDist.get("excellent"));
        assertEquals(0L, scoreDist.get("good"));
        assertEquals(0L, scoreDist.get("average"));
        assertEquals(1L, scoreDist.get("fail"));
        // expectedSubmissions = 2 * 3 = 6; totalSubmissions = 3 -> 50
        assertEquals(50.0, (Double) report.get("submissionRate"), 0.001);
        assertEquals(2, report.get("totalTasks"));
        assertEquals(3L, report.get("totalSubmissions"));
        // e1=100, e2=50, e3=80（progress 非 null）-> (100+50+80)/3 = 76.6667，实现 Math.round 两位 -> 76.67
        assertEquals(76.67, (Double) report.get("averageProgress"), 0.001);
        assertEquals(1L, report.get("completedStudents"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ranking = (List<Map<String, Object>>) report.get("studentRanking");
        assertEquals(2, ranking.size()); // 仅 e1,e2 有成绩
        assertEquals("学情分析报告", report.get("reportType"));
        assertNotNull(report.get("exportTime"));
    }

    // ===== getAtRiskStudents：school 维度（默认）=====
    @Test
    @DisplayName("getAtRiskStudents - groupBy 为 null 时按全校维度聚合，无分组")
    void testGetAtRiskStudents_School() {
        List<CourseEnrollment> enrollments = Arrays.asList(
                enr(1L, 1L, new BigDecimal("30"), null),       // 进度低 -> 掉队
                enr(1L, 2L, new BigDecimal("80"), new BigDecimal("55")), // 成绩低 -> 掉队
                enr(2L, 3L, new BigDecimal("90"), new BigDecimal("90"))  // 正常
        );
        when(enrollmentService.list()).thenReturn(enrollments);
        when(userService.listByIds(any())).thenReturn(Arrays.asList(
                user(1L, "计算机", "软件", "1班"),
                user(2L, "数学", "统计", "2班"),
                user(3L, "物理", "力学", "3班")
        ));
        when(courseService.listByIds(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = teachingAnalytics.getAtRiskStudents(null, null, null, null, null);

        assertEquals(3, result.get("totalStudents"));
        assertEquals(2, result.get("atRiskCount"));
        assertEquals("school", result.get("groupBy"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) result.get("groups");
        assertTrue(groups.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("details");
        assertEquals(2, details.size());
    }

    // ===== getAtRiskStudents：course 维度 + HIGH/MEDIUM 严重度 + 用户画像 =====
    @Test
    @DisplayName("getAtRiskStudents - course 维度下钻，双低为 HIGH，单低为 MEDIUM")
    void testGetAtRiskStudents_CourseDimension() {
        List<CourseEnrollment> enrollments = Arrays.asList(
                enr(1L, 1L, new BigDecimal("10"), null),            // 仅进度低 -> MEDIUM
                enr(1L, 2L, new BigDecimal("80"), new BigDecimal("50")), // 仅成绩低 -> MEDIUM
                enr(2L, 3L, new BigDecimal("10"), new BigDecimal("40"))  // 双低 -> HIGH
        );
        when(enrollmentService.list()).thenReturn(enrollments);
        when(userService.listByIds(any())).thenReturn(Arrays.asList(
                user(1L, "计算机", "软件", "1班"),
                user(2L, "数学", "统计", "2班"),
                user(3L, "物理", "力学", "3班")
        ));
        Course c1 = new Course(); c1.setId(1L); c1.setName("Java");
        Course c2 = new Course(); c2.setId(2L); c2.setName("Python");
        when(courseService.listByIds(any())).thenReturn(Arrays.asList(c1, c2));

        Map<String, Object> result = teachingAnalytics.getAtRiskStudents("course", null, null, null, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) result.get("groups");
        assertEquals(2, groups.size());
        // course#1 atRisk=2 排在 course#2 atRisk=1 之前（按 atRisk 降序）
        assertEquals("Java", groups.get(0).get("key"));
        assertEquals(2L, groups.get(0).get("atRisk"));
        assertEquals("Python", groups.get(1).get("key"));
        assertEquals(1L, groups.get(1).get("atRisk"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("details");
        assertEquals(3, details.size());
        long high = details.stream().filter(d -> "HIGH".equals(d.get("level"))).count();
        long medium = details.stream().filter(d -> "MEDIUM".equals(d.get("level"))).count();
        assertEquals(1L, high);
        assertEquals(2L, medium);
        // 用户画像字段来自 userMap
        Map<String, Object> highDetail = details.stream()
                .filter(d -> "HIGH".equals(d.get("level"))).findFirst().orElseThrow();
        assertEquals("物理", highDetail.get("department"));
        assertEquals("力学", highDetail.get("major"));
        assertEquals("3班", highDetail.get("className"));
    }

    // ===== getAtRiskStudents：用户画像缺失回退 =====
    @Test
    @DisplayName("getAtRiskStudents - 学生无画像时姓名回退为学号，院系等字段缺失")
    void testGetAtRiskStudents_UserMissingFallsBack() {
        List<CourseEnrollment> enrollments = Arrays.asList(
                enr(1L, 999L, new BigDecimal("10"), null) // studentId 999 不在 userMap 中
        );
        when(enrollmentService.list()).thenReturn(enrollments);
        when(userService.listByIds(any())).thenReturn(Collections.emptyList()); // 返回空 -> u == null
        when(courseService.listByIds(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = teachingAnalytics.getAtRiskStudents("course", null, null, null, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("details");
        assertEquals(1, details.size());
        assertEquals("999", details.get(0).get("studentName"));
        assertFalse(details.get(0).containsKey("department"));
    }

    // ===== getAtRiskStudents：department/major/class 维度 + 未知回退 =====
    @Test
    @DisplayName("getAtRiskStudents - department 维度，含未知院系回退")
    void testGetAtRiskStudents_DepartmentDimension() {
        List<CourseEnrollment> enrollments = Arrays.asList(
                enr(1L, 1L, new BigDecimal("10"), null),
                enr(1L, 2L, new BigDecimal("10"), null)
        );
        when(enrollmentService.list()).thenReturn(enrollments);
        // student1 有院系，student2 院系为 null -> 命中"未知院系"
        when(userService.listByIds(any())).thenReturn(Arrays.asList(
                user(1L, "计算机", "软件", "1班"),
                user(2L, null, "统计", "2班")
        ));
        when(courseService.listByIds(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = teachingAnalytics.getAtRiskStudents("department", null, null, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) result.get("groups");
        Set<Object> keys = groups.stream().map(g -> g.get("key")).collect(Collectors.toSet());
        assertTrue(keys.contains("计算机"));
        assertTrue(keys.contains("未知院系"));
    }

    @Test
    @DisplayName("getAtRiskStudents - major 维度")
    void testGetAtRiskStudents_MajorDimension() {
        List<CourseEnrollment> enrollments = Arrays.asList(enr(1L, 1L, new BigDecimal("10"), null));
        when(enrollmentService.list()).thenReturn(enrollments);
        when(userService.listByIds(any())).thenReturn(Arrays.asList(user(1L, "计算机", "软件", "1班")));
        when(courseService.listByIds(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = teachingAnalytics.getAtRiskStudents("major", null, null, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) result.get("groups");
        assertEquals("软件", groups.get(0).get("key"));
    }

    @Test
    @DisplayName("getAtRiskStudents - class 维度")
    void testGetAtRiskStudents_ClassDimension() {
        List<CourseEnrollment> enrollments = Arrays.asList(enr(1L, 1L, new BigDecimal("10"), null));
        when(enrollmentService.list()).thenReturn(enrollments);
        when(userService.listByIds(any())).thenReturn(Arrays.asList(user(1L, "计算机", "软件", "1班")));
        when(courseService.listByIds(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = teachingAnalytics.getAtRiskStudents("class", null, null, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) result.get("groups");
        assertEquals("1班", groups.get(0).get("key"));
    }

    // ===== getAtRiskStudents：courseId 过滤路径 =====
    @Test
    @DisplayName("getAtRiskStudents - 传入 courseId 时仅统计该课程选课")
    void testGetAtRiskStudents_CourseIdFilter() {
        List<CourseEnrollment> scoped = Arrays.asList(
                enr(5L, 1L, new BigDecimal("10"), null) // 仅 courseId=5
        );
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(scoped);
        when(userService.listByIds(any())).thenReturn(Arrays.asList(user(1L, "计算机", "软件", "1班")));
        when(courseService.listByIds(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = teachingAnalytics.getAtRiskStudents("school", 5L, null, null, null);

        assertEquals(1, result.get("totalStudents"));
        assertEquals(1, result.get("atRiskCount"));
        verify(enrollmentService).list(any(LambdaQueryWrapper.class));
    }

    // ===== getAtRiskStudents：院系/专业/班级 profile 过滤路径 =====
    @Test
    @DisplayName("getAtRiskStudents - department 过滤仅保留匹配学生")
    void testGetAtRiskStudents_ProfileFilter() {
        List<CourseEnrollment> all = Arrays.asList(
                enr(1L, 1L, new BigDecimal("10"), null), // 计算机 -> 保留
                enr(1L, 2L, new BigDecimal("10"), null)  // 数学 -> 过滤掉
        );
        when(enrollmentService.list()).thenReturn(all);
        User u1 = user(1L, "计算机", "软件", "1班");
        User u2 = user(2L, "数学", "统计", "2班");
        when(userService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(u1));
        // studentIdSetByProfile 仅返回 1（计算机）
        when(userService.listByIds(any())).thenReturn(Arrays.asList(u1));
        when(courseService.listByIds(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = teachingAnalytics.getAtRiskStudents("school", null, "计算机", null, null);

        assertEquals(1, result.get("totalStudents"));
        assertEquals(1, result.get("atRiskCount"));
    }

    @Test
    @DisplayName("getAtRiskStudents - classId 过滤")
    void testGetAtRiskStudents_ClassIdFilter() {
        List<CourseEnrollment> all = Arrays.asList(
                enr(1L, 1L, new BigDecimal("10"), null),
                enr(1L, 2L, new BigDecimal("10"), null)
        );
        when(enrollmentService.list()).thenReturn(all);
        User u1 = user(1L, "计算机", "软件", "1班");
        User u2 = user(2L, "数学", "统计", "2班");
        when(userService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(u1));
        when(userService.listByIds(any())).thenReturn(Arrays.asList(u1));
        when(courseService.listByIds(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = teachingAnalytics.getAtRiskStudents("school", null, null, null, 1L);
        assertEquals(1, result.get("atRiskCount"));
    }

    // ===== getAtRiskStudents：details 超过 200 截断 =====
    @Test
    @DisplayName("getAtRiskStudents - 掉队明细超过200时截断为200")
    void testGetAtRiskStudents_DetailsTruncated() {
        List<CourseEnrollment> many = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            many.add(enr(1L, (long) (i % 3 + 1), new BigDecimal("10"), null)); // 全部进度低 -> 掉队
        }
        when(enrollmentService.list()).thenReturn(many);
        when(userService.listByIds(any())).thenReturn(Arrays.asList(
                user(1L, "计算机", "软件", "1班"),
                user(2L, "数学", "统计", "2班"),
                user(3L, "物理", "力学", "3班")
        ));
        when(courseService.listByIds(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = teachingAnalytics.getAtRiskStudents("school", null, null, null, null);
        assertEquals(250, result.get("totalStudents"));
        assertEquals(200, result.get("atRiskCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("details");
        assertEquals(200, details.size());
    }
}
