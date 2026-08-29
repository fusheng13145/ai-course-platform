package com.aicourse.service.impl;

import com.aicourse.constant.SubmissionStatus;
import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.*;
import com.aicourse.service.component.TeacherChapterComponent;
import com.aicourse.service.component.TeacherCourseSupport;
import com.aicourse.service.component.TeacherDashboardComponent;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TeacherCourseServiceImpl 指令覆盖率强化测试。
 * 覆盖教学驾驶舱/AI 建课大纲/课程克隆/我的课程聚合/增强版仪表盘/章节树/班级列表，
 * 以及组件与 Support 私有辅助（round2/userNameMap/buildAtRisk/severity/tryParseJson/AI insight）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeacherCourseServiceImplBoostTest {

    @Mock
    private CourseService courseService;
    @Mock
    private CourseChapterService chapterService;
    @Mock
    private CourseEnrollmentService enrollmentService;
    @Mock
    private TaskService taskService;
    @Mock
    private SubmissionService submissionService;
    @Mock
    private ExamPaperService examPaperService;
    @Mock
    private QuestionService questionService;
    @Mock
    private LearningRecordService learningRecordService;
    @Mock
    private AIService aiService;
    @Mock
    private UserService userService;
    @Mock
    private ClassroomService classroomService;

    private TeacherCourseServiceImpl service;
    private TeacherCourseSupport support;
    private TeacherDashboardComponent dashboard;

    @BeforeEach
    void setUp() {
        support = new TeacherCourseSupport(courseService, userService);
        TeacherChapterComponent chapterComp = new TeacherChapterComponent(chapterService, enrollmentService,
                taskService, submissionService, userService, classroomService, support);
        dashboard = new TeacherDashboardComponent(courseService, chapterService, enrollmentService, taskService,
                submissionService, examPaperService, questionService, learningRecordService, aiService,
                support, chapterComp);
        service = new TeacherCourseServiceImpl(courseService, chapterService, enrollmentService, taskService,
                submissionService, examPaperService, questionService, learningRecordService, aiService,
                userService, classroomService);
        // 纯 Mockito 环境缺失 MyBatis-Plus 实体列缓存：显式注册 User 实体 TableInfo（courseClasses 测试依赖）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
    }

    private CourseEnrollment enrollment(Long studentId, BigDecimal progress, BigDecimal finalScore) {
        CourseEnrollment e = new CourseEnrollment();
        e.setStudentId(studentId);
        e.setProgress(progress);
        e.setFinalScore(finalScore);
        return e;
    }

    // ==================== getCourseTeachingDashboard ====================

    @Test
    @DisplayName("getCourseTeachingDashboard: 课程不存在抛异常")
    void dashboard_courseNotFound() {
        when(courseService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getCourseTeachingDashboard(1L, 7L));
    }

    @Test
    @DisplayName("getCourseTeachingDashboard: 成功聚合（含预警/AI洞察）")
    void dashboard_success() {
        Course c = new Course();
        c.setId(1L);
        c.setName("Java课");
        when(courseService.getById(1L)).thenReturn(c);

        when(enrollmentService.count(any())).thenReturn(3L);
        when(chapterService.count(any())).thenReturn(2L);
        when(taskService.count(any())).thenReturn(1L);
        when(examPaperService.count(any())).thenReturn(1L);
        when(questionService.count(any())).thenReturn(5L);
        when(learningRecordService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());

        CourseEnrollment e1 = enrollment(11L, new BigDecimal("30"), null);   // 低进度
        CourseEnrollment e2 = enrollment(12L, new BigDecimal("90"), new BigDecimal("50")); // 低分
        when(enrollmentService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Arrays.asList(e1, e2));

        when(taskService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
        when(submissionService.count(any())).thenReturn(0L);
        when(chapterService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());

        User u1 = new User(); u1.setId(11L); u1.setFullName("张三");
        User u2 = new User(); u2.setId(12L); u2.setUsername("stu12");
        when(userService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Arrays.asList(u1, u2));

        when(aiService.generateLearningAdviceJson(anyString(), anyString()))
                .thenReturn("{\"summary\":\"整体表现良好\"}");

        Map<String, Object> d = service.getCourseTeachingDashboard(1L, 7L);
        assertEquals("Java课", d.get("courseName"));
        assertNotNull(d.get("aiInsight"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> atRisk = (List<Map<String, Object>>) d.get("atRiskStudents");
        assertEquals(2, atRisk.size());
    }

    @Test
    @DisplayName("getCourseTeachingDashboard: 计算异常被捕获并记录 error")
    void dashboard_exception() {
        Course c = new Course();
        c.setId(1L);
        c.setName("课");
        when(courseService.getById(1L)).thenReturn(c);
        when(enrollmentService.count(any())).thenThrow(new RuntimeException("db down"));
        Map<String, Object> d = service.getCourseTeachingDashboard(1L, 7L);
        assertTrue(d.containsKey("error"));
    }

    // ==================== computeAtRisk / buildAtRisk ====================

    @Test
    @DisplayName("computeAtRisk: 空列表与含预警学生")
    void computeAtRisk() {
        assertTrue(service.computeAtRisk(Collections.emptyList()).isEmpty());
        CourseEnrollment e = enrollment(11L, new BigDecimal("20"), new BigDecimal("40"));
        List<Map<String, Object>> r = service.computeAtRisk(Collections.singletonList(e));
        assertEquals(1, r.size());
        assertEquals("HIGH", r.get(0).get("level")); // 双低
    }

    // ==================== generateCourseOutlineByAI ====================

    @Test
    @DisplayName("generateCourseOutlineByAI: 主题为空抛异常")
    void outline_blankTopic() {
        assertThrows(BusinessException.class, () -> service.generateCourseOutlineByAI("  ", "通用", "入门", 6, 7L));
    }

    @Test
    @DisplayName("generateCourseOutlineByAI: 正常返回大纲")
    void outline_success() {
        when(aiService.chat(anyString(), anyString(), anyLong()))
                .thenReturn("{\"title\":\"课\",\"description\":\"d\",\"chapters\":[]}");
        Map<String, Object> r = service.generateCourseOutlineByAI("Java", "通用", "入门", 8, 7L);
        assertEquals(true, r.get("success"));
        assertNotNull(r.get("outline"));
    }

    @Test
    @DisplayName("generateCourseOutlineByAI: AI 返回无法解析")
    void outline_unparseable() {
        when(aiService.chat(anyString(), anyString(), anyLong())).thenReturn("这不是JSON");
        Map<String, Object> r = service.generateCourseOutlineByAI("Java", "通用", "入门", 0, 7L);
        assertEquals(false, r.get("success"));
        assertNotNull(r.get("raw"));
    }

    @Test
    @DisplayName("generateCourseOutlineByAI: AI 调用失败")
    void outline_exception() {
        when(aiService.chat(anyString(), anyString(), anyLong())).thenThrow(new RuntimeException("boom"));
        Map<String, Object> r = service.generateCourseOutlineByAI("Java", "通用", "入门", 6, 7L);
        assertEquals(false, r.get("success"));
    }

    // ==================== cloneCourse ====================

    @Test
    @DisplayName("cloneCourse: 源课程不存在抛异常")
    void clone_notFound() {
        when(courseService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.cloneCourse(1L, "新名", 7L));
    }

    @Test
    @DisplayName("cloneCourse: 克隆成功（含父子章节修正）")
    void clone_success() {
        Course src = new Course();
        src.setId(1L);
        src.setName("源课");
        src.setCode("C1");
        when(courseService.getById(1L)).thenReturn(src);

        CourseChapter parent = new CourseChapter();
        parent.setId(1L); parent.setTitle("父"); parent.setOrderIndex(1);
        CourseChapter child = new CourseChapter();
        child.setId(2L); child.setTitle("子"); child.setOrderIndex(2); child.setParentId(1L);
        when(chapterService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Arrays.asList(parent, child));

        // 克隆后新课程章节（带新 id）
        CourseChapter clonedParent = new CourseChapter();
        clonedParent.setId(10L); clonedParent.setTitle("父"); clonedParent.setOrderIndex(1);
        CourseChapter clonedChild = new CourseChapter();
        clonedChild.setId(20L); clonedChild.setTitle("子"); clonedChild.setOrderIndex(2); clonedChild.setParentId(1L);
        // 第二次 list（按新课程 id 查）返回克隆章节
        when(chapterService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Arrays.asList(parent, child),
                Arrays.asList(clonedParent, clonedChild));

        Map<String, Object> r = service.cloneCourse(1L, "副本课", 7L);
        assertEquals("副本课", r.get("newCourseName"));
        verify(chapterService, atLeastOnce()).updateById(any(CourseChapter.class));
    }

    // ==================== getMyCourseSummaries ====================

    @Test
    @DisplayName("getMyCourseSummaries: 无课程返回空")
    void summaries_empty() {
        when(courseService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
        assertTrue(service.getMyCourseSummaries(7L).isEmpty());
    }

    @Test
    @DisplayName("getMyCourseSummaries: 单课程聚合")
    void summaries_one() {
        Course c = new Course();
        c.setId(1L); c.setName("课"); c.setCode("C"); c.setStatus("ACTIVE"); c.setCategory("CAT");
        when(courseService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.singletonList(c));
        when(enrollmentService.count(any())).thenReturn(2L);
        when(enrollmentService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
        when(taskService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
        when(submissionService.count(any())).thenReturn(0L);
        List<Map<String, Object>> r = service.getMyCourseSummaries(7L);
        assertEquals(1, r.size());
    }

    // ==================== getTeacherDashboardV2 ====================

    @Test
    @DisplayName("getTeacherDashboardV2: 成功聚合")
    void teacherDashboardV2_success() {
        Course c = new Course();
        c.setId(1L); c.setName("课");
        when(courseService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.singletonList(c));
        when(enrollmentService.count(any())).thenReturn(2L);
        when(taskService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
        when(submissionService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
        when(enrollmentService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
        when(aiService.generateLearningAdviceJson(anyString(), anyString()))
                .thenReturn("{\"summary\":\"总览\"}");

        Map<String, Object> d = service.getTeacherDashboardV2(7L);
        assertEquals(1L, d.get("courseCount"));
        assertNotNull(d.get("aiOverview"));
    }

    @Test
    @DisplayName("getTeacherDashboardV2: 异常捕获降级")
    void teacherDashboardV2_exception() {
        when(courseService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenThrow(new RuntimeException("boom"));
        Map<String, Object> d = service.getTeacherDashboardV2(7L);
        assertTrue(d.containsKey("error"));
        assertEquals(0L, d.get("courseCount"));
    }

    // ==================== getFullChapterTree ====================

    @Test
    @DisplayName("getFullChapterTree: 无章节/父子嵌套/提交统计")
    void chapterTree() {
        when(chapterService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
        when(enrollmentService.count(any())).thenReturn(0L);
        when(taskService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
        assertTrue(service.getFullChapterTree(1L).isEmpty());

        // 父子嵌套
        CourseChapter parent = new CourseChapter();
        parent.setId(1L); parent.setTitle("父"); parent.setOrderIndex(1);
        CourseChapter child = new CourseChapter();
        child.setId(2L); child.setTitle("子"); child.setOrderIndex(2); child.setParentId(1L);
        when(chapterService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Arrays.asList(parent, child));
        when(enrollmentService.count(any())).thenReturn(5L);

        Task t = new Task(); t.setId(3L); t.setChapterId(2L);
        when(taskService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.singletonList(t));
        Submission sub = new Submission(); sub.setTaskId(3L); sub.setStatus(SubmissionStatus.SUBMITTED);
        when(submissionService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.singletonList(sub));
        when(examPaperService.count(any())).thenReturn(0L);
        when(questionService.count(any())).thenReturn(0L);

        List<Map<String, Object>> tree = service.getFullChapterTree(1L);
        assertEquals(1, tree.size()); // 只有根（父），子被嵌套
    }

    // ==================== getCourseClasses ====================

    @Test
    @DisplayName("getCourseClasses: 空选课返回空")
    void courseClasses_empty() {
        when(enrollmentService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
        assertTrue(service.getCourseClasses(1L).isEmpty());
    }

    @Test
    @DisplayName("getCourseClasses: 按班级分组并返回（班级缺失则跳过）")
    void courseClasses_success() {
        CourseEnrollment e = new CourseEnrollment();
        e.setStudentId(11L);
        when(enrollmentService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.singletonList(e));

        User u = new User(); u.setId(11L); u.setClassId(99L);
        when(userService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.singletonList(u));

        Classroom cls = new Classroom(); cls.setId(99L); cls.setName("一班"); cls.setCode("C01");
        when(classroomService.listByIds(any())).thenReturn(Collections.singletonList(cls));
        when(taskService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
        when(submissionService.count(any())).thenReturn(0L);

        List<Map<String, Object>> r = service.getCourseClasses(1L);
        assertEquals(1, r.size());

        // 班级为 null -> 跳过
        when(classroomService.listByIds(any())).thenReturn(Collections.singletonList((Classroom) null));
        assertTrue(service.getCourseClasses(1L).isEmpty());
    }

    // ==================== 私有方法（已下沉至组件/Support）反射 ====================

    @Test
    @DisplayName("round2: 四舍五入保留两位")
    void round2() throws Exception {
        Method m = TeacherCourseSupport.class.getDeclaredMethod("round2", double.class);
        m.setAccessible(true);
        assertEquals(1.24, (double) m.invoke(support, 1.236));
    }

    @Test
    @DisplayName("userNameMap: 空集合返回空，正常映射取 fullName/用户名")
    void userNameMap() throws Exception {
        Method m = TeacherCourseSupport.class.getDeclaredMethod("userNameMap", Collection.class);
        m.setAccessible(true);
        assertTrue(((Map<?, ?>) m.invoke(support, (Object) null)).isEmpty());
        assertTrue(((Map<?, ?>) m.invoke(support, Collections.emptyList())).isEmpty());

        User u = new User(); u.setId(11L); u.setUsername("stu");
        when(userService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.singletonList(u));
        @SuppressWarnings("unchecked")
        Map<Long, String> map = (Map<Long, String>) m.invoke(support, Collections.singletonList(11L));
        assertEquals("stu", map.get(11L));
    }

    @Test
    @DisplayName("severity: 双低=2/单低=1/无=0")
    void severity() throws Exception {
        Method m = TeacherDashboardComponent.class.getDeclaredMethod("severity", CourseEnrollment.class);
        m.setAccessible(true);
        assertEquals(2, m.invoke(dashboard, enrollment(1L, new BigDecimal("10"), new BigDecimal("10"))));
        assertEquals(1, m.invoke(dashboard, enrollment(1L, new BigDecimal("10"), null)));
        assertEquals(1, m.invoke(dashboard, enrollment(1L, null, new BigDecimal("10"))));
        assertEquals(0, m.invoke(dashboard, enrollment(1L, new BigDecimal("90"), new BigDecimal("90"))));
    }

    @Test
    @DisplayName("tryParseJson: null/围栏/纯JSON/非法")
    void tryParseJson() throws Exception {
        Method m = TeacherCourseSupport.class.getDeclaredMethod("tryParseJson", String.class);
        m.setAccessible(true);
        assertNull(m.invoke(support, (Object) null));
        assertNull(m.invoke(support, "   "));
        assertTrue(m.invoke(support, "```json\n{\"a\":1}\n```") instanceof Map);
        assertTrue(m.invoke(support, "{\"a\":1}") instanceof Map);
        assertNull(m.invoke(support, "not json at all"));
    }

    @Test
    @DisplayName("buildCourseAIInsight: 解析成功/纯文本降级/异常降级")
    void buildCourseAIInsight() throws Exception {
        Method m = TeacherDashboardComponent.class.getDeclaredMethod("buildCourseAIInsight",
                Course.class, long.class, double.class, double.class, double.class, double.class, long.class, List.class);
        m.setAccessible(true);
        Course c = new Course(); c.setName("课");
        List<Map<String, Object>> atRisk = new ArrayList<>();

        when(aiService.generateLearningAdviceJson(anyString(), anyString())).thenReturn("{\"summary\":\"ok\"}");
        assertTrue(m.invoke(dashboard, c, 1L, 50.0, 50.0, 60.0, 80.0, 0L, atRisk) instanceof Map);

        when(aiService.generateLearningAdviceJson(anyString(), anyString())).thenReturn("纯文本建议");
        Map<?, ?> fb = (Map<?, ?>) m.invoke(dashboard, c, 1L, 50.0, 50.0, 60.0, 80.0, 0L, atRisk);
        assertEquals("纯文本建议", fb.get("summary"));

        when(aiService.generateLearningAdviceJson(anyString(), anyString())).thenThrow(new RuntimeException("boom"));
        Map<?, ?> ex = (Map<?, ?>) m.invoke(dashboard, c, 1L, 50.0, 50.0, 60.0, 80.0, 0L, atRisk);
        assertTrue(ex.containsKey("summary"));
    }

    @Test
    @DisplayName("buildTeacherAIOverview: 解析成功/纯文本降级/异常降级")
    void buildTeacherAIOverview() throws Exception {
        Method m = TeacherDashboardComponent.class.getDeclaredMethod("buildTeacherAIOverview",
                long.class, long.class, double.class, long.class, List.class, double.class);
        m.setAccessible(true);
        List<Map<String, Object>> atRisk = new ArrayList<>();

        when(aiService.generateLearningAdviceJson(anyString(), anyString())).thenReturn("{\"summary\":\"ok\"}");
        assertTrue(m.invoke(dashboard, 1L, 10L, 60.0, 0L, atRisk, 0.5) instanceof Map);

        when(aiService.generateLearningAdviceJson(anyString(), anyString())).thenReturn("纯文本");
        Map<?, ?> fb = (Map<?, ?>) m.invoke(dashboard, 1L, 10L, 60.0, 0L, atRisk, 0.5);
        assertEquals("纯文本", fb.get("summary"));

        when(aiService.generateLearningAdviceJson(anyString(), anyString())).thenThrow(new RuntimeException("boom"));
        Map<?, ?> ex = (Map<?, ?>) m.invoke(dashboard, 1L, 10L, 60.0, 0L, atRisk, 0.5);
        assertTrue(ex.containsKey("summary"));
    }
}
