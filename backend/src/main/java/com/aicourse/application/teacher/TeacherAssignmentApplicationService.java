package com.aicourse.application.teacher;

import com.aicourse.common.PageSupport;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.GradeDTO;
import com.aicourse.dto.TaskSaveDTO;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.TeacherAccessGuard;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.vo.SubmissionVO;
import com.aicourse.vo.TaskVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 教师端作业管理应用服务（3E-05E）。
 *
 * <p>从 {@code TeacherAssignmentController} 下移 8 个端点的编排与 2 处
 * {@code LambdaQueryWrapper}：列表可见范围收窄、任务归属校验、创建时服务端写入
 * （creatorId 取登录身份 / status 缺省 DRAFT / isPublished 缺省 false）、提交分页、批改入参收窄。</p>
 *
 * <p><b>[登记 DEF · 双轨]</b> 同一聚合（Task）存在两条写路径：{@code /tasks}（走
 * {@code application.course.TaskApplicationService}，含 visibility / 目标教学班归一与
 * {@code replaceTaskTargets}）与此处 {@code /teacher/assignments}（走
 * {@code TeacherAccessGuard#assertTaskOwner} + {@code taskService.updateTaskById}，<b>不校验
 * visibility / 不写目标表</code>）。本批只做「整块下移 + 行为逐字保留」，不做合并——合并会改变
 * 教师端创建/更新的可见性语义，属功能变更，需单独裁定。</p>
 */
@Service
public class TeacherAssignmentApplicationService {

    private static final String STATUS_DRAFT = "DRAFT";

    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final CourseAccessControl courseAccessControl;
    private final TeacherAccessGuard teacherAccessGuard;

    public TeacherAssignmentApplicationService(TaskService taskService,
                                               SubmissionService submissionService,
                                               CourseAccessControl courseAccessControl,
                                               TeacherAccessGuard teacherAccessGuard) {
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.courseAccessControl = courseAccessControl;
        this.teacherAccessGuard = teacherAccessGuard;
    }

    /** 创建作业（若指定课程则需归属校验）。 */
    public TaskVO create(TaskSaveDTO dto, Authentication auth) {
        // 入参收 DTO：creatorId/status/isPublished 由服务端写入，创建人取登录身份
        Task task = dto.toEntity();
        task.setCreatorId((Long) auth.getPrincipal());
        if (task.getCourseId() != null) {
            teacherAccessGuard.assertCourseOwner(task.getCourseId(), auth);
        }
        if (task.getIsPublished() == null) {
            task.setIsPublished(false);
        }
        if (task.getStatus() == null) {
            task.setStatus(STATUS_DRAFT);
        }
        taskService.save(task);
        return TaskVO.from(task);
    }

    /** 分页查询作业列表（教师视角收窄到本人可读课程，管理员全量）。 */
    public PagedResponse<TaskVO> listAssignments(long page, long size, Long courseId, Authentication auth) {
        LambdaQueryWrapper<Task> qw = new LambdaQueryWrapper<Task>()
                .eq(courseId != null, Task::getCourseId, courseId);
        List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
        if (readableCourseIds != null) {
            if (readableCourseIds.isEmpty()) {
                return PagedResponse.empty();
            }
            qw.in(Task::getCourseId, readableCourseIds);
        }
        Page<Task> taskPage = taskService.page(Page.of(page, size), qw);
        return PageSupport.toPage(taskPage, TaskVO::from);
    }

    /** 作业详情（需作业归属校验）。 */
    public TaskVO detail(Long assignmentId, Authentication auth) {
        teacherAccessGuard.assertTaskOwner(assignmentId, auth);
        Task task = taskService.getById(assignmentId);
        if (task == null) {
            throw new BusinessException("作业不存在");
        }
        return TaskVO.from(task);
    }

    /** 更新作业（需作业归属校验；不改写创建人与发布状态）。 */
    public TaskVO update(Long assignmentId, TaskSaveDTO dto, Authentication auth) {
        teacherAccessGuard.assertTaskOwner(assignmentId, auth);
        Task updated = taskService.updateTaskById(assignmentId, dto.toEntity());
        return TaskVO.from(updated);
    }

    /** 删除作业（需作业归属校验）。 */
    public void delete(Long assignmentId, Authentication auth) {
        teacherAccessGuard.assertTaskOwner(assignmentId, auth);
        taskService.removeById(assignmentId);
    }

    /** 分页获取某作业的提交列表（需作业归属校验）。 */
    public PagedResponse<SubmissionVO> submissions(Long assignmentId, long page, long size, Authentication auth) {
        teacherAccessGuard.assertTaskOwner(assignmentId, auth);
        Page<Submission> submissionPage = submissionService.page(Page.of(page, size),
                new LambdaQueryWrapper<Submission>().eq(Submission::getTaskId, assignmentId));
        return PageSupport.toPage(submissionPage, SubmissionVO::from);
    }

    /** 批改某条提交（只允许写 score 与 feedback，防止注入 graderId/status）。 */
    public SubmissionVO grade(Long assignmentId, Long submissionId, GradeDTO grade, Authentication auth) {
        teacherAccessGuard.assertTaskOwner(assignmentId, auth);
        Submission gradeEntity = new Submission();
        gradeEntity.setScore(grade.getScore());
        gradeEntity.setFeedback(grade.getFeedback());
        Submission submission = submissionService.gradeSubmissionByTeacher(
                assignmentId, submissionId, gradeEntity, (Long) auth.getPrincipal());
        return SubmissionVO.from(submission);
    }

    /** 发布作业（需作业归属校验）。 */
    public TaskVO publish(Long assignmentId, Authentication auth) {
        teacherAccessGuard.assertTaskOwner(assignmentId, auth);
        return TaskVO.from(taskService.publishTask(assignmentId));
    }

}
