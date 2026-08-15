package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.constant.SubmissionStatus;

import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.TaskMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 任务服务实现：相关业务逻辑入口，提供增删改查与编排能力。
 */
@Service
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);

    private final SubmissionService submissionService;
    private final CourseEnrollmentService courseEnrollmentService;
    private final NotificationService notificationService;

    public TaskServiceImpl(@Lazy SubmissionService submissionService,
                           CourseEnrollmentService courseEnrollmentService,
                           NotificationService notificationService) {
        this.submissionService = submissionService;
        this.courseEnrollmentService = courseEnrollmentService;
        this.notificationService = notificationService;
    }

    /**
     * 发布任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Task publishTask(Long taskId) {
        Task task = this.getById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        if (Boolean.TRUE.equals(task.getIsPublished())) {
            throw new BusinessException("任务已发布");
        }

        task.setIsPublished(true);
        task.setStatus("PUBLISHED");
        this.updateById(task);

        // 修复 P1-1：作业发布后自动推送通知给课程选课学生（事件驱动，铃铛不再失效）
        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("courseId", task.getCourseId());
            eventData.put("taskId", task.getId());
            eventData.put("taskTitle", task.getTitle());
            notificationService.onEvent("TASK_CREATED", eventData);
        } catch (Exception e) {
            log.error("发布任务失败: {}", e.getMessage(), e);

            // 通知失败不影响发布主流程
        }

        return task;
    }

    /**
     * 仅执行写操作的任务更新（权限已由调用方校验）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Task updateTaskById(Long id, Task task) {
        log.info("更新任务");

        task.setId(id);
        this.updateById(task);
        return this.getById(id);
    }

    /**
     * 延长截止日期
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Task extendDeadline(Long taskId, LocalDateTime newDueDate, String reason) {
        Task task = this.getById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        if (newDueDate == null) {
            throw new BusinessException("新的截止日期不能为空");
        }
        if (task.getDueDate() != null && !newDueDate.isAfter(task.getDueDate())) {
            throw new BusinessException("新的截止日期必须晚于当前截止日期");
        }

        task.setDueDate(newDueDate);

        // 记录延长原因到requirements（简单处理，实际可用扩展字段）
        String currentRequirements = task.getRequirements() != null ? task.getRequirements() : "";
        String extensionNote = "\n[延长截止日期至 " + newDueDate + "，原因：" + (reason != null ? reason : "未说明") + "]";
        task.setRequirements(currentRequirements + extensionNote);

        this.updateById(task);
        return task;
    }

    /**
     * 复制任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Task copyTask(Long sourceTaskId, String newTitle) {
        log.info("复制任务");

        Task sourceTask = this.getById(sourceTaskId);
        if (sourceTask == null) {
            throw new BusinessException("源任务不存在");
        }

        // 创建新任务
        Task newTask = new Task();
        newTask.setTitle(newTitle != null ? newTitle : sourceTask.getTitle() + "(副本)");
        newTask.setDescription(sourceTask.getDescription());
        newTask.setTaskType(sourceTask.getTaskType());
        newTask.setCourseId(sourceTask.getCourseId());
        // 修复 P0-7：复制任务必须保留章节归属，否则复制出的任务从章节视图消失
        newTask.setChapterId(sourceTask.getChapterId());
        newTask.setCreatorId(sourceTask.getCreatorId());
        // 新任务的截止日期默认为空，需要手动设置
        newTask.setMaxScore(sourceTask.getMaxScore());
        newTask.setStatus("DRAFT");
        newTask.setIsPublished(false);
        newTask.setSubmissionFormat(sourceTask.getSubmissionFormat());
        newTask.setRequirements(sourceTask.getRequirements());
        newTask.setAttachmentUrl(sourceTask.getAttachmentUrl());

        this.save(newTask);
        return newTask;
    }

    /**
     * 获取待批阅的任务列表（教师视角）
     * 查询该教师创建的任务中存在未批阅提交记录的任务
     */
    @Override
    public List<Task> getPendingGradingTasks(Long teacherId) {
        // 查询该教师创建的所有已发布任务
        LambdaQueryWrapper<Task> taskWrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getCreatorId, teacherId)
                .eq(Task::getIsPublished, true)
                .orderByDesc(Task::getDueDate);
        List<Task> allTasks = this.list(taskWrapper);

        // 筛选出有待批阅提交的任务
        List<Task> pendingTasks = new ArrayList<>();
        for (Task task : allTasks) {
            LambdaQueryWrapper<Submission> submissionWrapper = new LambdaQueryWrapper<Submission>()
                    .eq(Submission::getTaskId, task.getId())
                    .in(Submission::getStatus, SubmissionStatus.SUBMITTED, SubmissionStatus.RESUBMITTED);
            long pendingCount = submissionService.count(submissionWrapper);
            if (pendingCount > 0) {
                pendingTasks.add(task);
            }
        }

        return pendingTasks;
    }

    /**
     * 统计任务完成率
     */
    @Override
    public Map<String, Object> getTaskCompletionStats(Long taskId) {
        Task task = this.getById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }

        // 统计各状态提交数量
        long totalSubmissions = submissionService.count(
                new LambdaQueryWrapper<Submission>().eq(Submission::getTaskId, taskId)
        );

        long submittedCount = submissionService.count(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getTaskId, taskId)
                        .in(Submission::getStatus, SubmissionStatus.SUBMITTED, SubmissionStatus.RESUBMITTED)
        );

        long gradedCount = submissionService.count(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getTaskId, taskId)
                        .eq(Submission::getStatus, SubmissionStatus.GRADED)
        );

        long returnedCount = submissionService.count(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getTaskId, taskId)
                        .eq(Submission::getStatus, SubmissionStatus.RETURNED)
        );

        long lateCount = submissionService.count(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getTaskId, taskId)
                        .eq(Submission::getIsLate, true)
        );

        // 计算平均分（仅已评分的）
        double avgScore = 0.0;
        if (gradedCount > 0) {
            List<Submission> gradedSubmissions = submissionService.list(
                    new LambdaQueryWrapper<Submission>()
                            .eq(Submission::getTaskId, taskId)
                            .eq(Submission::getStatus, SubmissionStatus.GRADED)
                            .isNotNull(Submission::getScore)
            );
            avgScore = gradedSubmissions.stream()
                    .mapToDouble(s -> s.getScore() != null ? s.getScore().doubleValue() : 0.0)
                    .average()
                    .orElse(0.0);
            avgScore = Math.round(avgScore * 100.0) / 100.0;
        }

        // 计算完成率（修复 P0-7）：分母应为"应提交人数=课程选课人数"，分子为"已提交学生人数"
        // 原实现用 gradedCount / totalSubmissions（实为已批改率且分母错误），现改为真实完成率。
        long submittedStudentCount = submissionService.list(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getTaskId, taskId)
                        .in(Submission::getStatus, SubmissionStatus.SUBMITTED, SubmissionStatus.RESUBMITTED, SubmissionStatus.GRADED, SubmissionStatus.RETURNED)
        ).stream().map(Submission::getStudentId).distinct().count();

        long enrolledCount = 0;
        if (task.getCourseId() != null) {
            enrolledCount = courseEnrollmentService.count(
                    new LambdaQueryWrapper<CourseEnrollment>()
                            .eq(CourseEnrollment::getCourseId, task.getCourseId())
                            .eq(CourseEnrollment::getIsActive, true));
        }

        double completionRate = 0.0;
        if (enrolledCount > 0) {
            completionRate = Math.round((double) submittedStudentCount / enrolledCount * 10000.0) / 100.0;
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("taskId", taskId);
        stats.put("taskTitle", task.getTitle());
        stats.put("totalSubmissions", totalSubmissions);
        stats.put("pendingReview", submittedCount); // 待批阅数量
        stats.put("gradedCount", gradedCount);
        stats.put("returnedCount", returnedCount);
        stats.put("lateCount", lateCount);
        stats.put("averageScore", avgScore);
        stats.put("completionRate", completionRate);
        stats.put("dueDate", task.getDueDate());
        stats.put("status", task.getStatus());

        return stats;
    }
}
