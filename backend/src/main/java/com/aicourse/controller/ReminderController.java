package com.aicourse.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Reminder;
import com.aicourse.service.ReminderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

import com.aicourse.common.ApiCode;
import com.aicourse.dto.ReminderCreateDTO;
import jakarta.validation.Valid;

/**
 * 提醒管理控制器
 * 提供提醒的创建、待办查询、取消、改期及定时发送等API
 */
@Tag(name = "催交提醒", description = "作业催交提醒的创建与管理")
@RestController
@RequestMapping("/reminders")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    /**
     * 创建提醒
     * @param dto 提醒创建请求DTO
     * @return 创建的提醒
     */
    @PostMapping("")
    public ApiResponse<Reminder> createReminder(@Valid @RequestBody ReminderCreateDTO dto) {
        Long userId = dto.getUserId();
        String reminderType = dto.getReminderType();
        Long taskId = dto.getTaskId();
        Long courseId = dto.getCourseId();
        String title = dto.getTitle();
        String message = dto.getMessage();
        LocalDateTime dueDate = dto.getDueDate() != null ?
                LocalDateTime.parse(dto.getDueDate()) : null;
        Integer triggerDay = dto.getTriggerDay();

        if (userId == null) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "接收用户ID不能为空");
        }
        if (reminderType == null || reminderType.isEmpty()) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "提醒类型不能为空");
        }
        if (title == null || title.isEmpty()) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "提醒标题不能为空");
        }
        if (dueDate == null) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "截止时间不能为空");
        }
        if (triggerDay == null) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "提前触发天数不能为空");
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
     * 获取待办提醒列表（管理端）
     * 管理员返回全部未取消提醒（按计划时间倒序，最多500条）；普通用户返回本人待办
     * @param auth 认证信息
     * @return 待办提醒列表
     */
    @GetMapping("/todos")
    public ApiResponse<List<Reminder>> getTodos(Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin) {
            List<Reminder> reminders = reminderService.lambdaQuery()
                    .ne(Reminder::getStatus, "CANCELLED")
                    .orderByDesc(Reminder::getScheduledAt)
                    .last("LIMIT 500")
                    .list();
            return ApiResponse.success(reminders);
        }
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(reminderService.getPendingReminders(userId));
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
     * @param dto 提醒创建请求DTO（仅使用newScheduledAt字段）
     * @return 操作结果
     */
    @PatchMapping("/{id}/reschedule")
    public ApiResponse<Void> rescheduleReminder(@PathVariable Long id, @Valid @RequestBody ReminderCreateDTO dto) {
        LocalDateTime newScheduledAt = dto.getNewScheduledAt() != null ?
                LocalDateTime.parse(dto.getNewScheduledAt()) : null;
        if (newScheduledAt == null) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "新的计划发送时间不能为空");
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
