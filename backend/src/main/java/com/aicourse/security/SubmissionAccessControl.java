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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(SubmissionAccessControl.class);

    private final SubmissionService submissionService;
    private final TaskMapper taskMapper;
    private final CourseMapper courseMapper;
    private final CourseAccessControl courseAccessControl;

    public SubmissionAccessControl(SubmissionService submissionService,
                                   TaskMapper taskMapper,
                                   CourseMapper courseMapper,
                                   CourseAccessControl courseAccessControl) {
        this.submissionService = submissionService;
        this.taskMapper = taskMapper;
        this.courseMapper = courseMapper;
        this.courseAccessControl = courseAccessControl;
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
                log.warn("[SubmissionAccess] 越权访问尝试（学生非本人提交）：submissionId={}, 访问者={}, 持有者={}",
                        submissionId, userId, submission.getStudentId());
                throw new BusinessException(403, "无权访问该提交记录");
            }
            return;
        }
        assertCanAccessTaskCourse(submission.getTaskId(), auth, false);
    }

    /**
     * 管理（评分/查重/退回/AI评分）权限校验：仅教师/管理员且其本人任教课程。
     */
    public void assertManageable(Long submissionId, Authentication auth) {
        Long userId = principalId(auth);
        String role = roleOf(auth);
        if (RoleConstants.STUDENT.equals(role)) {
            log.warn("[SubmissionAccess] 越权管理尝试（学生角色）：submissionId={}, userId={}", submissionId, userId);
            throw new BusinessException(403, "学生无权执行该操作");
        }
        // 教学写面不替代教师：管理员只读本院提交/成绩，评分/退回/查重仍须课程负责人本人。
        if (isAdmin(auth)) {
            log.warn("[SubmissionAccess] 拒绝管理员代教师管理提交：submissionId={}, userId={}", submissionId, userId);
            throw new BusinessException(403, "管理员仅可查看提交记录，不能代教师评分或退回");
        }
        Submission submission = requireSubmission(submissionId);
        assertCanAccessTaskCourse(submission.getTaskId(), auth, true);
    }

    /**
     * 列出某任务提交：学生由调用方限定仅本人；教师/管理员须拥有该任务所属课程。
     */
    public void assertCanListTask(Long taskId, Authentication auth) {
        String role = roleOf(auth);
        if (RoleConstants.STUDENT.equals(role)) {
            return;
        }
        assertCanAccessTaskCourse(taskId, auth, false);
    }

    /**
     * 提交作业前的对象级校验：任务必须存在，且当前用户对该任务所属课程有读权限。
     *
     * <p>学生走 {@link CourseAccessControl#assertCanReadTask}：任务已发布 + 已通过审批的选课；
     * 教师须为任务所属课程授课教师；管理员按课程作用域放行。原先两条提交入口
     * （{@code SubmissionController#submit} 与 {@code StudentController#submitAssignment}）
     * 都只校验任务发布/次数/截止，未校验课程成员关系，任意学生可向他人课程任务提交。</p>
     *
     * @throws BusinessException 任务不存在 404；未发布/未选课/跨教师课程 403
     */
    public Task assertCanSubmitTask(Long taskId, Authentication auth) {
        Task task = taskId != null ? taskMapper.selectById(taskId) : null;
        if (task == null) {
            throw new BusinessException(404, "作业任务不存在");
        }
        courseAccessControl.assertCanReadTask(task, auth);
        return task;
    }

    // ===================== 内部工具 =====================

    private Submission requireSubmission(Long submissionId) {
        Submission submission = submissionService.getById(submissionId);
        if (submission == null) {
            throw new BusinessException(404, "提交记录不存在");
        }
        return submission;
    }

    /**
     * 课程边界：管理员按学院作用域只读/治理；教师仍须为课程负责人。
     *
     * <p>2026-11-16 P0-2：原实现对 ADMIN 也走 {@code assertOwnsCourse}，导致校级管理员
     * 反而被 403；现改为管理员走 {@link CourseAccessControl}（其内部已接
     * {@link AdminScopeGuard}），教学写面不因此获得代教师权限。</p>
     */
    private void assertCanAccessTaskCourse(Long taskId, Authentication auth, boolean manage) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "关联任务不存在");
        }
        Course course = courseMapper.selectById(task.getCourseId());
        if (course == null) {
            throw new BusinessException(404, "关联课程不存在");
        }
        if (isAdmin(auth)) {
            if (manage) {
                courseAccessControl.assertCanManageCourse(course.getId(), auth);
            } else {
                courseAccessControl.assertCanReadCourse(course.getId(), auth);
            }
            return;
        }
        Long userId = principalId(auth);
        if (!userId.equals(course.getTeacherId())) {
            log.warn("[SubmissionAccess] 越权管理尝试（非授课教师）：submissionId/taskId={}, userId={}, courseId={}, teacherId={}",
                    taskId, userId, course.getId(), course.getTeacherId());
            throw new BusinessException(403, "无权操作非本人课程的提交记录");
        }
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_" + RoleConstants.ADMIN));
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
