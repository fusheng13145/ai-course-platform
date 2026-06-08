package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Reminder;
import com.aicourse.service.ReminderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 提醒管理控制器
 * 提供提醒的创建、待办查询、取消、改期及定时发送等API
 */
@RestController
@RequestMapping("/reminders")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    /**
     * 创建提醒
     * @param params 请求参数（包含 userId, reminderType, taskId, courseId, title, message, dueDate, triggerDay）
     * @return 创建的提醒
     */
    @PostMapping("")
    public ApiResponse<Reminder> createReminder(@RequestBody Map<String, Object> params) {
        Long userId = params.get("userId") != null ? Long.valueOf(params.get("userId").toString()) : null;
        String reminderType = (String) params.get("reminderType");
        Long taskId = params.get("taskId") != null ? Long.valueOf(params.get("taskId").toString()) : null;
        Long courseId = params.get("courseId") != null ? Long.valueOf(params.get("courseId").toString()) : null;
        String title = (String) params.get("title");
        String message = (String) params.get("message");
        LocalDateTime dueDate = params.get("dueDate") != null ?
                LocalDateTime.parse(params.get("dueDate").toString()) : null;
        Integer triggerDay = params.get("triggerDay") != null ?
                Integer.valueOf(params.get("triggerDay").toString()) : null;

        if (userId == null) {
            return ApiResponse.error(400, "接收用户ID不能为空");
        }
        if (reminderType == null || reminderType.isEmpty()) {
            return ApiResponse.error(400, "提醒类型不能为空");
        }
        if (title == null || title.isEmpty()) {
            return ApiResponse.error(400, "提醒标题不能为空");
        }
        if (dueDate == null) {
            return ApiResponse.error(400, "截止时间不能为空");
        }
        if (triggerDay == null) {
            return ApiResponse.error(400, "提前触发天数不能为空");
        }

        Reminder reminder = reminderService.createReminder(userId, reminderType, taskId, courseId,
                title, message, dueDate, triggerDay);
        return ApiResponse.success("创建成功", reminder);
    }

    /**
     * 获取当前用户的待办提醒列表
     * @param auth 认证信息
     * @return 待办提醒列表
     */
    @GetMapping("/pending")
    public ApiResponse<List<Reminder>> getPendingReminders(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<Reminder> reminders = reminderService.getPendingReminders(userId);
        return ApiResponse.success(reminders);
    }

    /**
     * 取消提醒
     * @param id 提醒ID
     * @return 操作结果
     */
    @PatchMapping("/{id}/cancel")
    public ApiResponse<Void> cancelReminder(@PathVariable Long id) {
        reminderService.cancelReminder(id);
        return ApiResponse.success("取消成功");
    }

    /**
     * 修改提醒的计划发送时间（改期）
     * @param id 提醒ID
     * @param params 请求参数（包含 newScheduledAt）
     * @return 操作结果
     */
    @PatchMapping("/{id}/reschedule")
    public ApiResponse<Void> rescheduleReminder(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        LocalDateTime newScheduledAt = params.get("newScheduledAt") != null ?
                LocalDateTime.parse(params.get("newScheduledAt").toString()) : null;
        if (newScheduledAt == null) {
            return ApiResponse.error(400, "新的计划发送时间不能为空");
        }
        reminderService.rescheduleReminder(id, newScheduledAt);
        return ApiResponse.success("改期成功");
    }

    /**
     * 发送到期提醒（定时任务调用，需管理员权限）
     * @return 已发送的提醒列表
     */
    @PostMapping("/send-due")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Reminder>> sendDueReminders() {
        List<Reminder> sentReminders = reminderService.sendDueReminders(LocalDateTime.now());
        return ApiResponse.success("发送完成", sentReminders);
    }
}
