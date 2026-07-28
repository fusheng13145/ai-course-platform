package com.aicourse.security;

import com.aicourse.entity.Course;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.TaskMapper;
import com.aicourse.service.SubmissionService;
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
 * 对象级授权单元测试：直接验证 IDOR / 水平越权修复逻辑。
 * 无需数据库与 Spring 上下文，确定性证明归属校验行为。
 */
@ExtendWith(MockitoExtension.class)
class SubmissionAccessControlTest {

    @Mock
    private SubmissionService submissionService;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private CourseMapper courseMapper;

    @InjectMocks
    private SubmissionAccessControl accessControl;

    private static Authentication auth(Long userId, String role) {
        return new UsernamePasswordAuthenticationToken(userId, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static Submission submission(Long id, Long studentId, Long taskId) {
        Submission s = new Submission();
        s.setId(id);
        s.setStudentId(studentId);
        s.setTaskId(taskId);
        return s;
    }

    private static Task task(Long courseId) {
        Task t = new Task();
        t.setCourseId(courseId);
        return t;
    }

    private static Course course(Long id, Long teacherId) {
        Course c = new Course();
        c.setId(id);
        c.setTeacherId(teacherId);
        return c;
    }

    // ===================== 读权限 =====================

    @Test
    void studentReadsOwnSubmission_ok() {
        when(submissionService.getById(1L)).thenReturn(submission(1L, 10L, 5L));
        assertDoesNotThrow(() -> accessControl.assertReadable(1L, auth(10L, "STUDENT")));
    }

    @Test
    void studentReadsOthersSubmission_rejected() {
        when(submissionService.getById(1L)).thenReturn(submission(1L, 99L, 5L));
        assertThrows(BusinessException.class,
                () -> accessControl.assertReadable(1L, auth(10L, "STUDENT")));
    }

    @Test
    void teacherReadsOwnCourseSubmission_ok() {
        when(submissionService.getById(1L)).thenReturn(submission(1L, 99L, 5L));
        when(taskMapper.selectById(5L)).thenReturn(task(7L));
        when(courseMapper.selectById(7L)).thenReturn(course(7L, 20L));
        assertDoesNotThrow(() -> accessControl.assertReadable(1L, auth(20L, "TEACHER")));
    }

    @Test
    void teacherReadsOtherCourseSubmission_rejected() {
        when(submissionService.getById(1L)).thenReturn(submission(1L, 99L, 5L));
        when(taskMapper.selectById(5L)).thenReturn(task(7L));
        when(courseMapper.selectById(7L)).thenReturn(course(7L, 20L));
        assertThrows(BusinessException.class,
                () -> accessControl.assertReadable(1L, auth(21L, "TEACHER")));
    }

    // ===================== 管理（评分/查重/退回）权限 =====================

    @Test
    void studentManagesSubmission_rejected() {
        // 学生角色在 assertManageable 中直接被拒，不会查询提交记录
        assertThrows(BusinessException.class,
                () -> accessControl.assertManageable(1L, auth(10L, "STUDENT")));
    }

    @Test
    void teacherManagesOwnCourseSubmission_ok() {
        when(submissionService.getById(1L)).thenReturn(submission(1L, 99L, 5L));
        when(taskMapper.selectById(5L)).thenReturn(task(7L));
        when(courseMapper.selectById(7L)).thenReturn(course(7L, 20L));
        assertDoesNotThrow(() -> accessControl.assertManageable(1L, auth(20L, "TEACHER")));
    }

    @Test
    void teacherManagesOtherCourseSubmission_rejected() {
        when(submissionService.getById(1L)).thenReturn(submission(1L, 99L, 5L));
        when(taskMapper.selectById(5L)).thenReturn(task(7L));
        when(courseMapper.selectById(7L)).thenReturn(course(7L, 20L));
        assertThrows(BusinessException.class,
                () -> accessControl.assertManageable(1L, auth(21L, "TEACHER")));
    }

    @Test
    void listTaskByStudent_noCourseCheckNeeded_ok() {
        // 学生列出任务提交由控制器限定 studentId，无需课程归属校验
        assertDoesNotThrow(() -> accessControl.assertCanListTask(5L, auth(10L, "STUDENT")));
    }

    @Test
    void listTaskByTeacherOtherCourse_rejected() {
        when(taskMapper.selectById(5L)).thenReturn(task(7L));
        when(courseMapper.selectById(7L)).thenReturn(course(7L, 20L));
        assertThrows(BusinessException.class,
                () -> accessControl.assertCanListTask(5L, auth(21L, "TEACHER")));
    }
}
