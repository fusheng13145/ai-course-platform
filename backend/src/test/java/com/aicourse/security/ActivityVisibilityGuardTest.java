package com.aicourse.security;

import com.aicourse.common.ActivityVisibility;
import com.aicourse.common.ApiCode;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.Task;
import com.aicourse.entity.TeachingClass;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamTeachingClassMapper;
import com.aicourse.mapper.TaskTeachingClassMapper;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link ActivityVisibilityGuard} 单测（V15 / WP6 验收第 3、4 条）。
 */
@ExtendWith(MockitoExtension.class)
class ActivityVisibilityGuardTest {

    @Mock
    private CourseService courseService;
    @Mock
    private CourseEnrollmentService enrollmentService;
    @Mock
    private TeachingClassMapper teachingClassMapper;
    @Mock
    private TaskTeachingClassMapper taskTeachingClassMapper;
    @Mock
    private ExamTeachingClassMapper examTeachingClassMapper;
    @InjectMocks
    private ActivityVisibilityGuard guard;

    private Authentication auth(long userId, String... roles) {
        return new UsernamePasswordAuthenticationToken(userId, null,
                Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    private Course course(long id, long teacherId) {
        Course course = new Course();
        course.setId(id);
        course.setTeacherId(teacherId);
        return course;
    }

    private TeachingClass teachingClass(long id, long courseId, long leadTeacherId) {
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(id);
        teachingClass.setCourseId(courseId);
        teachingClass.setLeadTeacherId(leadTeacherId);
        return teachingClass;
    }

    private Task task(String visibility) {
        Task task = new Task();
        task.setId(100L);
        task.setCourseId(1L);
        task.setStatus("PUBLISHED");
        task.setVisibility(visibility);
        return task;
    }

    private CourseEnrollment enrollment(long courseId, long teachingClassId) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourseId(courseId);
        enrollment.setTeachingClassId(teachingClassId);
        enrollment.setApprovalStatus("APPROVED");
        enrollment.setIsActive(true);
        return enrollment;
    }

    @Test
    @DisplayName("课程负责人始终可见本课程 CLASSES 活动")
    void owner_can_see_classes_task() {
        when(courseService.getById(1L)).thenReturn(course(1L, 3L));
        guard.assertVisibleTask(task(ActivityVisibility.CLASSES), auth(3L, "TEACHER"));
    }

    @Test
    @DisplayName("同课程非目标教学班教师看不到 CLASSES 活动")
    void non_target_class_teacher_cannot_see_task() {
        when(courseService.getById(1L)).thenReturn(course(1L, 2L));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(teachingClass(11L, 1L, 3L)));
        when(taskTeachingClassMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.assertVisibleTask(task(ActivityVisibility.CLASSES), auth(3L, "TEACHER")));
        assertEquals(ApiCode.FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("目标教学班教师可以看到 CLASSES 活动")
    void target_class_teacher_can_see_task() {
        when(courseService.getById(1L)).thenReturn(course(1L, 2L));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(teachingClass(12L, 1L, 3L)));
        when(taskTeachingClassMapper.selectCount(any())).thenReturn(1L);

        guard.assertVisibleTask(task(ActivityVisibility.CLASSES), auth(3L, "TEACHER"));
    }

    @Test
    @DisplayName("非负责人但主讲本课程任一教学班，可见 COURSE 级活动")
    void lead_teacher_can_see_course_scope_task() {
        when(courseService.getById(1L)).thenReturn(course(1L, 2L));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(teachingClass(11L, 1L, 3L)));

        guard.assertVisibleTask(task(ActivityVisibility.COURSE), auth(3L, "TEACHER"));
    }

    @Test
    @DisplayName("目标教学班学生可见 CLASSES 活动")
    void target_class_student_can_see_task() {
        when(enrollmentService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(enrollment(1L, 11L)));
        when(taskTeachingClassMapper.selectCount(any())).thenReturn(1L);

        guard.assertVisibleTask(task(ActivityVisibility.CLASSES), auth(9L, "STUDENT"));
    }

    @Test
    @DisplayName("非目标教学班学生看不到 CLASSES 活动")
    void non_target_student_cannot_see_task() {
        when(enrollmentService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(enrollment(1L, 19L)));
        when(taskTeachingClassMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.assertVisibleTask(task(ActivityVisibility.CLASSES), auth(9L, "STUDENT")));
        assertEquals(ApiCode.FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("学生未加入课程时看不到活动")
    void student_without_enrollment_cannot_see_task() {
        when(enrollmentService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.assertVisibleTask(task(ActivityVisibility.COURSE), auth(9L, "STUDENT")));
        assertEquals(ApiCode.FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("管理员调用发布/改目标返回 403")
    void admin_is_rejected_for_write_actions() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.assertNotAdmin(auth(1L, "ADMIN")));
        assertEquals(ApiCode.FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("试卷可见性口径与任务一致：目标班教师可见")
    void target_class_teacher_can_see_exam_paper() {
        ExamPaper paper = new ExamPaper();
        paper.setId(200L);
        paper.setCourseId(1L);
        paper.setVisibility(ActivityVisibility.CLASSES);
        when(courseService.getById(1L)).thenReturn(course(1L, 2L));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(teachingClass(12L, 1L, 3L)));
        when(examTeachingClassMapper.selectCount(any())).thenReturn(1L);

        guard.assertVisibleExamPaper(paper, auth(3L, "TEACHER"));
    }
}
