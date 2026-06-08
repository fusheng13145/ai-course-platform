package com.aicourse.service.impl;

import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.TaskMapper;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements TaskService {

    private final SubmissionService submissionService;

    @Autowired
    public TaskServiceImpl(@Lazy SubmissionService submissionService) {
        this.submissionService = submissionService;
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

        return task;
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
                    .in(Submission::getStatus, "SUBMITTED", "RESUBMITTED");
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
                        .in(Submission::getStatus, "SUBMITTED", "RESUBMITTED")
        );

        long gradedCount = submissionService.count(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getTaskId, taskId)
                        .eq(Submission::getStatus, "GRADED")
        );

        long returnedCount = submissionService.count(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getTaskId, taskId)
                        .eq(Submission::getStatus, "RETURNED")
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
                            .eq(Submission::getStatus, "GRADED")
                            .isNotNull(Submission::getScore)
            );
            avgScore = gradedSubmissions.stream()
                    .mapToDouble(s -> s.getScore() != null ? s.getScore().doubleValue() : 0.0)
                    .average()
                    .orElse(0.0);
            avgScore = Math.round(avgScore * 100.0) / 100.0;
        }

        // 计算完成率
        double completionRate = 0.0;
        // 这里假设通过课程注册表获取学生总数，简化处理用提交数作为参考
        if (totalSubmissions > 0) {
            completionRate = Math.round((double) gradedCount / totalSubmissions * 10000.0) / 100.0;
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
