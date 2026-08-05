package com.aicourse.security;

import com.aicourse.entity.Course;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamSessionService;
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
 * 考试会话对象级授权单元测试：直接验证收卷/阅卷/作弊检测等管理操作的归属校验逻辑。
 * 无需数据库与 Spring 上下文，确定性证明：学生一律拒绝、管理员放行、
 * 教师须为会话所属试卷对应课程的授课教师（exam_papers.course_id → courses.teacher_id）。
 */
@ExtendWith(MockitoExtension.class)
class ExamSessionAccessControlTest {

    @Mock
    private ExamSessionService examSessionService;
    @Mock
    private ExamPaperService examPaperService;
    @Mock
    private CourseService courseService;

    @InjectMocks
    private ExamSessionAccessControl accessControl;

    private static Authentication auth(Long userId, String role) {
        return new UsernamePasswordAuthenticationToken(userId, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static ExamSession session(Long id, Long examPaperId, Long studentId) {
        ExamSession s = new ExamSession();
        s.setId(id);
        s.setExamPaperId(examPaperId);
        s.setStudentId(studentId);
        return s;
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

    // ===================== 管理权限（force-submit / auto-grade / manual-grade / cheating） =====================

    @Test
    void studentManagesSession_rejected() {
        // 学生角色直接拒绝，不依赖任何查询
        assertThrows(BusinessException.class,
                () -> accessControl.assertManageable(1L, auth(10L, "STUDENT")));
    }

    @Test
    void adminManagesAnySession_ok() {
        // 管理员无需试卷/课程查询直接放行（即使会话存在即可）
        when(examSessionService.getById(1L)).thenReturn(session(1L, 5L, 10L));
        assertDoesNotThrow(() -> accessControl.assertManageable(1L, auth(1L, "ADMIN")));
    }

    @Test
    void teacherManagesOwnCourseSession_ok() {
        when(examSessionService.getById(1L)).thenReturn(session(1L, 5L, 10L));
        when(examPaperService.getById(5L)).thenReturn(paper(5L, 7L));
        when(courseService.getById(7L)).thenReturn(course(7L, 20L));
        assertDoesNotThrow(() -> accessControl.assertManageable(1L, auth(20L, "TEACHER")));
    }

    @Test
    void teacherManagesOtherTeachersCourseSession_rejected() {
        when(examSessionService.getById(1L)).thenReturn(session(1L, 5L, 10L));
        when(examPaperService.getById(5L)).thenReturn(paper(5L, 7L));
        when(courseService.getById(7L)).thenReturn(course(7L, 20L));
        assertThrows(BusinessException.class,
                () -> accessControl.assertManageable(1L, auth(21L, "TEACHER")));
    }

    @Test
    void sessionNotFound_rejected() {
        when(examSessionService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> accessControl.assertManageable(1L, auth(20L, "TEACHER")));
    }
}
