package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseAccessControlTest {

    @Mock
    private CourseService courseService;
    @Mock
    private CourseEnrollmentService enrollmentService;
    @InjectMocks
    private CourseAccessControl accessControl;

    private Authentication auth(long userId, String... roles) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    private Course course(long id, long teacherId) {
        Course course = new Course();
        course.setId(id);
        course.setTeacherId(teacherId);
        return course;
    }

    private Task task(long courseId, String status) {
        Task task = new Task();
        task.setId(1L);
        task.setCourseId(courseId);
        task.setStatus(status);
        return task;
    }

    private CourseEnrollment enrollment(long courseId) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourseId(courseId);
        return enrollment;
    }

    @Test
    void admin_can_manage_any_course_and_global_content() {
        accessControl.assertCanManageCourse(99L, auth(1L, "ADMIN"));
        accessControl.assertCanManageCourse(null, auth(1L, "ADMIN"));
    }

    @Test
    void owning_teacher_can_manage_course() {
        when(courseService.getById(1L)).thenReturn(course(1L, 7L));
        accessControl.assertCanManageCourse(1L, auth(7L, "TEACHER"));
    }

    @Test
    void other_teacher_cannot_manage_course() {
        when(courseService.getById(1L)).thenReturn(course(1L, 7L));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> accessControl.assertCanManageCourse(1L, auth(8L, "TEACHER")));
        assertEquals(ApiCode.FORBIDDEN, ex.getCode());
    }

    @Test
    void student_cannot_manage_course() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> accessControl.assertCanManageCourse(1L, auth(9L, "STUDENT")));
        assertEquals(ApiCode.FORBIDDEN, ex.getCode());
    }

    @Test
    void teacher_cannot_manage_null_or_missing_course() {
        BusinessException nullEx = assertThrows(BusinessException.class,
                () -> accessControl.assertCanManageCourse(null, auth(7L, "TEACHER")));
        assertEquals(ApiCode.BAD_REQUEST, nullEx.getCode());

        when(courseService.getById(2L)).thenReturn(null);
        BusinessException missingEx = assertThrows(BusinessException.class,
                () -> accessControl.assertCanManageCourse(2L, auth(7L, "TEACHER")));
        assertEquals(ApiCode.NOT_FOUND, missingEx.getCode());
    }

    @Test
    void approved_student_can_read_course_but_unenrolled_student_cannot() {
        when(courseService.getById(1L)).thenReturn(course(1L, 7L));
        when(enrollmentService.hasApprovedEnrollment(9L, 1L)).thenReturn(true);
        accessControl.assertCanReadCourse(1L, auth(9L, "STUDENT"));

        when(enrollmentService.hasApprovedEnrollment(10L, 1L)).thenReturn(false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> accessControl.assertCanReadCourse(1L, auth(10L, "STUDENT")));
        assertEquals(ApiCode.FORBIDDEN, ex.getCode());
    }

    @Test
    void teacher_can_read_own_course_but_not_other_teacher_course() {
        when(courseService.getById(1L)).thenReturn(course(1L, 7L));
        accessControl.assertCanReadCourse(1L, auth(7L, "TEACHER"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accessControl.assertCanReadCourse(1L, auth(8L, "TEACHER")));
        assertEquals(ApiCode.FORBIDDEN, ex.getCode());
    }

    @Test
    void student_can_read_published_task_only_when_enrolled() {
        when(courseService.getById(1L)).thenReturn(course(1L, 7L));
        when(enrollmentService.hasApprovedEnrollment(9L, 1L)).thenReturn(true);
        accessControl.assertCanReadTask(task(1L, "PUBLISHED"), auth(9L, "STUDENT"));

        BusinessException draftEx = assertThrows(BusinessException.class,
                () -> accessControl.assertCanReadTask(task(1L, "DRAFT"), auth(9L, "STUDENT")));
        assertEquals(ApiCode.FORBIDDEN, draftEx.getCode());
    }

    @Test
    void readable_course_ids_follow_role_scope() {
        assertNull(accessControl.readableCourseIds(auth(1L, "ADMIN")));

        when(courseService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(course(1L, 7L), course(2L, 7L)));
        assertEquals(List.of(1L, 2L), accessControl.readableCourseIds(auth(7L, "TEACHER")));

        when(enrollmentService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(enrollment(1L), enrollment(3L)));
        assertEquals(List.of(1L, 3L), accessControl.readableCourseIds(auth(9L, "STUDENT")));
    }

    @Test
    void is_student_matches_role_authority() {
        assertTrue(accessControl.isStudent(auth(9L, "STUDENT")));
        assertFalse(accessControl.isStudent(auth(7L, "TEACHER")));
    }
}
