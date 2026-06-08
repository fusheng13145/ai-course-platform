package com.aicourse.service;

import com.aicourse.entity.Reminder;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提醒服务接口
 * 提供提醒的创建、发送、查询、取消及改期等能力，支持定时任务调用
 */
public interface ReminderService extends IService<Reminder> {

    /**
     * 创建提醒
     * @param userId 接收提醒的用户ID
     * @param reminderType 提醒类型（DEADLINE/EXAM/COURSE_UPDATE/SYSTEM）
     * @param taskId 关联任务ID（可选）
     * @param courseId 关联课程ID（可选）
     * @param title 提醒标题
     * @param message 提醒内容
     * @param dueDate 截止时间
     * @param triggerDay 提前触发天数
     * @return 创建的提醒实体
     */
    Reminder createReminder(Long userId, String reminderType, Long taskId, Long courseId,
                           String title, String message, LocalDateTime dueDate, Integer triggerDay);

    /**
     * 发送到期提醒（定时任务调用）
     * 查找 scheduledAt <= now 且 status=PENDING 的提醒，逐个发送通知并更新状态
     * @param now 当前时间
     * @return 已发送的提醒列表
     */
    List<Reminder> sendDueReminders(LocalDateTime now);

    /**
     * 获取用户的待办提醒列表
     * @param userId 用户ID
     * @return 待办提醒列表
     */
    List<Reminder> getPendingReminders(Long userId);

    /**
     * 取消提醒
     * @param reminderId 提醒ID
     */
    void cancelReminder(Long reminderId);

    /**
     * 修改提醒的计划发送时间
     * @param reminderId 提醒ID
     * @param newScheduledAt 新的计划发送时间
     */
    void rescheduleReminder(Long reminderId, LocalDateTime newScheduledAt);
}
