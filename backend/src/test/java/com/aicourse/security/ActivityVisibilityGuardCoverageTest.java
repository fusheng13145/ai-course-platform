package com.aicourse.security;

import com.aicourse.common.ActivityVisibility;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamTeachingClass;
import com.aicourse.entity.Task;
import com.aicourse.entity.TaskTeachingClass;
import com.aicourse.entity.TeachingClass;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamTeachingClassMapper;
import com.aicourse.mapper.TaskTeachingClassMapper;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ActivityVisibilityGuard 覆盖率补测（I-04 第七批）。
 *
 * <p>覆盖任务/试卷可见性、管理员只读、学生选课与教学班范围、
 * 教师负责课程与主讲班组合、列表作用域的管理员/学生/教师分支。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivityVisibilityGuardCoverageTest {

    @Mock private CourseService courseService;
    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private TeachingClassMapper teachingClassMapper;
    @Mock private TaskTeachingClassMapper taskTeachingClassMapper;
    @Mock private ExamTeachingClassMapper examTeachingClassMapper;
    @Mock private AdminScopeGuard adminScopeGuard;
    @Mock private CourseAccessControl courseAccessControl;

    private ActivityVisibilityGuard guard;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> entity : List.of(Task.class, ExamPaper.class, Course.class,
                CourseEnrollment.class, TeachingClass.class, TaskTeachingClass.class,
                ExamTeachingClass.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @BeforeEach
    void setUp() {
        guard = new ActivityVisibilityGuard(courseService, enrollmentService, teachingClassMapper,
                taskTeachingClassMapper, examTeachingClassMapper, adminScopeGuard, courseAccessControl);
    }

    private Authentication auth(Long id, String... roles) {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getPrincipal()).thenReturn(id);
        lenient().doReturn(List.of(roles).stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList())
                .when(auth).getAuthorities();
        return auth;
    }

    private Course course(Long id, Long teacherId) {
        Course c = new Course();
        c.setId(id);
        c.setTeacherId(teacherId);
        return c;
    }

    private Task task(Long id, Long courseId, String status, Boolean published, String visibility) {
        Task t = new Task();
        t.setId(id);
        t.setCourseId(courseId);
        t.setStatus(status);
        t.setIsPublished(published);
        t.setVisibility(visibility);
        return t;
    }

    private ExamPaper paper(Long id, Long courseId, String visibility) {
        ExamPaper p = new ExamPaper();
        p.setId(id);
        p.setCourseId(courseId);
        p.setVisibility(visibility);
        return p;
    }

    private TeachingClass tc(Long id, Long courseId, Long leadId) {
        TeachingClass t = new TeachingClass();
        t.setId(id);
        t.setCourseId(courseId);
        t.setLeadTeacherId(leadId);
        return t;
    }

    private CourseEnrollment enrollment(Long courseId, Long classId) {
        CourseEnrollment e = new CourseEnrollment();
        e.setCourseId(courseId);
        e.setTeachingClassId(classId);
        return e;
    }

    @Test
    @DisplayName("assertNotAdmin：管理员 403，教师/学生放行")
    void notAdmin() {
        assertThrows(BusinessException.class, () -> guard.assertNotAdmin(auth(1L, RoleConstants.ADMIN)));
        assertDoesNotThrow(() -> guard.assertNotAdmin(auth(2L, RoleConstants.TEACHER)));
        assertDoesNotThrow(() -> guard.assertNotAdmin(auth(3L, RoleConstants.STUDENT)));
        assertDoesNotThrow(() -> guard.assertNotAdmin(null));
    }

    @Test
    @DisplayName("assertVisibleTask：空、管理员、学生未发布/未选课/班级未命中/命中")
    void visibleTask_student() {
        assertThrows(BusinessException.class, () -> guard.assertVisibleTask(null, auth(1L, RoleConstants.STUDENT)));

        Task t = task(1L, 1L, "DRAFT", false, ActivityVisibility.COURSE);
        when(courseService.getById(1L)).thenReturn(course(1L, 9L));
        assertDoesNotThrow(() -> guard.assertVisibleTask(t, auth(99L, RoleConstants.ADMIN)));
        verify(adminScopeGuard).assertCourseAccess(any(Course.class));

        assertThrows(BusinessException.class, () -> guard.assertVisibleTask(t, auth(2L, RoleConstants.STUDENT)));

        Task published = task(2L, 1L, "PUBLISHED", null, ActivityVisibility.COURSE);
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> guard.assertVisibleTask(published, auth(2L, RoleConstants.STUDENT)));

        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(enrollment(1L, 7L)));
        assertDoesNotThrow(() -> guard.assertVisibleTask(published, auth(2L, RoleConstants.STUDENT)));

        Task classTask = task(3L, 1L, "PUBLISHED", null, ActivityVisibility.CLASSES);
        assertThrows(BusinessException.class, () -> guard.assertVisibleTask(classTask, auth(2L, RoleConstants.STUDENT)));
        when(taskTeachingClassMapper.selectCount(any())).thenReturn(1L);
        assertDoesNotThrow(() -> guard.assertVisibleTask(classTask, auth(2L, RoleConstants.STUDENT)));
    }

    @Test
    @DisplayName("assertVisibleTask：教师负责人、主讲班 CLASSES 命中/未命中、COURSE 有主讲班")
    void visibleTask_teacher() {
        when(courseService.getById(1L)).thenReturn(course(1L, 9L));

        Task owned = task(1L, 1L, "DRAFT", false, ActivityVisibility.COURSE);
        assertDoesNotThrow(() -> guard.assertVisibleTask(owned, auth(9L, RoleConstants.TEACHER)));

        Task classTask = task(2L, 1L, "PUBLISHED", null, ActivityVisibility.CLASSES);
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(tc(7L, 1L, 5L)));
        when(taskTeachingClassMapper.selectCount(any())).thenReturn(0L);
        assertThrows(BusinessException.class, () -> guard.assertVisibleTask(classTask, auth(5L, RoleConstants.TEACHER)));
        when(taskTeachingClassMapper.selectCount(any())).thenReturn(1L);
        assertDoesNotThrow(() -> guard.assertVisibleTask(classTask, auth(5L, RoleConstants.TEACHER)));

        Task courseTask = task(3L, 1L, "PUBLISHED", null, ActivityVisibility.COURSE);
        assertDoesNotThrow(() -> guard.assertVisibleTask(courseTask, auth(5L, RoleConstants.TEACHER)));

        when(teachingClassMapper.selectList(any())).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> guard.assertVisibleTask(courseTask, auth(6L, RoleConstants.TEACHER)));
    }

    @Test
    @DisplayName("assertVisibleExamPaper：空、管理员、学生未选课/班级未命中/命中、教师负责人/主讲班")
    void visibleExamPaper() {
        assertThrows(BusinessException.class, () -> guard.assertVisibleExamPaper(null, auth(1L, RoleConstants.STUDENT)));

        ExamPaper p = paper(1L, 1L, ActivityVisibility.COURSE);
        when(courseService.getById(1L)).thenReturn(course(1L, 9L));
        assertDoesNotThrow(() -> guard.assertVisibleExamPaper(p, auth(99L, RoleConstants.ADMIN)));

        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> guard.assertVisibleExamPaper(p, auth(2L, RoleConstants.STUDENT)));

        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(enrollment(1L, 7L)));
        assertDoesNotThrow(() -> guard.assertVisibleExamPaper(p, auth(2L, RoleConstants.STUDENT)));

        ExamPaper classPaper = paper(2L, 1L, ActivityVisibility.CLASSES);
        when(examTeachingClassMapper.selectCount(any())).thenReturn(0L);
        assertThrows(BusinessException.class, () -> guard.assertVisibleExamPaper(classPaper, auth(2L, RoleConstants.STUDENT)));
        when(examTeachingClassMapper.selectCount(any())).thenReturn(1L);
        assertDoesNotThrow(() -> guard.assertVisibleExamPaper(classPaper, auth(2L, RoleConstants.STUDENT)));

        assertDoesNotThrow(() -> guard.assertVisibleExamPaper(classPaper, auth(9L, RoleConstants.TEACHER)));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(tc(7L, 1L, 5L)));
        when(examTeachingClassMapper.selectCount(any())).thenReturn(1L);
        assertDoesNotThrow(() -> guard.assertVisibleExamPaper(classPaper, auth(5L, RoleConstants.TEACHER)));
        assertDoesNotThrow(() -> guard.assertVisibleExamPaper(p, auth(5L, RoleConstants.TEACHER)));
    }

    @Test
    @DisplayName("applyTaskScope：管理员无班/有班、学生有班/无班/空选课/空课程、教师三类")
    void taskScope() {
        when(courseAccessControl.readableCourseIds(any())).thenReturn(List.of(1L));
        when(teachingClassMapper.selectById(7L)).thenReturn(tc(7L, 1L, 5L));
        when(courseService.getById(1L)).thenReturn(course(1L, 9L));

        assertDoesNotThrow(() -> guard.applyTaskScope(new LambdaQueryWrapper<>(), auth(99L, RoleConstants.ADMIN), null));
        assertDoesNotThrow(() -> guard.applyTaskScope(new LambdaQueryWrapper<>(), auth(99L, RoleConstants.ADMIN), 7L));

        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(enrollment(1L, 7L)));
        assertDoesNotThrow(() -> guard.applyTaskScope(new LambdaQueryWrapper<>(), auth(2L, RoleConstants.STUDENT), 7L));

        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of());
        assertDoesNotThrow(() -> guard.applyTaskScope(new LambdaQueryWrapper<>(), auth(2L, RoleConstants.STUDENT), null));

        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(enrollment(null, null)));
        assertDoesNotThrow(() -> guard.applyTaskScope(new LambdaQueryWrapper<>(), auth(2L, RoleConstants.STUDENT), null));

        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(enrollment(1L, 7L)));
        assertDoesNotThrow(() -> guard.applyTaskScope(new LambdaQueryWrapper<>(), auth(2L, RoleConstants.STUDENT), null));

        when(courseService.list(any(Wrapper.class))).thenReturn(List.of());
        when(teachingClassMapper.selectList(any())).thenReturn(List.of());
        assertDoesNotThrow(() -> guard.applyTaskScope(new LambdaQueryWrapper<>(), auth(5L, RoleConstants.TEACHER), null));

        when(courseService.list(any(Wrapper.class))).thenReturn(List.of(course(1L, 5L)));
        assertDoesNotThrow(() -> guard.applyTaskScope(new LambdaQueryWrapper<>(), auth(5L, RoleConstants.TEACHER), null));

        when(courseService.list(any(Wrapper.class))).thenReturn(List.of());
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(tc(7L, 1L, 5L)));
        assertDoesNotThrow(() -> guard.applyTaskScope(new LambdaQueryWrapper<>(), auth(5L, RoleConstants.TEACHER), null));

        when(courseService.list(any(Wrapper.class))).thenReturn(List.of(course(1L, 5L)));
        assertDoesNotThrow(() -> guard.applyTaskScope(new LambdaQueryWrapper<>(), auth(5L, RoleConstants.TEACHER), null));
    }

    @Test
    @DisplayName("applyExamPaperScope：管理员无班/有班、学生空选课/空课程/正常、教师三类")
    void examScope() {
        when(courseAccessControl.readableCourseIds(any())).thenReturn(null);
        when(teachingClassMapper.selectById(7L)).thenReturn(tc(7L, 1L, 5L));
        when(courseService.getById(1L)).thenReturn(course(1L, 9L));

        assertDoesNotThrow(() -> guard.applyExamPaperScope(new LambdaQueryWrapper<>(), auth(99L, RoleConstants.ADMIN), null));
        assertDoesNotThrow(() -> guard.applyExamPaperScope(new LambdaQueryWrapper<>(), auth(99L, RoleConstants.ADMIN), 7L));

        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(enrollment(1L, 7L)));
        assertDoesNotThrow(() -> guard.applyExamPaperScope(new LambdaQueryWrapper<>(), auth(2L, RoleConstants.STUDENT), 7L));

        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of());
        assertDoesNotThrow(() -> guard.applyExamPaperScope(new LambdaQueryWrapper<>(), auth(2L, RoleConstants.STUDENT), null));
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(enrollment(null, null)));
        assertDoesNotThrow(() -> guard.applyExamPaperScope(new LambdaQueryWrapper<>(), auth(2L, RoleConstants.STUDENT), null));
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(enrollment(1L, 7L)));
        assertDoesNotThrow(() -> guard.applyExamPaperScope(new LambdaQueryWrapper<>(), auth(2L, RoleConstants.STUDENT), null));

        when(courseService.list(any(Wrapper.class))).thenReturn(List.of());
        when(teachingClassMapper.selectList(any())).thenReturn(List.of());
        assertDoesNotThrow(() -> guard.applyExamPaperScope(new LambdaQueryWrapper<>(), auth(5L, RoleConstants.TEACHER), null));
        when(courseService.list(any(Wrapper.class))).thenReturn(List.of(course(1L, 5L)));
        assertDoesNotThrow(() -> guard.applyExamPaperScope(new LambdaQueryWrapper<>(), auth(5L, RoleConstants.TEACHER), null));
        when(courseService.list(any(Wrapper.class))).thenReturn(List.of());
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(tc(7L, 1L, 5L)));
        assertDoesNotThrow(() -> guard.applyExamPaperScope(new LambdaQueryWrapper<>(), auth(5L, RoleConstants.TEACHER), null));
        when(courseService.list(any(Wrapper.class))).thenReturn(List.of(course(1L, 5L)));
        assertDoesNotThrow(() -> guard.applyExamPaperScope(new LambdaQueryWrapper<>(), auth(5L, RoleConstants.TEACHER), null));
    }

    @Test
    @DisplayName("applyTaskScope 指定教学班：学生不在班 403、教师负责人/主讲/其他")
    void classViewer() {
        when(teachingClassMapper.selectById(7L)).thenReturn(tc(7L, 1L, 5L));
        when(courseService.getById(1L)).thenReturn(course(1L, 9L));

        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> guard.applyTaskScope(new LambdaQueryWrapper<>(),
                auth(2L, RoleConstants.STUDENT), 7L));

        assertDoesNotThrow(() -> guard.applyTaskScope(new LambdaQueryWrapper<>(),
                auth(9L, RoleConstants.TEACHER), 7L));
        assertDoesNotThrow(() -> guard.applyTaskScope(new LambdaQueryWrapper<>(),
                auth(5L, RoleConstants.TEACHER), 7L));
        assertThrows(BusinessException.class, () -> guard.applyTaskScope(new LambdaQueryWrapper<>(),
                auth(6L, RoleConstants.TEACHER), 7L));

        when(teachingClassMapper.selectById(8L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> guard.applyTaskScope(new LambdaQueryWrapper<>(),
                auth(2L, RoleConstants.STUDENT), 8L));
    }
}
