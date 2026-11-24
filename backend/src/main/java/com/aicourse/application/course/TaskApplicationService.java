package com.aicourse.application.course;

import com.aicourse.common.ActivityVisibility;
import com.aicourse.common.ActivityTargetsPayload;
import com.aicourse.common.ApiCode;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.ActivityTargetDTO;
import com.aicourse.dto.TaskSaveDTO;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.ActivityVisibilityGuard;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.ActivityTargetService;
import com.aicourse.service.TaskService;
import com.aicourse.util.LenientDateTimeParser;
import com.aicourse.vo.TaskCompletionStatsVO;
import com.aicourse.vo.TaskVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 作业任务应用服务（3E-04A）。
 *
 * <p>把原先散在 {@code TaskController} 里的编排收拢到一处。改造前有 8 个端点重复写
 * 「取任务 → 判空 404 → 判课程管理权」这两步，其中任何一个端点漏写一步就是一个越权口子；
 * 现在统一走 {@link #requireManageableTask}。</p>
 *
 * <p>四条既有口径原样保留（迁移时逐条核对，不做「顺手优化」）：</p>
 * <ol>
 *   <li><b>任务创建</b>：{@code creatorId} 取登录身份、{@code status} 固定 DRAFT、
 *       {@code isPublished} 缺省 false——否则创建即可自标已发布，绕过 /publish 状态机与通知。</li>
 *   <li><b>任务更新</b>：{@code courseId} 沿用原任务课程，不接受请求值（防跨课程搬运）；
 *       未触达目标教学班时<strong>不重算 visibility、不重写目标表</strong>。</li>
 *   <li><b>发布</b>：管理员治理只读，不代教师发布（WP6）。</li>
 *   <li><b>截止日期</b>：{@code newDueDate} 走 {@link LenientDateTimeParser}，与全局反序列化同口径
 *       ——B-13 ⑨ 修的就是「同一控制器内 dueDate 宽松、newDueDate 只认 ISO-8601」的不一致。</li>
 * </ol>
 */
@Service
public class TaskApplicationService {

    private final TaskService taskService;
    private final CourseAccessControl courseAccessControl;
    private final ActivityVisibilityGuard activityVisibilityGuard;
    private final ActivityTargetService activityTargetService;

    public TaskApplicationService(TaskService taskService,
                                  CourseAccessControl courseAccessControl,
                                  ActivityVisibilityGuard activityVisibilityGuard,
                                  ActivityTargetService activityTargetService) {
        this.taskService = taskService;
        this.courseAccessControl = courseAccessControl;
        this.activityVisibilityGuard = activityVisibilityGuard;
        this.activityTargetService = activityTargetService;
    }

    // ===================== 查询 =====================

    /** 分页查询任务：学生只见 PUBLISHED，教学班可见范围由守卫追加条件。 */
    public PagedResponse<TaskVO> listByParams(long page, long size, Long courseId, Long teachingClassId,
                                              Authentication auth) {
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
        }
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<Task>()
                .eq(courseId != null, Task::getCourseId, courseId)
                .eq(courseAccessControl.isStudent(auth), Task::getStatus, "PUBLISHED");
        activityVisibilityGuard.applyTaskScope(queryWrapper, auth, teachingClassId);
        return toPagedVO(taskService.page(Page.of(page, size), queryWrapper));
    }

    /** 按课程分页查询任务。 */
    public PagedResponse<TaskVO> listByCourse(Long courseId, long page, long size, Long teachingClassId,
                                              Authentication auth) {
        courseAccessControl.assertCanReadCourse(courseId, auth);
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getCourseId, courseId)
                .eq(courseAccessControl.isStudent(auth), Task::getStatus, "PUBLISHED");
        activityVisibilityGuard.applyTaskScope(queryWrapper, auth, teachingClassId);
        return toPagedVO(taskService.page(Page.of(page, size), queryWrapper));
    }

    /** 任务详情（按可见性判定，学生可达）。 */
    public TaskVO detail(Long id, Authentication auth) {
        Task task = taskService.getById(id);
        activityVisibilityGuard.assertVisibleTask(task, auth);
        return TaskVO.from(task);
    }

    /** 待批阅任务列表（教师身份维度）。 */
    public List<TaskVO> pendingGrading(Authentication auth) {
        Long teacherId = (Long) auth.getPrincipal();
        List<Task> tasks = taskService.getPendingGradingTasks(teacherId);
        return tasks.stream().map(TaskVO::from).toList();
    }

    /** 任务完成率统计。 */
    public TaskCompletionStatsVO completionStats(Long id, Authentication auth) {
        Task existing = requireManageableTask(id, auth);
        return taskService.getTaskCompletionStats(existing.getId());
    }

    // ===================== 目标教学班 =====================

    /** 查询任务可见范围与目标教学班。 */
    public Map<String, Object> targets(Long id, Authentication auth) {
        Task task = requireTask(id);
        activityVisibilityGuard.assertVisibleTask(task, auth);
        return ActivityTargetsPayload.of(
                ActivityVisibility.normalize(task.getVisibility()),
                activityTargetService.taskTargetClassIds(id));
    }

    /** 设置任务目标教学班（教师权限；管理员 403）。 */
    public Map<String, Object> updateTargets(Long id, ActivityTargetDTO targets, Authentication auth) {
        activityVisibilityGuard.assertNotAdmin(auth);
        Task existing = requireTask(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        String visibility = activityTargetService.normalizeAndValidate(
                id, existing.getCourseId(), targets.getVisibility(), targets.getTeachingClassIds(), false);
        Task patch = new Task();
        patch.setId(id);
        patch.setVisibility(visibility);
        taskService.updateById(patch);
        activityTargetService.replaceTaskTargets(id, visibility, targets.getTeachingClassIds());
        return ActivityTargetsPayload.of(visibility, activityTargetService.taskTargetClassIds(id));
    }

    // ===================== 写面 =====================

    /**
     * 创建任务。
     *
     * <p>客户端只能决定内容与可见范围：归属、状态、发布标记全部服务端写入。</p>
     */
    public TaskVO create(TaskSaveDTO dto, Authentication auth) {
        Task task = dto.toEntity();
        task.setCreatorId((Long) auth.getPrincipal());
        if (task.getIsPublished() == null) {
            task.setIsPublished(false);
        }
        task.setStatus("DRAFT");
        courseAccessControl.assertCanManageCourse(task.getCourseId(), auth);
        String requestedVisibility = ActivityVisibility.normalizeOrNull(task.getVisibility());
        if (ActivityVisibility.CLASSES.equals(requestedVisibility)
                || (task.getTeachingClassIds() != null && !task.getTeachingClassIds().isEmpty())) {
            activityVisibilityGuard.assertNotAdmin(auth);
        }
        String visibility = activityTargetService.normalizeAndValidate(
                null, task.getCourseId(), task.getVisibility(), task.getTeachingClassIds(), false);
        task.setVisibility(visibility);
        taskService.save(task);
        activityTargetService.replaceTaskTargets(task.getId(), visibility, task.getTeachingClassIds());
        return TaskVO.from(task);
    }

    /**
     * 更新任务。
     *
     * <p>{@code courseId} 沿用原任务课程；仅在请求真的带了 visibility 或 teachingClassIds 时
     * 才重算可见性并重写目标表——否则一次「只改标题」的保存会静默清空目标教学班。</p>
     */
    public TaskVO update(Long id, TaskSaveDTO dto, Authentication auth) {
        Task task = dto.toEntity();
        Task existing = requireManageableTask(id, auth);
        boolean targetsTouched = task.getVisibility() != null || task.getTeachingClassIds() != null;
        String normalizedVisibility = null;
        if (targetsTouched) {
            // 管理员不得修改目标教学班（WP6 验收第 4 条）
            activityVisibilityGuard.assertNotAdmin(auth);
            String effectiveVisibility = task.getVisibility() != null
                    ? task.getVisibility() : existing.getVisibility();
            normalizedVisibility = activityTargetService.normalizeAndValidate(
                    id, existing.getCourseId(), effectiveVisibility, task.getTeachingClassIds(), false);
            task.setVisibility(normalizedVisibility);
        }
        task.setId(id);
        task.setCourseId(existing.getCourseId());
        taskService.updateById(task);
        if (targetsTouched) {
            activityTargetService.replaceTaskTargets(id, normalizedVisibility, task.getTeachingClassIds());
        }
        return TaskVO.from(taskService.getById(id));
    }

    /** 删除任务。 */
    public void delete(Long id, Authentication auth) {
        requireManageableTask(id, auth);
        taskService.removeById(id);
    }

    /** 发布任务：管理员治理只读，不代教师发布（WP6）。 */
    public TaskVO publish(Long id, Authentication auth) {
        activityVisibilityGuard.assertNotAdmin(auth);
        requireManageableTask(id, auth);
        return TaskVO.from(taskService.publishTask(id));
    }

    /**
     * 延长截止日期。
     *
     * <p>日期解析走 {@link LenientDateTimeParser}（B-13 ⑨），解析失败仍是 400 而不是 500。</p>
     */
    public TaskVO extendDeadline(Long id, String newDueDateText, String reason, Authentication auth) {
        if (newDueDateText == null || newDueDateText.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "新的截止日期不能为空");
        }
        LocalDateTime newDueDate;
        try {
            newDueDate = LenientDateTimeParser.parse(newDueDateText);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ApiCode.BAD_REQUEST,
                    "截止日期格式不正确；" + LenientDateTimeParser.FORMAT_HINT);
        }
        requireManageableTask(id, auth);
        return TaskVO.from(taskService.extendDeadline(id, newDueDate, reason));
    }

    /** 复制任务（可自定义新标题）。 */
    public TaskVO copy(Long id, String newTitle, Authentication auth) {
        requireManageableTask(id, auth);
        return TaskVO.from(taskService.copyTask(id, newTitle));
    }

    // ===================== 内部 =====================

    private Task requireTask(Long id) {
        Task task = taskService.getById(id);
        if (task == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    /**
     * 取「当前用户可管理」的任务：取任务（404）→ 课程管理权（403）。
     *
     * <p>改造前这两步在 8 个端点各写一遍，漏一步就是越权；这里合成唯一入口。</p>
     */
    private Task requireManageableTask(Long id, Authentication auth) {
        Task task = requireTask(id);
        courseAccessControl.assertCanManageCourse(task.getCourseId(), auth);
        return task;
    }

    private static PagedResponse<TaskVO> toPagedVO(Page<Task> page) {
        return PagedResponse.of(page.getRecords().stream().map(TaskVO::from).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }
}
