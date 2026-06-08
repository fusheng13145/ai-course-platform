package com.aicourse.service;

import com.aicourse.entity.Task;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface TaskService extends IService<Task> {

    /**
     * 发布任务
     * @param taskId 任务ID
     * @return 发布后的任务
     */
    Task publishTask(Long taskId);

    /**
     * 延长截止日期
     * @param taskId 任务ID
     * @param newDueDate 新的截止日期
     * @param reason 延长原因
     * @return 更新后的任务
     */
    Task extendDeadline(Long taskId, LocalDateTime newDueDate, String reason);

    /**
     * 复制任务
     * @param sourceTaskId 源任务ID
     * @param newTitle 新标题
     * @return 复制后的新任务
     */
    Task copyTask(Long sourceTaskId, String newTitle);

    /**
     * 获取待批阅的任务列表
     * @param teacherId 教师ID
     * @return 待批阅任务列表
     */
    List<Task> getPendingGradingTasks(Long teacherId);

    /**
     * 统计任务完成率
     * @param taskId 任务ID
     * @return 统计数据
     */
    Map<String, Object> getTaskCompletionStats(Long taskId);
}
