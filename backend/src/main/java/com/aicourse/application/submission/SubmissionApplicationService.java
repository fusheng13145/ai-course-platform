package com.aicourse.application.submission;

import com.aicourse.common.ApiCode;
import com.aicourse.dto.AssignmentSubmitDTO;
import com.aicourse.dto.GradeDTO;
import com.aicourse.dto.SubmissionCreateDTO;
import com.aicourse.dto.SubmissionResubmitDTO;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.common.PagedResponse;
import com.aicourse.common.RoleConstants;
import com.aicourse.vo.SubmissionVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aicourse.security.AccessSupport;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.SubmissionAccessControl;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.UserService;
import com.aicourse.vo.UnsubmittedStudentVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 作业提交与评分用例编排（application 层）。
 *
 * <p><b>这一层为什么存在</b>：提交/评分这批端点的实质不是「接 HTTP」，而是「编排一次完整动作」——
 * 对象级授权 → 领域写入 → 提交后副作用（异步 AI 预批改）。这些步骤原先散在
 * {@code SubmissionController} 里，代价有三个，且都是实际踩过的：</p>
 * <ul>
 *   <li><b>双轨分叉</b>：同一个「提交作业」用例有两条入口（{@code POST /submissions} 与
 *       {@code POST /student/assignments/{id}/submit}）。两条各自写了一份授权与副作用，
 *       结果学生入口漏掉了 AI 预批改，且任务空值时一边 404、一边 NPE。收口到这里后，
 *       两条入口只是参数形状不同，动作序列由同一段代码决定。</li>
 *   <li><b>持久化细节泄漏</b>：催交名单/未提交名单的查询条件（招生课程在册 × 非草稿提交差集）
 *       原先写在控制器里，表结构一动就要改 Web 层。</li>
 *   <li><b>异步副作用住在 Web 层</b>：{@code CompletableFuture.runAsync(..., aiGradingExecutor)}
 *       写在控制器方法体里，控制器因此被迫注入线程池；换一个调用入口（定时任务、CLI）就没有预批改。
 *       现在预批改属于用例的一部分。</li>
 * </ul>
 *
 * <p><b>事务边界（与 3C-3 提交锁的关系，本批重点复核）</b>：本层刻意<b>不加</b>
 * {@code @Transactional}。事务仍由 {@code SubmissionServiceImpl} 的门面方法创建，
 * {@code SubmissionWriteComponent} 里的 {@code SubmissionSubmitGuard.Lease} 是 try-with-resources，
 * 在组件方法退出时 close。也就是说：</p>
 * <pre>
 * controller → application（无事务） → SubmissionServiceImpl.submitAssignment（开启事务）
 *                                          └─ component：acquire 锁 → 读改写 → lease.close() 释放锁
 * </pre>
 * <p>锁的持有区间仍然严格落在「同一事务内的一次读改写序列」上；本层多一级调用不会把锁提前释放，
 * 也不会把锁扩大到跨越异步任务（异步预批改在本层事务之外、提交返回之后触发）。
 * 若日后有人给本层加上 {@code @Transactional}，锁的释放点会挪到外层事务末尾，
 * 释放时机反而变晚（同一 (taskId, studentId) 的并发窗口被拉长），故以架构用例钉住：
 * {@code ArchitectureTest#submissionApplicationShouldStayNonTransactional} 断言本包方法不带
 * {@code @Transactional}，{@code #submissionApplicationShouldNotTouchDomainComponents} 断言本包不直接依赖
 * {@code service.component}。</p>
 */
@Slf4j
@Service
public class SubmissionApplicationService {

    private final SubmissionService submissionService;
    private final SubmissionAccessControl accessControl;
    private final CourseAccessControl courseAccessControl;
    private final TaskService taskService;
    private final CourseEnrollmentService enrollmentService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final Executor aiGradingExecutor;

    public SubmissionApplicationService(SubmissionService submissionService,
                                        SubmissionAccessControl accessControl,
                                        CourseAccessControl courseAccessControl,
                                        TaskService taskService,
                                        CourseEnrollmentService enrollmentService,
                                        UserService userService,
                                        NotificationService notificationService,
                                        @Qualifier("aiGradingExecutor") Executor aiGradingExecutor) {
        this.submissionService = submissionService;
        this.accessControl = accessControl;
        this.courseAccessControl = courseAccessControl;
        this.taskService = taskService;
        this.enrollmentService = enrollmentService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.aiGradingExecutor = aiGradingExecutor;
    }

    // ===================== 写：提交/草稿/重交/评分/退回/AI评分 =====================

    /**
     * 提交作业（{@code POST /submissions} 入口）。
     */
    /**
     * 我的提交记录（按 studentId 归属过滤，创建时间倒序）。
     */
    public PagedResponse<SubmissionVO> mySubmissions(Long studentId, long page, long size) {
        Page<Submission> result = submissionService.page(Page.of(page, size),
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getStudentId, studentId)
                        .orderByDesc(Submission::getCreatedAt));
        return toPagedVO(result);
    }

    /**
     * 指定任务下的提交记录。
     * <p>学生视角强制仅本人提交（防查看全班作业）；教师/管理员在可列任务的课程范围内看全部。</p>
     */
    public PagedResponse<SubmissionVO> listByTask(Long taskId, long page, long size, Authentication auth) {
        accessControl.assertCanListTask(taskId, auth);
        LambdaQueryWrapper<Submission> queryWrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTaskId, taskId);
        if (RoleConstants.STUDENT.equals(AccessSupport.roleOf(auth))) {
            queryWrapper.eq(Submission::getStudentId, (Long) auth.getPrincipal());
        }
        return toPagedVO(submissionService.page(Page.of(page, size), queryWrapper));
    }

    /** 提交分页转出参模型（阶段 3 · Q-05）。 */
    private static PagedResponse<SubmissionVO> toPagedVO(Page<Submission> page) {
        return PagedResponse.of(page.getRecords().stream().map(SubmissionVO::from).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    public Submission submit(SubmissionCreateDTO body, Authentication auth) {
        return doSubmit(body.getTaskId(), body.getContent(), body.getFileUrls(), auth);
    }

    /**
     * 提交作业（{@code POST /student/assignments/{id}/submit} 入口）。
     *
     * <p>与 {@link #submit} 共用同一段动作序列：任务存在性（404）+ 任务可读（发布态/选课/授课关系）
     * → 领域写入 → 异步预批改。此前该入口没有预批改，也不做任务存在性判断，属双轨遗漏。</p>
     */
    public Submission submitForAssignment(Long assignmentId, AssignmentSubmitDTO body, Authentication auth) {
        return doSubmit(assignmentId, body.getContent(), body.getFileUrls(), auth);
    }

    /** 保存草稿（与学生提交同源，复用同一行草稿，不计入次数）。 */
    public Submission saveDraft(SubmissionCreateDTO body, Authentication auth) {
        Long userId = principalId(auth);
        Task task = accessControl.assertCanSubmitTask(body.getTaskId(), auth);
        return submissionService.saveDraft(task.getId(), userId, body.getContent(), body.getFileUrls());
    }

    /** 重新提交（仅提交者本人或任教教师，读权限断言）。 */
    public Submission resubmit(Long submissionId, SubmissionResubmitDTO body, Authentication auth) {
        accessControl.assertReadable(submissionId, auth);
        return submissionService.resubmit(submissionId, body.getContent(), body.getFileUrls());
    }

    /** 教师评分（管理权限断言）。 */
    public Submission grade(Long submissionId, GradeDTO grade, Authentication auth) {
        accessControl.assertManageable(submissionId, auth);
        return submissionService.gradeSubmission(
                submissionId, grade.getScore(), grade.getFeedback(), principalId(auth));
    }

    /** 退回作业（管理权限断言）。 */
    public Submission returnSubmission(Long submissionId, String reason, Authentication auth) {
        accessControl.assertManageable(submissionId, auth);
        return submissionService.returnSubmission(submissionId, reason);
    }

    /** 触发/重跑 AI 评分（管理权限断言）。 */
    public Submission aiGrade(Long submissionId, Authentication auth) {
        accessControl.assertManageable(submissionId, auth);
        return submissionService.aiGradeSubmission(submissionId);
    }

    // ===================== 读 + 催交（教师侧名单） =====================

    /**
     * 任务未提交学生名单：课程在册（{@code enrollment.isActive}）− 已有非草稿提交的学生。
     */
    public List<UnsubmittedStudentVO> unsubmittedStudents(Long taskId, Authentication auth) {
        Task task = requireManageableTask(taskId, auth);
        return unsubmittedStudentsOf(task).stream().map(UnsubmittedStudentVO::from).toList();
    }

    /**
     * 一键催交：给未提交学生批量投递系统通知。
     *
     * <p>刻意不建 {@code Reminder} 定时任务——催交是即时动作，建 PENDING 任务还要额外触发 send-due。</p>
     */
    public ReminderOutcome remindUnsubmitted(Long taskId, Authentication auth) {
        Task task = requireManageableTask(taskId, auth);
        List<User> students = unsubmittedStudentsOf(task);
        if (students.isEmpty()) {
            return new ReminderOutcome(0, "没有未提交的学生");
        }
        List<Long> userIds = students.stream().map(User::getId).collect(Collectors.toList());
        String title = "作业催交：" + task.getTitle();
        String content = task.getDueDate() != null
                ? "请在 " + task.getDueDate().toString().replace('T', ' ') + " 前完成并提交；逾期将按规则扣分。"
                : "请尽快完成并提交。";
        notificationService.batchSendNotifications(userIds, title, content, "SYSTEM", "TASK_REMIND", "MEDIUM",
                "/assignment/" + taskId, null);
        return new ReminderOutcome(userIds.size(), "已向 " + userIds.size() + " 名学生发送催交通知");
    }

    /** 催交结果（计数 + 提示文案，文案由本层产生以保持与改造前逐字一致）。 */
    public record ReminderOutcome(int count, String message) {
    }

    // ===================== 内部 =====================

    /** 提交动作序列：授权 → 领域写入 → 异步预批改。 */
    private Submission doSubmit(Long taskId, String content, List<String> fileUrls, Authentication auth) {
        Long studentId = principalId(auth);
        // 对象级校验：任务存在（404）+ 发布态/选课/授课关系（403）。
        // 服务层仍保留发布/截止/次数/逾期策略，职责不重叠。
        accessControl.assertCanSubmitTask(taskId, auth);
        Submission submission = submissionService.submitAssignment(taskId, studentId, content, fileUrls);
        triggerAiPreGrade(submission.getId());
        return submission;
    }

    /**
     * 异步 AI 预批改：不阻塞提交响应，失败只记日志（提交主流程绝不因预批改失败而回滚）。
     */
    private void triggerAiPreGrade(Long submissionId) {
        if (aiGradingExecutor == null) {
            return;
        }
        Long savedId = submissionId;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                submissionService.aiGradeSubmission(savedId);
                log.info("AI预批改完成: submissionId={}", savedId);
            } catch (Exception e) {
                log.warn("AI预批改失败（不影响提交流程）: submissionId={}", savedId, e);
            }
        }, aiGradingExecutor);
    }

    /** 取任务并校验当前用户可管理其所属课程。 */
    private Task requireManageableTask(Long taskId, Authentication auth) {
        Task task = taskId != null ? taskService.getById(taskId) : null;
        if (task == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "作业任务不存在");
        }
        if (task.getCourseId() == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "任务未关联课程");
        }
        courseAccessControl.assertCanManageCourse(task.getCourseId(), auth);
        return task;
    }

    /** 课程在册学生中，尚未正式提交该任务的人（按账号排序，便于教师核对）。 */
    private List<User> unsubmittedStudentsOf(Task task) {
        List<CourseEnrollment> enrollments = enrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getCourseId, task.getCourseId())
                        .eq(CourseEnrollment::getIsActive, true));
        if (enrollments.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> enrolledIds = enrollments.stream()
                .map(CourseEnrollment::getStudentId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Submission> submitted = submissionService.list(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getTaskId, task.getId())
                        .ne(Submission::getStatus, "DRAFT"));
        Set<Long> submittedIds = submitted.stream()
                .map(Submission::getStudentId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        enrolledIds.removeAll(submittedIds);
        if (enrolledIds.isEmpty()) {
            return new ArrayList<>();
        }
        return userService.listByIds(enrolledIds).stream()
                .sorted((a, b) -> String.valueOf(a.getUsername()).compareTo(String.valueOf(b.getUsername())))
                .collect(Collectors.toList());
    }

    private Long principalId(Authentication auth) {
        return AccessSupport.requirePrincipalId(auth);
    }
}
