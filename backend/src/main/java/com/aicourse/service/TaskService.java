package com.aicourse.service;

import com.aicourse.entity.Task;
import com.aicourse.vo.TaskCompletionStatsVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;
import java.util.List;


/**
 * 任务接口：相关业务逻辑入口，提供增删改查与编排能力。
 */
public interface TaskService extends IService<Task> {

    /**
     * 发布任务
     * @param taskId 任务ID
     * @return 发布后的任务
     */
    Task publishTask(Long taskId);

    /**
     * 仅执行写操作的任务更新（权限已由调用方校验），返回更新后任务。
     * @param id    任务ID
     * @param task  待更新数据
     * @return 更新后的任务
     */
    Task updateTaskById(Long id, Task task);

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
     * @return 任务完成率统计
     */
    TaskCompletionStatsVO getTaskCompletionStats(Long taskId);
}
