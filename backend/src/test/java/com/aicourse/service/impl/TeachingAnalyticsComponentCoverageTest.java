package com.aicourse.service.impl;

import com.aicourse.constant.ExamSessionStatus;
import com.aicourse.entity.AdminClass;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.TeachingClass;
import com.aicourse.entity.User;
import com.aicourse.mapper.AdminClassMapper;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.UserService;
import com.aicourse.service.component.analytics.CourseAnalyticsComponent;
import com.aicourse.service.component.analytics.TeachingAnalyticsComponent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * TeachingAnalyticsComponent 覆盖率补测（I-04）。
 *
 * <p>聚焦原测试未覆盖的教师仪表盘完整路径、getTeachingSummary 全部分支、
 * 预警按学院/课程过滤与分组回退。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeachingAnalyticsComponentCoverageTest {

    @Mock private CourseService courseService;
    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private TaskService taskService;
    @Mock private SubmissionService submissionService;
    @Mock private UserService userService;
    @Mock private CourseAnalyticsComponent courseAnalytics;
    @Mock private AdminClassMapper adminClassMapper;
    @Mock private TeachingClassMapper teachingClassMapper;
    @Mock private ExamPaperService examPaperService;
    @Mock private ExamSessionService examSessionService;

    @InjectMocks private TeachingAnalyticsComponent component;

    @BeforeEach
    void setUp() {
        lenient().when(enrollmentService.list()).thenReturn(Collections.emptyList());
        lenient().when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        lenient().when(userService.listByIds(any())).thenReturn(Collections.emptyList());
        lenient().when(courseService.listByIds(any())).thenReturn(Collections.emptyList());
        lenient().when(examPaperService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        lenient().when(examSessionService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        lenient().when(taskService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        lenient().when(submissionService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        AdminClass one = adminClass(1L, "1班");
        AdminClass two = adminClass(2L, "2班");
        AdminClass unnamed = adminClass(4L, null);
        lenient().when(adminClassMapper.selectById(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            if (id == 1L) return one;
            if (id == 2L) return two;
            if (id == 4L) return unnamed;
            return null;
        });
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(89L);
        teachingClass.setName("计网1班");
        TeachingClass unnamedTeachingClass = new TeachingClass();
        unnamedTeachingClass.setId(90L);
        unnamedTeachingClass.setName(null);
        lenient().when(teachingClassMapper.selectById(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            if (id == 89L) return teachingClass;
            if (id == 90L) return unnamedTeachingClass;
            return null;
        });
    }

    private static AdminClass adminClass(Long id, String name) {
        AdminClass adminClass = new AdminClass();
        adminClass.setId(id);
        adminClass.setName(name);
        return adminClass;
    }

    private static Course course(Long id, String name) {
        Course course = new Course();
        course.setId(id);
        course.setName(name);
        course.setCode("C" + id);
        return course;
    }

    private static CourseEnrollment enrollment(Long courseId, Long studentId, String progress, String score) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourseId(courseId);
        enrollment.setStudentId(studentId);
        enrollment.setProgress(progress == null ? null : new BigDecimal(progress));
        enrollment.setFinalScore(score == null ? null : new BigDecimal(score));
        return enrollment;
    }

    private static User user(Long id, String fullName, String department, String major, Long adminClassId) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        user.setDepartment(department);
        user.setMajor(major);
        user.setAdminClassId(adminClassId);
        user.setGrade("2024");
        return user;
    }

    private static Task task(Long id, Long courseId) {
        Task task = new Task();
        task.setId(id);
        task.setCourseId(courseId);
        return task;
    }

    private static Submission submission(Long id, Long taskId, String score, String status) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setTaskId(taskId);
        submission.setScore(score == null ? null : new BigDecimal(score));
        submission.setStatus(status);
        submission.setCreatedAt(LocalDateTime.now());
        return submission;
    }

    // ===================== getTeacherDashboard 完整路径 =====================

    @Test
    @DisplayName("教师仪表盘：课程/学生/待批/均分/分布/趋势全路径")
    void dashboard_fullPath() {
        when(courseService.count(any(LambdaQueryWrapper.class))).thenReturn(1L);
        Course c1 = course(1L, "Java");
        Course c2 = course(null, "无效课程");
        when(courseService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(c1, c2));

        when(enrollmentService.count(any(LambdaQueryWrapper.class))).thenReturn(2L);
        when(taskService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(task(11L, 1L), task(null, 1L)));
        when(submissionService.count(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(submissionService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                submission(1L, 11L, "80", "SUBMITTED"),
                submission(2L, 11L, null, "SUBMITTED")
        ));

        Map<String, Object> data = component.getTeacherDashboard(9L);

        assertEquals(1L, data.get("courseCount"));
        assertEquals(1L, data.get("totalCourses"));
        assertEquals(2L, data.get("studentCount"));
        assertEquals(2L, data.get("totalStudents"));
        assertEquals(1L, data.get("pendingGradeCount"));
        assertEquals(1L, data.get("pendingGrading"));
        assertEquals(40.0, (Double) data.get("avgScore"), 0.001);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> distribution = (List<Map<String, Object>>) data.get("courseDistribution");
        assertEquals(2, distribution.size());
        assertEquals("Java", distribution.get(0).get("name"));
        assertEquals(2L, distribution.get(0).get("value"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trend = (List<Map<String, Object>>) data.get("trendData");
        assertEquals(7, trend.size());
        assertEquals(1L, trend.get(0).get("value"));
    }

    @Test
    @DisplayName("教师仪表盘：有课程但无任务时待批与均分为零")
    void dashboard_courseWithoutTasks() {
        when(courseService.count(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(courseService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(course(1L, "Java")));
        when(enrollmentService.count(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(taskService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        Map<String, Object> data = component.getTeacherDashboard(9L);

        assertEquals(0L, data.get("pendingGradeCount"));
        assertEquals(0.0, (Double) data.get("avgScore"), 0.001);
    }

    // ===================== exportReport 分支补足 =====================

    @Test
    @DisplayName("导出报告：四档成绩、空进度、空分数与 Top10 排序")
    void exportReport_scoreBucketsAndNulls() {
        when(courseAnalytics.getCourseAnalysis(1L)).thenReturn(new java.util.HashMap<>());
        when(courseService.getById(1L)).thenReturn(course(1L, "Java"));
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                enrollment(1L, 1L, "100", "95"),
                enrollment(1L, 2L, "80", "85"),
                enrollment(1L, 3L, "70", "75"),
                enrollment(1L, 4L, "60", "65"),
                enrollment(1L, 5L, "50", "55"),
                enrollment(1L, 6L, null, null),
                enrollment(1L, 7L, null, "88")
        ));
        when(taskService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        Map<String, Object> report = component.exportReport(1L);

        @SuppressWarnings("unchecked")
        Map<String, Long> distribution = (Map<String, Long>) report.get("scoreDistribution");
        assertEquals(1L, distribution.get("excellent"));
        assertEquals(2L, distribution.get("good"));
        assertEquals(2L, distribution.get("average"));
        assertEquals(1L, distribution.get("fail"));
        assertEquals(0.0, (Double) report.get("submissionRate"), 0.001);
        assertEquals(0, report.get("totalTasks"));
        assertEquals(0L, report.get("totalSubmissions"));
        assertEquals(1L, report.get("completedStudents"));
        assertEquals(14.29, (Double) report.get("completionRate"), 0.001);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ranking = (List<Map<String, Object>>) report.get("studentRanking");
        assertEquals(6, ranking.size());
        assertEquals(new BigDecimal("95"), ranking.get(0).get("score"));
    }

    @Test
    @DisplayName("导出报告：空选课集合不除零")
    void exportReport_emptyEnrollments() {
        when(courseAnalytics.getCourseAnalysis(2L)).thenReturn(new java.util.HashMap<>());
        when(courseService.getById(2L)).thenReturn(course(2L, "空课程"));
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(taskService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(task(21L, 2L)));
        when(submissionService.count(any(LambdaQueryWrapper.class))).thenReturn(0L);

        Map<String, Object> report = component.exportReport(2L);

        assertEquals(0.0, (Double) report.get("submissionRate"), 0.001);
        assertEquals(0.0, (Double) report.get("averageProgress"), 0.001);
        assertEquals(0L, report.get("completedStudents"));
        assertEquals(0.0, (Double) report.get("completionRate"), 0.001);
        assertTrue(((List<?>) report.get("studentRanking")).isEmpty());
    }

    // ===================== getAtRiskStudents：学院/课程过滤 =====================

    @Test
    @DisplayName("预警：空白 groupBy 按全校，空数据不除零")
    void atRisk_blankGroupByAndEmpty() {
        Map<String, Object> result = component.getAtRiskStudents("   ", null, null, null, null);

        assertEquals("school", result.get("groupBy"));
        assertEquals(0, result.get("totalStudents"));
        assertEquals(0, result.get("atRiskCount"));
        assertEquals(0.0, (Double) result.get("atRiskRate"), 0.001);
        assertTrue(((List<?>) result.get("groups")).isEmpty());
        assertTrue(((List<?>) result.get("details")).isEmpty());
    }

    @Test
    @DisplayName("预警：学院无课程时返回空集合")
    void atRisk_collegeWithoutCourses() {
        when(courseService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        Map<String, Object> result = component.getAtRiskStudents("school", null, null, null, null, 5L);

        assertEquals(0, result.get("totalStudents"));
        assertEquals("school", result.get("groupBy"));
    }

    @Test
    @DisplayName("预警：学院有课程且未传 courseId 时按课程集合查询")
    void atRisk_collegeWithCourses() {
        when(courseService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(course(1L, "Java")));
        when(enrollmentService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(enrollment(1L, 1L, "10", null)));

        Map<String, Object> result = component.getAtRiskStudents("school", null, null, null, null, 5L);

        assertEquals(1, result.get("totalStudents"));
        assertEquals(1, result.get("atRiskCount"));
        assertTrue(((List<?>) result.get("details")).size() == 1);
    }

    @Test
    @DisplayName("预警：courseId 属于学院时正常查询")
    void atRisk_courseInsideCollege() {
        when(courseService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(course(1L, "Java")));
        when(enrollmentService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(enrollment(1L, 1L, "10", null)));

        Map<String, Object> result = component.getAtRiskStudents("school", 1L, null, null, null, 5L);

        assertEquals(1, result.get("totalStudents"));
    }

    @Test
    @DisplayName("预警：courseId 不属于学院时直接空结果")
    void atRisk_courseOutsideCollege() {
        when(courseService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(course(1L, "Java")));

        Map<String, Object> result = component.getAtRiskStudents("school", 3L, null, null, null, 5L);

        assertEquals(0, result.get("totalStudents"));
        assertEquals(0, result.get("atRiskCount"));
        assertTrue(((List<?>) result.get("details")).isEmpty());
    }

    // ===================== getAtRiskStudents：分组维度 =====================

    @Test
    @DisplayName("预警：adminClass 分组在学生缺失或 classId 为空时回退未知")
    void atRisk_adminClassMissingStudentAndNullId() {
        when(enrollmentService.list()).thenReturn(Arrays.asList(
                enrollment(1L, 1L, "10", null),
                enrollment(1L, 2L, "10", null)
        ));
        when(userService.listByIds(any())).thenReturn(Arrays.asList(
                user(2L, "乙", "计算机", "软件", null)
        ));
        when(courseService.listByIds(any())).thenReturn(List.of(course(1L, "Java")));

        Map<String, Object> result = component.getAtRiskStudents("adminClass", null, null, null, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) result.get("groups");
        Set<Object> keys = groups.stream().map(g -> g.get("key")).collect(Collectors.toSet());
        assertTrue(keys.contains("未知行政班"));
        assertEquals(1, groups.size());
    }

    @Test
    @DisplayName("预警：专业分组在学生存在但专业为空时回退未知专业")
    void atRisk_majorPresentButNull() {
        when(enrollmentService.list()).thenReturn(List.of(enrollment(1L, 1L, "10", null)));
        when(userService.listByIds(any())).thenReturn(Arrays.asList(
                user(1L, "甲", "计算机", null, 1L)
        ));
        when(courseService.listByIds(any())).thenReturn(List.of(course(1L, "Java")));

        Map<String, Object> result = component.getAtRiskStudents("major", null, null, null, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) result.get("groups");
        assertEquals("未知专业", groups.get(0).get("key"));
    }

    @Test
    @DisplayName("预警：教学班名称为空时回退未知班级")
    void atRisk_teachingClassDimension() {
        CourseEnrollment withClass = enrollment(1L, 1L, "10", null);
        withClass.setTeachingClassId(89L);
        CourseEnrollment withoutClass = enrollment(1L, 2L, "10", null);
        withoutClass.setTeachingClassId(null);
        CourseEnrollment unknownClass = enrollment(1L, 3L, "10", null);
        unknownClass.setTeachingClassId(99L);
        CourseEnrollment unnamedClass = enrollment(1L, 4L, "10", null);
        unnamedClass.setTeachingClassId(90L);
        when(enrollmentService.list()).thenReturn(Arrays.asList(withClass, withoutClass, unknownClass, unnamedClass));
        when(userService.listByIds(any())).thenReturn(Arrays.asList(
                user(1L, "甲", "计算机", "软件", 1L),
                user(2L, "乙", "计算机", "软件", null),
                user(3L, "丙", "计算机", "软件", 1L),
                user(4L, "丁", "计算机", "软件", 1L)
        ));
        when(courseService.listByIds(any())).thenReturn(Arrays.asList(course(1L, "Java")));

        Map<String, Object> result = component.getAtRiskStudents("teachingClass", null, null, null, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) result.get("groups");
        Set<Object> keys = groups.stream().map(g -> g.get("key")).collect(Collectors.toSet());
        assertTrue(keys.contains("计网1班"));
        assertTrue(keys.contains("未知班级"));
    }

    @Test
    @DisplayName("预警：adminClass 别名分组与未知行政班")
    void atRisk_adminClassAlias() {
        when(enrollmentService.list()).thenReturn(Arrays.asList(
                enrollment(1L, 1L, "10", null),
                enrollment(1L, 2L, "10", null),
                enrollment(1L, 3L, "10", null)
        ));
        when(userService.listByIds(any())).thenReturn(Arrays.asList(
                user(1L, "甲", "计算机", "软件", 2L),
                user(2L, "乙", "计算机", "软件", 99L),
                user(3L, "丙", "计算机", "软件", 4L)
        ));
        when(courseService.listByIds(any())).thenReturn(Arrays.asList(course(1L, "Java")));

        Map<String, Object> result = component.getAtRiskStudents("adminClass", null, null, null, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) result.get("groups");
        Set<Object> keys = groups.stream().map(g -> g.get("key")).collect(Collectors.toSet());
        assertTrue(keys.contains("2班"));
        assertTrue(keys.contains("未知行政班"));
    }

    @Test
    @DisplayName("预警：课程名为空时分组名归一为空串")
    void atRisk_courseNameFallback() {
        when(enrollmentService.list()).thenReturn(List.of(enrollment(1L, 1L, "10", null)));
        when(userService.listByIds(any())).thenReturn(List.of(user(1L, "甲", "计算机", "软件", 1L)));
        when(courseService.listByIds(any())).thenReturn(List.of(course(1L, null)));

        Map<String, Object> result = component.getAtRiskStudents("course", null, null, null, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) result.get("groups");
        assertEquals("", groups.get(0).get("key"));
    }

    @Test
    @DisplayName("预警：院系/专业分组对缺失画像回退")
    void atRisk_departmentAndMajorUnknown() {
        when(enrollmentService.list()).thenReturn(List.of(enrollment(1L, 999L, "10", null)));
        when(userService.listByIds(any())).thenReturn(Collections.emptyList());
        when(courseService.listByIds(any())).thenReturn(Collections.emptyList());

        Map<String, Object> department = component.getAtRiskStudents("department", null, null, null, null);
        Map<String, Object> major = component.getAtRiskStudents("major", null, null, null, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> departmentGroups = (List<Map<String, Object>>) department.get("groups");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> majorGroups = (List<Map<String, Object>>) major.get("groups");
        assertEquals("未知院系", departmentGroups.get(0).get("key"));
        assertEquals("未知专业", majorGroups.get(0).get("key"));
    }

    @Test
    @DisplayName("预警：详情姓名回退、空进度记 0、空成绩记 null")
    void atRisk_detailFallbacks() {
        CourseEnrollment progressLow = enrollment(1L, 1L, "10", null);
        CourseEnrollment scoreLow = enrollment(1L, 2L, null, "50");
        when(enrollmentService.list()).thenReturn(Arrays.asList(progressLow, scoreLow));
        when(userService.listByIds(any())).thenReturn(Arrays.asList(
                user(1L, null, "计算机", "软件", null),
                user(2L, "乙", "数学", "统计", null)
        ));
        when(courseService.listByIds(any())).thenReturn(Arrays.asList(course(1L, "Java")));

        Map<String, Object> result = component.getAtRiskStudents("school", null, null, null, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("details");
        Map<String, Object> first = details.stream()
                .filter(d -> Long.valueOf(1L).equals(d.get("studentId")))
                .findFirst().orElseThrow();
        Map<String, Object> second = details.stream()
                .filter(d -> Long.valueOf(2L).equals(d.get("studentId")))
                .findFirst().orElseThrow();
        assertEquals("1", first.get("studentName"));
        assertNull(first.get("className"));
        assertEquals(10.0, (Double) first.get("progress"), 0.001);
        assertNull(first.get("finalScore"));
        assertEquals(0.0, (Double) second.get("progress"), 0.001);
        assertEquals(50.0, (Double) second.get("finalScore"), 0.001);
    }

    @Test
    @DisplayName("预警：major 单一画像过滤生效")
    void atRisk_majorProfileFilter() {
        when(enrollmentService.list()).thenReturn(Arrays.asList(
                enrollment(1L, 1L, "10", null),
                enrollment(1L, 2L, "10", null)
        ));
        User software = user(1L, "甲", "计算机", "软件", 1L);
        when(userService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(software));
        when(userService.listByIds(any())).thenReturn(List.of(software));
        when(courseService.listByIds(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = component.getAtRiskStudents("school", null, null, "软件", null);

        assertEquals(1, result.get("totalStudents"));
    }

    // ===================== getTeachingSummary =====================

    @Test
    @DisplayName("教学总览：无课程返回空列表")
    void summary_noCourses() {
        when(courseService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        assertTrue(component.getTeachingSummary().isEmpty());
    }

    @Test
    @DisplayName("教学总览：课程聚合、空分母、考试/待批映射与空记录过滤")
    void summary_fullPath() {
        Course c1 = course(1L, "Java");
        Course c2 = course(2L, "空课程");
        when(courseService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(c1, c2));

        CourseEnrollment e1 = enrollment(1L, 1L, "50", "55");
        CourseEnrollment e2 = enrollment(1L, 2L, "100", "80");
        CourseEnrollment e3 = enrollment(1L, 3L, null, null);
        CourseEnrollment orphan = enrollment(null, 4L, "10", "10");
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(e1, e2, e3, orphan));

        ExamPaper paper1 = new ExamPaper();
        paper1.setId(10L);
        paper1.setCourseId(1L);
        when(examPaperService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(paper1));
        ExamSession started = new ExamSession();
        started.setExamPaperId(10L);
        started.setStatus(ExamSessionStatus.STARTED);
        ExamSession unknownPaper = new ExamSession();
        unknownPaper.setExamPaperId(99L);
        unknownPaper.setStatus(ExamSessionStatus.STARTED);
        when(examSessionService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(started, unknownPaper));

        when(taskService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(task(100L, 1L)));
        when(submissionService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                submission(1L, 100L, "80", "SUBMITTED"),
                submission(2L, 999L, "80", "SUBMITTED")
        ));

        List<Map<String, Object>> summary = component.getTeachingSummary();

        assertEquals(2, summary.size());
        Map<String, Object> first = summary.stream()
                .filter(m -> Long.valueOf(1L).equals(m.get("courseId"))).findFirst().orElseThrow();
        Map<String, Object> second = summary.stream()
                .filter(m -> Long.valueOf(2L).equals(m.get("courseId"))).findFirst().orElseThrow();
        assertEquals(3L, first.get("studentCount"));
        assertEquals(new BigDecimal("75.0"), first.get("avgProgress"));
        assertEquals(new BigDecimal("67.5"), first.get("avgFinalScore"));
        assertEquals(1L, first.get("atRiskCount"));
        assertEquals(1L, first.get("activeExams"));
        assertEquals(1L, first.get("pendingGrading"));
        assertEquals(0L, second.get("studentCount"));
        assertNull(second.get("avgProgress"));
        assertNull(second.get("avgFinalScore"));
        assertEquals(0L, second.get("atRiskCount"));
        assertEquals(0L, second.get("activeExams"));
        assertEquals(0L, second.get("pendingGrading"));
    }

    @Test
    @DisplayName("教学总览：无考试/无任务时映射为空")
    void summary_withoutExamsAndTasks() {
        when(courseService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(course(1L, "Java")));
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<Map<String, Object>> summary = component.getTeachingSummary();

        assertEquals(1, summary.size());
        assertEquals(0L, summary.get(0).get("studentCount"));
        assertNull(summary.get(0).get("avgProgress"));
        assertNull(summary.get(0).get("avgFinalScore"));
        assertEquals(0L, summary.get(0).get("activeExams"));
        assertEquals(0L, summary.get(0).get("pendingGrading"));
    }

    @Test
    @DisplayName("预警：课程分组不存在的课程名按 ID 回退且详情截断逻辑保持")
    void atRisk_courseGroupWithoutMapping() {
        when(enrollmentService.list()).thenReturn(List.of(enrollment(3L, 1L, "10", null)));
        when(userService.listByIds(any())).thenReturn(List.of(user(1L, "甲", "计算机", "软件", 1L)));
        when(courseService.listByIds(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = component.getAtRiskStudents("course", null, null, null, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) result.get("groups");
        assertEquals("课程#3", groups.get(0).get("key"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("details");
        assertEquals("", details.get(0).get("courseName"));
    }

    @Test
    @DisplayName("教学总览：有学生但全无进度与成绩时均值为空")
    void summary_rowsWithoutProgressOrScore() {
        when(courseService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(course(1L, "Java")));
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                enrollment(1L, 1L, null, null),
                enrollment(1L, 2L, null, null)
        ));

        List<Map<String, Object>> summary = component.getTeachingSummary();

        assertEquals(1, summary.size());
        assertEquals(2L, summary.get(0).get("studentCount"));
        assertNull(summary.get(0).get("avgProgress"));
        assertNull(summary.get(0).get("avgFinalScore"));
        assertEquals(0L, summary.get(0).get("atRiskCount"));
    }

    @Test
    @DisplayName("预警：details 为空时各字段为 0/空")
    void atRisk_emptyDetails() {
        Map<String, Object> result = component.getAtRiskStudents("course", null, null, null, null);
        assertEquals(0, result.get("totalStudents"));
        assertEquals(0, result.get("atRiskCount"));
        assertEquals(0.0, (Double) result.get("atRiskRate"), 0.001);
        assertFalse(result.containsKey("error"));
        assertNotNull(result.get("groups"));
    }

    @Test
    @DisplayName("教学总览：课程排序按学生数降序")
    void summary_sortedByStudentCount() {
        when(courseService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(course(2L, "多学生"), course(1L, "少学生")));
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                enrollment(1L, 1L, "10", null),
                enrollment(2L, 1L, "10", null),
                enrollment(2L, 2L, "10", null)
        ));

        List<Map<String, Object>> summary = component.getTeachingSummary();

        assertEquals(2L, summary.get(0).get("courseId"));
        assertEquals(2L, summary.get(0).get("studentCount"));
        assertEquals(1L, summary.get(1).get("studentCount"));
    }
}
