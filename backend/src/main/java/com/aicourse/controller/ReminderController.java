package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Reminder;
import com.aicourse.service.ReminderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

import com.aicourse.dto.ReminderCreateDTO;
import com.aicourse.dto.ReminderUpdateDTO;
import com.aicourse.exception.BusinessException;
import jakarta.validation.Valid;

/**
 * 提醒管理控制器
 * 提供提醒的创建、待办查询、取消、改期及定时发送等API
 */
@Tag(name = "催交提醒", description = "作业催交提醒的创建与管理")
@RestController
@RequestMapping("/reminders")
public class ReminderController {

    private static final Logger log = LoggerFactory.getLogger(ReminderController.class);

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    /**
     * 创建提醒（修复 P1-3 越权：仅 ADMIN/TEACHER 可指定接收人；普通用户只能给自己创建）
     * @param dto 提醒创建请求DTO
     * @param auth 认证信息
     * @return 创建的提醒
     */
    @PostMapping("")
    public ApiResponse<Reminder> createReminder(@Valid @RequestBody ReminderCreateDTO dto, Authentication auth) {
        Long currentUserId = (Long) auth.getPrincipal();
        boolean privileged = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_TEACHER".equals(a.getAuthority()));
        Long userId = privileged ? dto.getUserId() : currentUserId;
        if (userId == null) {
            userId = currentUserId;
        }
        String reminderType = dto.getReminderType();
        Long taskId = dto.getTaskId();
        Long courseId = dto.getCourseId();
        String title = dto.getTitle();
        String message = dto.getMessage();
        LocalDateTime dueDate = dto.getDueDate() != null ?
                LocalDateTime.parse(dto.getDueDate()) : null;
        Integer triggerDay = dto.getTriggerDay();

        if (reminderType == null || reminderType.isEmpty()) {
            throw new BusinessException("提醒类型不能为空");
        }
        if (title == null || title.isEmpty()) {
            throw new BusinessException("提醒标题不能为空");
        }
        if (dueDate == null) {
            throw new BusinessException("截止时间不能为空");
        }
        if (triggerDay == null) {
            throw new BusinessException("提前触发天数不能为空");
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
     * 取消提醒（修复 P1-3 越权：非 ADMIN 仅能操作自己的提醒）
     * @param id 提醒ID
     * @return 操作结果
     */
    @PatchMapping("/{id}/cancel")
    public ApiResponse<Void> cancelReminder(@PathVariable Long id, Authentication auth) {
        assertOwner(id, auth);
        reminderService.cancelReminder(id);
        return ApiResponse.success("取消成功");
    }

    /**
     * 修改提醒的计划发送时间（改期）（修复 P1-3 越权：非 ADMIN 仅能操作自己的提醒）
     * @param id 提醒ID
     * @param dto 提醒创建请求DTO（仅使用newScheduledAt字段）
     * @return 操作结果
     */
    @PatchMapping("/{id}/reschedule")
    public ApiResponse<Void> rescheduleReminder(@PathVariable Long id, @Valid @RequestBody ReminderCreateDTO dto,
                                                Authentication auth) {
        assertOwner(id, auth);
        LocalDateTime newScheduledAt = dto.getNewScheduledAt() != null ?
                LocalDateTime.parse(dto.getNewScheduledAt()) : null;
        if (newScheduledAt == null) {
            throw new BusinessException("新的计划发送时间不能为空");
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

    /**
     * 更新提醒（局部更新）（修复 P1-3 越权：非 ADMIN 仅能操作自己的提醒）
     * 字段命名与前端编辑表单对齐：title/content/remindAt/targetUserId。
     * @param id 提醒ID
     * @param dto 更新字段（仅更新非 null 字段）
     * @return 更新后的提醒
     */
    @PutMapping("/{id}")
    public ApiResponse<Reminder> updateReminder(@PathVariable Long id, @Valid @RequestBody ReminderUpdateDTO dto,
                                                Authentication auth) {
        assertOwner(id, auth);
        Reminder updated = reminderService.updateReminder(id, dto);
        return ApiResponse.success("更新成功", updated);
    }

    /**
     * 归属断言（修复 P1-3）：ADMIN 可操作全部提醒；普通用户仅能操作本人的提醒，否则 403。
     */
    private void assertOwner(Long reminderId, Authentication auth) {
        Reminder reminder = reminderService.getById(reminderId);
        if (reminder == null) {
            throw new com.aicourse.exception.BusinessException("提醒不存在");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin && !reminder.getUserId().equals((Long) auth.getPrincipal())) {
            throw new com.aicourse.exception.BusinessException(403, "无权操作该提醒");
        }
    }
}
