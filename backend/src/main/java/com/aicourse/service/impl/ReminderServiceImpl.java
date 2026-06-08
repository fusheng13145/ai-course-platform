package com.aicourse.service.impl;

import com.aicourse.entity.Notification;
import com.aicourse.entity.Reminder;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ReminderMapper;
import com.aicourse.service.NotificationService;
import com.aicourse.service.ReminderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 提醒服务实现类
 * 实现提醒的创建、发送、查询、取消及改期等核心逻辑
 */
@Service
public class ReminderServiceImpl extends ServiceImpl<ReminderMapper, Reminder> implements ReminderService {

    private final NotificationService notificationService;

    public ReminderServiceImpl(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 创建提醒
     * 根据截止时间和提前天数计算计划发送时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Reminder createReminder(Long userId, String reminderType, Long taskId, Long courseId,
                                   String title, String message, LocalDateTime dueDate, Integer triggerDay) {
        // 计算计划发送时间：截止时间减去提前天数
        LocalDateTime scheduledAt = dueDate.minusDays(triggerDay);

        Reminder reminder = new Reminder();
        reminder.setReminderType(reminderType);
        reminder.setStatus("PENDING");
        reminder.setUserId(userId);
        reminder.setTaskId(taskId);
        reminder.setCourseId(courseId);
        reminder.setTitle(title);
        reminder.setMessage(message);
        reminder.setDueDate(dueDate);
        reminder.setTriggerDay(triggerDay);
        reminder.setScheduledAt(scheduledAt);
        this.save(reminder);
        return reminder;
    }

    /**
     * 发送到期提醒（定时任务调用）
     * 查找所有满足发送条件的提醒，逐个发送通知并更新状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Reminder> sendDueReminders(LocalDateTime now) {
        // 查询状态为待发送且计划发送时间已到的提醒
        List<Reminder> dueReminders = this.list(
                new LambdaQueryWrapper<Reminder>()
                        .eq(Reminder::getStatus, "PENDING")
                        .le(Reminder::getScheduledAt, now)
        );

        List<Reminder> sentReminders = new ArrayList<>();
        for (Reminder reminder : dueReminders) {
            // 通过通知服务发送提醒消息
            notificationService.sendNotification(
                    reminder.getUserId(),
                    reminder.getTitle(),
                    reminder.getMessage(),
                    "SYSTEM",
                    reminder.getReminderType(),
                    "MEDIUM",
                    null,
                    null,
                    reminder.getTaskId() != null ? reminder.getTaskId() : reminder.getCourseId(),
                    reminder.getTaskId() != null ? "TASK" : "COURSE"
            );

            // 更新提醒状态为已发送
            reminder.setStatus("SENT");
            reminder.setSentAt(now);
            this.updateById(reminder);
            sentReminders.add(reminder);
        }
        return sentReminders;
    }

    /**
     * 获取用户的待办提醒列表
     */
    @Override
    public List<Reminder> getPendingReminders(Long userId) {
        return this.list(
                new LambdaQueryWrapper<Reminder>()
                        .eq(Reminder::getUserId, userId)
                        .eq(Reminder::getStatus, "PENDING")
                        .orderByAsc(Reminder::getScheduledAt)
        );
    }

    /**
     * 取消提醒
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelReminder(Long reminderId) {
        Reminder reminder = this.getById(reminderId);
        if (reminder == null) {
            throw new BusinessException("提醒不存在");
        }
        if ("SENT".equals(reminder.getStatus()) || "CANCELLED".equals(reminder.getStatus())) {
            throw new BusinessException("该提醒已发送或已取消，无法再次操作");
        }
        reminder.setStatus("CANCELLED");
        this.updateById(reminder);
    }

    /**
     * 修改提醒的计划发送时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rescheduleReminder(Long reminderId, LocalDateTime newScheduledAt) {
        Reminder reminder = this.getById(reminderId);
        if (reminder == null) {
            throw new BusinessException("提醒不存在");
        }
        if ("SENT".equals(reminder.getStatus()) || "CANCELLED".equals(reminder.getStatus())) {
            throw new BusinessException("该提醒已发送或已取消，无法改期");
        }
        reminder.setScheduledAt(newScheduledAt);
        this.updateById(reminder);
    }
}
