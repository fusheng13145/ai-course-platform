package com.aicourse.security;

import com.aicourse.common.RoleConstants;
import com.aicourse.entity.Course;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.TaskMapper;
import com.aicourse.service.SubmissionService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 提交记录对象级授权（防 IDOR / BOLA / 水平越权）。
 *
 * <p>原 {@code SubmissionController} 仅按角色放行，未校验资源归属：
 * 任意学生可枚举 {@code /submissions/1,2,3...} 拖走全平台作业；教师 A 可批改教师 B 的课程。
 * 本组件在 Service 边界统一执行归属校验，作为后端权威兜底（即使前端伪边界被绕过也安全）。</p>
 *
 * <p>归属规则：
 * <ul>
 *   <li>学生：仅可访问/操作其本人（{@code studentId == 当前用户}）的提交；</li>
 *   <li>教师/管理员：仅可访问/操作其本人任教课程（{@code Course.teacherId == 当前用户}）下的提交。</li>
 * </ul>
 * </p>
 */
@Component
public class SubmissionAccessControl {

    private final SubmissionService submissionService;
    private final TaskMapper taskMapper;
    private final CourseMapper courseMapper;

    public SubmissionAccessControl(SubmissionService submissionService,
                                   TaskMapper taskMapper,
                                   CourseMapper courseMapper) {
        this.submissionService = submissionService;
        this.taskMapper = taskMapper;
        this.courseMapper = courseMapper;
    }

    /**
     * 读权限校验：学生仅本人；教师/管理员须拥有所属课程。
     */
    public void assertReadable(Long submissionId, Authentication auth) {
        Long userId = principalId(auth);
        String role = roleOf(auth);
        Submission submission = requireSubmission(submissionId);
        if (RoleConstants.STUDENT.equals(role)) {
            if (!userId.equals(submission.getStudentId())) {
                throw new BusinessException(403, "无权访问该提交记录");
            }
            return;
        }
        assertOwnsCourse(submission.getTaskId(), userId);
    }

    /**
     * 管理（评分/查重/退回/AI评分）权限校验：仅教师/管理员且其本人任教课程。
     */
    public void assertManageable(Long submissionId, Authentication auth) {
        Long userId = principalId(auth);
        String role = roleOf(auth);
        if (RoleConstants.STUDENT.equals(role)) {
            throw new BusinessException(403, "学生无权执行该操作");
        }
        Submission submission = requireSubmission(submissionId);
        assertOwnsCourse(submission.getTaskId(), userId);
    }

    /**
     * 列出某任务提交：学生由调用方限定仅本人；教师/管理员须拥有该任务所属课程。
     */
    public void assertCanListTask(Long taskId, Authentication auth) {
        String role = roleOf(auth);
        if (RoleConstants.STUDENT.equals(role)) {
            return;
        }
        assertOwnsCourse(taskId, principalId(auth));
    }

    // ===================== 内部工具 =====================

    private Submission requireSubmission(Long submissionId) {
        Submission submission = submissionService.getById(submissionId);
        if (submission == null) {
            throw new BusinessException(404, "提交记录不存在");
        }
        return submission;
    }

    private void assertOwnsCourse(Long taskId, Long userId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "关联任务不存在");
        }
        Course course = courseMapper.selectById(task.getCourseId());
        if (course == null) {
            throw new BusinessException(404, "关联课程不存在");
        }
        if (!userId.equals(course.getTeacherId())) {
            throw new BusinessException(403, "无权操作非本人课程的提交记录");
        }
    }

    private Long principalId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }

    private String roleOf(Authentication auth) {
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .toList();
        return roles.isEmpty() ? "" : roles.get(0);
    }
}
