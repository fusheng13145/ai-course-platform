package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.LearningRecord;
import com.aicourse.entity.Question;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.User;
import com.aicourse.entity.Classroom;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.AIService;
import com.aicourse.service.ClassroomService;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.LearningRecordService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.UserService;
import com.aicourse.service.component.teacher.TeacherChapterComponent;
import com.aicourse.service.component.teacher.TeacherCourseComponent;
import com.aicourse.service.component.teacher.TeacherDashboardComponent;
import com.aicourse.service.component.teacher.TeacherOutlineComponent;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 课程教学聚合门面单元测试（B 主战场）。
 * 依赖 11 个 Service 接口（无 BaseMapper），内部以 LambdaQueryWrapper 调服务，
 * 故在 @BeforeEach 为所有内部所用实体注册 TableInfo，且 *.list(any()) 须锚定 any(Wrapper.class)。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeacherCourseServiceImplTest {

    @Mock private CourseService courseService;
    @Mock private CourseChapterService chapterService;
    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private TaskService taskService;
    @Mock private SubmissionService submissionService;
    @Mock private ExamPaperService examPaperService;
    @Mock private QuestionService questionService;
    @Mock private LearningRecordService learningRecordService;
    @Mock private AIService aiService;
    @Mock private UserService userService;
    @Mock private ClassroomService classroomService;

    private TeacherCourseServiceImpl service;

    private Course course;
    private List<CourseEnrollment> enrollments;
    private List<CourseChapter> chapters;
    private List<Task> tasks;
    private List<Submission> submissions;
    private List<LearningRecord> records;
    private List<User> users;
    private List<Classroom> classrooms;

    @BeforeEach
    void setUp() {
        // 注册内部所用实体的 lambda 元数据
        for (Class<?> e : new Class<?>[]{Course.class, User.class, CourseEnrollment.class, CourseChapter.class,
                Task.class, ExamPaper.class, Question.class, Submission.class, LearningRecord.class}) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), e);
        }

        // 原兼容构造器已移除：内联其组件组装逻辑，再走主构造器（3 个组件）
        TeacherCourseComponent courseComponent = new TeacherCourseComponent(courseService, userService);
        TeacherChapterComponent chapterComponent = new TeacherChapterComponent(chapterService, enrollmentService,
                taskService, submissionService, userService, classroomService, courseComponent);
        service = new TeacherCourseServiceImpl(
                new TeacherDashboardComponent(courseService, chapterService, enrollmentService, taskService,
                        submissionService, examPaperService, questionService, learningRecordService, aiService,
                        courseComponent, chapterComponent),
                new TeacherOutlineComponent(courseService, chapterService, aiService, courseComponent),
                chapterComponent);

        course = new Course();
        course.setId(1L);
        course.setName("Java 程序设计");
        course.setTeacherId(1L);
        course.setStatus("ACTIVE");
        course.setCode("CS101");

        CourseEnrollment e1 = new CourseEnrollment();
        e1.setCourseId(1L); e1.setStudentId(11L);
        e1.setProgress(new BigDecimal("80")); e1.setFinalScore(new BigDecimal("75"));
        e1.setApprovalStatus("APPROVED"); e1.setIsActive(Boolean.TRUE);
        CourseEnrollment e2 = new CourseEnrollment();
        e2.setCourseId(1L); e2.setStudentId(12L);
        e2.setProgress(new BigDecimal("30")); e2.setFinalScore(new BigDecimal("50"));
        e2.setApprovalStatus("PENDING"); e2.setIsActive(Boolean.FALSE);
        enrollments = List.of(e1, e2);

        CourseChapter c1 = new CourseChapter();
        c1.setId(1L); c1.setCourseId(1L); c1.setTitle("第一章"); c1.setOrderIndex(1); c1.setIsPublished(Boolean.TRUE);
        CourseChapter c2 = new CourseChapter();
        c2.setId(2L); c2.setCourseId(1L); c2.setTitle("第二章"); c2.setOrderIndex(2); c2.setParentId(1L); c2.setIsPublished(Boolean.TRUE);
        chapters = List.of(c1, c2);

        Task t1 = new Task();
        t1.setId(1L); t1.setCourseId(1L); t1.setChapterId(1L); t1.setIsPublished(Boolean.TRUE);
        t1.setTitle("任务1"); t1.setTaskType("VIDEO");
        tasks = List.of(t1);

        Submission s1 = new Submission();
        s1.setTaskId(1L); s1.setStudentId(11L); s1.setStatus("SUBMITTED"); s1.setScore(new BigDecimal("75"));
        Submission s2 = new Submission();
        s2.setTaskId(1L); s2.setStudentId(12L); s1.setStatus("GRADED"); s2.setScore(new BigDecimal("50"));
        submissions = List.of(s1, s2);

        LearningRecord r1 = new LearningRecord();
        r1.setCourseId(1L); r1.setUserId(11L); r1.setCreatedAt(LocalDateTime.now());
        records = List.of(r1);

        User u1 = new User();
        u1.setId(11L); u1.setUsername("stu11"); u1.setFullName("张三"); u1.setClassId(101L);
        User u2 = new User();
        u2.setId(12L); u2.setUsername("stu12"); u2.setFullName("李四"); u2.setClassId(101L);
        users = List.of(u1, u2);

        Classroom cr = new Classroom();
        cr.setId(101L); cr.setName("计算机1班"); cr.setCode("C1"); cr.setDescription("周一");
        classrooms = List.of(cr);
    }

    private void stubCommon() {
        when(courseService.getById(anyLong())).thenReturn(course);
        when(courseService.list(any(Wrapper.class))).thenReturn(List.of(course));
        doAnswer(inv -> { ((Course) inv.getArgument(0)).setId(99L); return true; }).when(courseService).save(any(Course.class));
        when(enrollmentService.count(any(Wrapper.class))).thenReturn(2L);
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(enrollments);
        when(chapterService.count(any(Wrapper.class))).thenReturn(2L);
        when(chapterService.list(any(Wrapper.class))).thenReturn(chapters);
        when(chapterService.save(any(CourseChapter.class))).thenReturn(true);
        when(chapterService.updateById(any(CourseChapter.class))).thenReturn(true);
        when(taskService.count(any(Wrapper.class))).thenReturn(1L);
        when(taskService.list(any(Wrapper.class))).thenReturn(tasks);
        when(examPaperService.count(any(Wrapper.class))).thenReturn(1L);
        when(questionService.count(any(Wrapper.class))).thenReturn(5L);
        when(submissionService.count(any(Wrapper.class))).thenReturn(2L);
        when(submissionService.list(any(Wrapper.class))).thenReturn(submissions);
        when(learningRecordService.list(any(Wrapper.class))).thenReturn(records);
        when(userService.list(any(Wrapper.class))).thenReturn(users);
        when(classroomService.listByIds(anyList())).thenReturn(classrooms);
        when(aiService.generateLearningAdviceJson(any(), any())).thenReturn("{\"summary\":\"OK\"}");
        when(aiService.chat(any(), any(), anyLong())).thenReturn("{\"title\":\"大纲\",\"chapters\":[]}");
    }

    @Test
    @DisplayName("教学驾驶舱-课程不存在抛异常")
    void dashboard_courseNotFound() {
        when(courseService.getById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getCourseTeachingDashboard(9L, 1L));
    }

    @Test
    @DisplayName("教学驾驶舱-正常组装指标")
    void dashboard_ok() {
        stubCommon();
        Map<String, Object> d = service.getCourseTeachingDashboard(1L, 1L);
        assertNotNull(d.get("overview"));
        assertNotNull(d.get("content"));
        assertNotNull(d.get("learning"));
        assertNotNull(d.get("trend"));
        assertNotNull(d.get("chapterTree"));
        assertNotNull(d.get("aiInsight"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> atRisk = (List<Map<String, Object>>) d.get("atRiskStudents");
        assertNotNull(atRisk);
    }

    @Test
    @DisplayName("AI 建课大纲-主题为空白抛异常")
    void outline_blankTopic() {
        assertThrows(BusinessException.class, () -> service.generateCourseOutlineByAI("  ", "通用", "入门", 6, 1L));
    }

    @Test
    @DisplayName("AI 建课大纲-正常解析返回大纲")
    void outline_ok() {
        stubCommon();
        Map<String, Object> r = service.generateCourseOutlineByAI("Java", "通用", "入门", 6, 1L);
        assertEquals(true, r.get("success"));
        assertNotNull(r.get("outline"));
    }

    @Test
    @DisplayName("AI 建课大纲-解析失败回退")
    void outline_parseFail() {
        when(aiService.chat(any(), any(), anyLong())).thenReturn("not-json");
        Map<String, Object> r = service.generateCourseOutlineByAI("Java", "通用", "入门", 6, 1L);
        assertEquals(false, r.get("success"));
        assertNotNull(r.get("raw"));
    }

    @Test
    @DisplayName("课程克隆-源课程不存在抛异常")
    void clone_notFound() {
        when(courseService.getById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.cloneCourse(9L, "新名称", 1L));
    }

    @Test
    @DisplayName("课程克隆-正常复制章节并修正父子关系")
    void clone_ok() {
        stubCommon();
        // 第二次 chapterService.list 返回带新ID的章节（用于父子修正）
        when(chapterService.list(any(Wrapper.class))).thenReturn(chapters)
                .thenReturn(List.of(chapters.get(0), chapters.get(1)));
        Map<String, Object> r = service.cloneCourse(1L, "副本课程", 1L);
        assertNotNull(r.get("newCourseId"));
        assertEquals(2, r.get("clonedChapters"));
    }

    @Test
    @DisplayName("我的课程聚合-正常汇总")
    void myCourseSummaries_ok() {
        stubCommon();
        List<Map<String, Object>> list = service.getMyCourseSummaries(1L);
        assertEquals(1, list.size());
        assertEquals("Java 程序设计", list.get(0).get("name"));
    }

    @Test
    @DisplayName("教师仪表盘-正常组装")
    void dashboardOverview_ok() {
        stubCommon();
        Map<String, Object> d = service.getTeacherDashboard(1L);
        assertNotNull(d.get("courseCount"));
        assertNotNull(d.get("courseCards"));
        assertNotNull(d.get("atRiskStudents"));
        assertNotNull(d.get("taskPointOverview"));
        assertNotNull(d.get("platformComparison"));
        // ⑯：主聚合不再同步调 LLM，AI 总览改由独立端点补数
        assertFalse(d.containsKey("aiOverview"));
        verify(aiService, never()).generateLearningAdviceJson(any(), any());
    }

    @Test
    @DisplayName("教师 AI 总览-独立端点复用统计聚合")
    void teacherAiOverview_ok() {
        stubCommon();
        Map<String, Object> ai = service.getTeacherAIOverview(1L);
        assertTrue(String.valueOf(ai.get("summary")).contains("OK"));
    }

    @Test
    @DisplayName("教师 AI 总览-模型异常返回兜底文案")
    void teacherAiOverview_fallbackOnError() {
        stubCommon();
        when(aiService.generateLearningAdviceJson(any(), any())).thenThrow(new RuntimeException("boom"));
        Map<String, Object> ai = service.getTeacherAIOverview(1L);
        assertNotNull(ai.get("summary"));
    }

    @Test
    @DisplayName("教师 AI 总览-独立入口复用同一统计口径并返回模型结果（B-13 ⑯）")
    void teacherAiOverview_reusesAggregationAndCallsModel() {
        stubCommon();
        when(aiService.generateLearningAdviceJson(any(), any()))
                .thenReturn("{\"summary\":\"本周两门课进度正常\"}");

        Map<String, Object> overview = service.getTeacherAIOverview(1L);

        assertEquals("本周两门课进度正常", overview.get("summary"));
        verify(aiService).generateLearningAdviceJson(any(), any());
        // 聚合里的 deferred 标记不得混进 AI 总览的返回
        assertEquals(null, overview.get("deferred"));
    }

    @Test
    @DisplayName("教师仪表盘-无课程空集合")
    void dashboardOverview_noCourses() {
        when(courseService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        Map<String, Object> d = service.getTeacherDashboard(1L);
        assertEquals(0L, d.get("courseCount"));
        assertEquals(0L, d.get("studentCount"));
    }

    @Test
    @DisplayName("完整章节树-父子组装与提交统计")
    void chapterTree_ok() {
        stubCommon();
        List<Map<String, Object>> tree = service.getFullChapterTree(1L);
        assertEquals(1, tree.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) tree.get(0).get("children");
        assertEquals(1, children.size());
    }

    @Test
    @DisplayName("课程关联班级-按班级聚合学生与待批改")
    void courseClasses_ok() {
        stubCommon();
        List<Map<String, Object>> list = service.getCourseClasses(1L);
        assertEquals(1, list.size());
        assertEquals("计算机1班", list.get(0).get("name"));
        assertTrue((Integer) list.get(0).get("students") >= 1);
    }

    @Test
    @DisplayName("预警学生计算-低进度/低成绩被标记")
    void computeAtRisk_ok() {
        stubCommon();
        List<Map<String, Object>> r = service.computeAtRisk(enrollments);
        // e2 进度30<40 且 成绩50<60 → HIGH；e1 均达标 → 不在列表
        assertEquals(1, r.size());
        assertEquals("HIGH", r.get(0).get("level"));
    }
}
