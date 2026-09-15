package com.aicourse.security;

import com.aicourse.entity.Course;
import com.aicourse.entity.ExamPaper;
import com.aicourse.exception.BusinessException;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 试卷对象级授权单元测试。
 *
 * <p>覆盖 2026-09-14 修复的越权缺陷：教师 B 可发布教师 A 课程试卷（{@code publish}）、
 * 可为他人课程组卷（{@code auto-compose}）。规则：学生一律拒绝、管理员放行、
 * 教师须为课程授课教师（exam_papers.course_id → courses.teacher_id）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ExamPaperAccessControlTest {

    @Mock
    private CourseService courseService;

    @InjectMocks
    private ExamPaperAccessControl accessControl;

    private static Authentication auth(Long userId, String role) {
        return new UsernamePasswordAuthenticationToken(userId, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static ExamPaper paper(Long id, Long courseId) {
        ExamPaper p = new ExamPaper();
        p.setId(id);
        p.setCourseId(courseId);
        return p;
    }

    private static Course course(Long id, Long teacherId) {
        Course c = new Course();
        c.setId(id);
        c.setTeacherId(teacherId);
        return c;
    }

    // ===================== assertCanManage（发布/更新/删除/复制） =====================

    @Test
    void publish_ownCourseTeacher_ok() {
        when(courseService.getById(7L)).thenReturn(course(7L, 20L));
        assertDoesNotThrow(() -> accessControl.assertCanManage(paper(5L, 7L), auth(20L, "TEACHER")));
    }

    @Test
    void publish_otherTeacherCourse_rejected() {
        when(courseService.getById(7L)).thenReturn(course(7L, 20L));
        assertThrows(BusinessException.class,
                () -> accessControl.assertCanManage(paper(5L, 7L), auth(21L, "TEACHER")));
    }

    @Test
    void publish_byAdmin_ok() {
        assertDoesNotThrow(() -> accessControl.assertCanManage(paper(5L, 7L), auth(1L, "ADMIN")));
    }

    @Test
    void publish_byStudent_rejected() {
        assertThrows(BusinessException.class,
                () -> accessControl.assertCanManage(paper(5L, 7L), auth(10L, "STUDENT")));
    }

    @Test
    void publish_paperNotFound_rejected() {
        assertThrows(BusinessException.class,
                () -> accessControl.assertCanManage(null, auth(20L, "TEACHER")));
    }

    @Test
    void publish_courseNotFound_rejected() {
        when(courseService.getById(7L)).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> accessControl.assertCanManage(paper(5L, 7L), auth(20L, "TEACHER")));
    }

    // ===================== assertCanCompose（自动组卷） =====================

    @Test
    void compose_ownCourse_ok() {
        when(courseService.getById(7L)).thenReturn(course(7L, 20L));
        assertDoesNotThrow(() -> accessControl.assertCanCompose(7L, auth(20L, "TEACHER")));
    }

    @Test
    void compose_otherTeacherCourse_rejected() {
        when(courseService.getById(7L)).thenReturn(course(7L, 20L));
        assertThrows(BusinessException.class,
                () -> accessControl.assertCanCompose(7L, auth(21L, "TEACHER")));
    }

    @Test
    void compose_byAdmin_ok() {
        assertDoesNotThrow(() -> accessControl.assertCanCompose(7L, auth(1L, "ADMIN")));
    }

    @Test
    void compose_byStudent_rejected() {
        assertThrows(BusinessException.class,
                () -> accessControl.assertCanCompose(7L, auth(10L, "STUDENT")));
    }

    @Test
    void compose_missingCourseId_rejected() {
        assertThrows(BusinessException.class,
                () -> accessControl.assertCanCompose(null, auth(20L, "TEACHER")));
    }
}
