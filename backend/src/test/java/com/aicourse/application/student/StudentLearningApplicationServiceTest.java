package com.aicourse.application.student;

import com.aicourse.common.ApiCode;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.TeachingClass;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.TeachingClassService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.vo.CourseDetailVO;
import com.aicourse.vo.TeachingClassOptionVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 3E-05B：{@code StudentLearningApplicationService} 用例。
 *
 * <p>重点锁三条口径：①「有效选课」= 启用 + 审批通过，且空集合时不去查课程；②待办判定按
 * 「最新一次提交」（attemptNumber 大者为准，RETURNED 仍待办）；③教学班可选性由课程审批状态
 * 与课程状态双闸门决定。</p>
 */
@ExtendWith(MockitoExtension.class)
class StudentLearningApplicationServiceTest {

    @Mock
    private CourseService courseService;
    @Mock
    private CourseEnrollmentService enrollmentService;
    @Mock
    private TaskService taskService;
    @Mock
    private SubmissionService submissionService;
    @Mock
    private TeachingClassService teachingClassService;
    @Mock
    private WrongQuestionService wrongQuestionService;

    @InjectMocks
    private StudentLearningApplicationService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Course.class);
        TableInfoHelper.initTableInfo(assistant, CourseEnrollment.class);
        TableInfoHelper.initTableInfo(assistant, Task.class);
        TableInfoHelper.initTableInfo(assistant, Submission.class);
        TableInfoHelper.initTableInfo(assistant, WrongQuestion.class);
    }

    private static CourseEnrollment enrollment(Long courseId) {
        CourseEnrollment e = new CourseEnrollment();
        e.setCourseId(courseId);
        e.setStudentId(1L);
        e.setIsActive(true);
        e.setApprovalStatus("APPROVED");
        return e;
    }

    private static Task task(Long id, Long courseId) {
        Task t = new Task();
        t.setId(id);
        t.setCourseId(courseId);
        t.setStatus("PUBLISHED");
        return t;
    }

    private static Submission submission(Long taskId, int attempt, String status) {
        Submission s = new Submission();
        s.setTaskId(taskId);
        s.setStudentId(1L);
        s.setAttemptNumber(attempt);
        s.setStatus(status);
        return s;
    }

    // ==================== 我的课程 ====================

    @Test
    @DisplayName("我的课程-无有效选课：返回空分页且不查课程")
    void myCoursesWithoutEnrollments() {
        when(enrollmentService.list(ArgumentMatchers.<LambdaQueryWrapper<CourseEnrollment>>any()))
                .thenReturn(List.of());

        PagedResponse<?> result = service.myCourses(1L, 1, 10);

        assertTrue(result.getList().isEmpty());
        assertEquals(0L, result.getTotal());
        verify(courseService, never()).page(any(), any());
    }

    @Test
    @DisplayName("我的课程-有有效选课：按课程ID集合分页并转 VO")
    void myCoursesWithEnrollments() {
        when(enrollmentService.list(ArgumentMatchers.<LambdaQueryWrapper<CourseEnrollment>>any()))
                .thenReturn(List.of(enrollment(5L), enrollment(6L)));
        Page<Course> page = new Page<>(2, 3);
        Course c = new Course();
        c.setId(5L);
        c.setName("高等数学");
        page.setRecords(List.of(c));
        page.setTotal(1);
        when(courseService.page(any(), any())).thenReturn(page);

        PagedResponse<?> result = service.myCourses(1L, 2, 3);

        assertEquals(1, result.getList().size());
        assertEquals(1L, result.getTotal());
        assertEquals(2L, result.getPage());
        assertEquals(3L, result.getPageSize());
    }

    // ==================== 课程详情 ====================

    @Test
    @DisplayName("课程详情-课程不存在返回 404（语义码修正，原为 400）")
    void courseDetailNotFound() {
        when(courseService.getById(9L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.courseDetail(1L, 9L));

        assertEquals(ApiCode.NOT_FOUND, ex.getCode());
    }

    @Test
    @DisplayName("课程详情-返回课程实体与本人选课记录")
    void courseDetailWithEnrollment() {
        Course course = new Course();
        course.setId(9L);
        when(courseService.getById(9L)).thenReturn(course);
        when(enrollmentService.getOne(ArgumentMatchers.<LambdaQueryWrapper<CourseEnrollment>>any()))
                .thenReturn(enrollment(9L));

        CourseDetailVO vo = service.courseDetail(1L, 9L);

        assertNotNull(vo.getCourse());
        assertNotNull(vo.getEnrollment());
        assertEquals(9L, vo.getEnrollment().getCourseId());
    }

    @Test
    @DisplayName("课程详情-未选课时 enrollment 为 null（不报错）")
    void courseDetailWithoutEnrollment() {
        Course course = new Course();
        course.setId(9L);
        when(courseService.getById(9L)).thenReturn(course);
        when(enrollmentService.getOne(ArgumentMatchers.<LambdaQueryWrapper<CourseEnrollment>>any()))
                .thenReturn(null);

        CourseDetailVO vo = service.courseDetail(1L, 9L);

        assertNull(vo.getEnrollment());
    }

    // ==================== 可选教学班 ====================

    @Test
    @DisplayName("可选教学班-只返回 ACTIVE 的班级，字段逐一映射")
    void availableTeachingClassesFiltersActive() {
        Course course = new Course();
        course.setId(3L);
        course.setApprovalStatus("APPROVED");
        course.setStatus("ACTIVE");
        when(courseService.getById(3L)).thenReturn(course);

        TeachingClass active = new TeachingClass();
        active.setId(11L);
        active.setName("一班");
        active.setCode("C1");
        active.setCapacity(40);
        active.setStatus("ACTIVE");
        TeachingClass draft = new TeachingClass();
        draft.setId(12L);
        draft.setStatus("DRAFT");
        when(teachingClassService.listByCourse(3L)).thenReturn(List.of(active, draft));

        List<TeachingClassOptionVO> options = service.availableTeachingClasses(3L);

        assertEquals(1, options.size());
        assertEquals(11L, options.get(0).getId());
        assertEquals("一班", options.get(0).getName());
        assertEquals("C1", options.get(0).getCode());
        assertEquals(40, options.get(0).getCapacity());
    }

    @Test
    @DisplayName("可选教学班-课程未过审 400")
    void availableTeachingClassesNotApproved() {
        Course course = new Course();
        course.setId(3L);
        course.setApprovalStatus("PENDING");
        when(courseService.getById(3L)).thenReturn(course);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.availableTeachingClasses(3L));

        assertEquals(ApiCode.BAD_REQUEST, ex.getCode());
        verify(teachingClassService, never()).listByCourse(anyLong());
    }

    @Test
    @DisplayName("可选教学班-课程已结束 400；课程不存在 404")
    void availableTeachingClassesEndedOrMissing() {
        Course ended = new Course();
        ended.setId(4L);
        ended.setApprovalStatus("APPROVED");
        ended.setStatus("FINISHED");
        when(courseService.getById(4L)).thenReturn(ended);
        assertThrows(BusinessException.class, () -> service.availableTeachingClasses(4L));

        when(courseService.getById(8L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.availableTeachingClasses(8L));
        assertEquals(ApiCode.NOT_FOUND, ex.getCode());
    }

    // ==================== 待完成作业 ====================

    @Test
    @DisplayName("待办-无有效选课：直接空列表，不查任务")
    void pendingWithoutEnrollments() {
        when(enrollmentService.list(ArgumentMatchers.<LambdaQueryWrapper<CourseEnrollment>>any()))
                .thenReturn(List.of());

        assertTrue(service.pendingAssignments(1L, null).isEmpty());
        verify(taskService, never()).list(ArgumentMatchers.<LambdaQueryWrapper<Task>>any());
        verify(submissionService, never()).list(ArgumentMatchers.<LambdaQueryWrapper<Submission>>any());
    }

    @Test
    @DisplayName("待办-无提交的作业在列；最新为 RETURNED 仍在列；最新为 GRADED 剔除")
    void pendingKeepsReturnedAndDropsGraded() {
        when(enrollmentService.list(ArgumentMatchers.<LambdaQueryWrapper<CourseEnrollment>>any()))
                .thenReturn(List.of(enrollment(3L)));
        when(taskService.list(ArgumentMatchers.<LambdaQueryWrapper<Task>>any()))
                .thenReturn(List.of(task(1L, 3L), task(2L, 3L), task(3L, 3L)));
        when(submissionService.list(ArgumentMatchers.<LambdaQueryWrapper<Submission>>any()))
                .thenReturn(List.of(
                        submission(2L, 1, "GRADED"),
                        submission(2L, 2, "RETURNED"),
                        submission(3L, 1, "GRADED")));

        List<?> pending = service.pendingAssignments(1L, null);

        assertEquals(2, pending.size());
        verify(taskService).list(ArgumentMatchers.<LambdaQueryWrapper<Task>>any());
    }

    @Test
    @DisplayName("待办-同一作业多次提交按 attemptNumber 判最新（后提交覆盖先前记录）")
    void pendingUsesLatestAttempt() {
        when(enrollmentService.list(ArgumentMatchers.<LambdaQueryWrapper<CourseEnrollment>>any()))
                .thenReturn(List.of(enrollment(3L)));
        when(taskService.list(ArgumentMatchers.<LambdaQueryWrapper<Task>>any()))
                .thenReturn(List.of(task(2L, 3L)));
        when(submissionService.list(ArgumentMatchers.<LambdaQueryWrapper<Submission>>any()))
                .thenReturn(List.of(
                        submission(2L, 2, "SUBMITTED"),
                        submission(2L, 1, "RETURNED")));

        assertTrue(service.pendingAssignments(1L, null).isEmpty());
    }

    @Test
    @DisplayName("待办-无任务时不再查提交（省一次往返）")
    void pendingWithoutTasksSkipsSubmissionQuery() {
        when(enrollmentService.list(ArgumentMatchers.<LambdaQueryWrapper<CourseEnrollment>>any()))
                .thenReturn(List.of(enrollment(3L)));
        when(taskService.list(ArgumentMatchers.<LambdaQueryWrapper<Task>>any())).thenReturn(List.of());

        assertTrue(service.pendingAssignments(1L, null).isEmpty());
        verify(submissionService, never()).list(ArgumentMatchers.<LambdaQueryWrapper<Submission>>any());
    }

    // ==================== 已完成作业 ====================

    @Test
    @DisplayName("已完成-指定课程时按作业归属课程过滤，且不返回其他课程提交")
    void completedFiltersByCourse() {
        when(submissionService.list(ArgumentMatchers.<LambdaQueryWrapper<Submission>>any()))
                .thenReturn(List.of(submission(1L, 1, "GRADED"), submission(2L, 1, "SUBMITTED")));
        when(taskService.listByIds(ArgumentMatchers.<java.util.Collection<Long>>any()))
                .thenReturn(List.of(task(1L, 3L), task(2L, 7L)));

        List<?> completed = service.completedAssignments(1L, 3L);

        assertEquals(1, completed.size());
        verify(taskService).listByIds(ArgumentMatchers.<java.util.Collection<Long>>any());
    }

    @Test
    @DisplayName("已完成-未指定课程：直接返回全部提交，不查任务归属")
    void completedWithoutCourseFilter() {
        when(submissionService.list(ArgumentMatchers.<LambdaQueryWrapper<Submission>>any()))
                .thenReturn(List.of(submission(1L, 1, "GRADED")));

        assertEquals(1, service.completedAssignments(1L, null).size());
        verify(taskService, never()).listByIds(ArgumentMatchers.<java.util.Collection<Long>>any());
    }

    // ==================== 错题本分页 ====================

    @Test
    @DisplayName("错题本分页-转 VO 并透传分页元信息")
    void wrongQuestionPageConversion() {
        Page<WrongQuestion> page = new Page<>(1, 10);
        WrongQuestion wq = new WrongQuestion();
        wq.setId(1L);
        wq.setQuestionId(2L);
        page.setRecords(List.of(wq));
        page.setTotal(1);
        when(wrongQuestionService.getWrongQuestionBook(eq(1L), eq(null), anyInt(), anyInt())).thenReturn(page);

        PagedResponse<?> result = service.wrongQuestionPage(1L, null, 1, 10);

        assertEquals(1, result.getList().size());
        assertEquals(1L, result.getTotal());
        verify(wrongQuestionService).getWrongQuestionBook(eq(1L), eq(null), anyInt(), anyInt());
    }
}
