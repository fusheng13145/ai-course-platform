package com.aicourse.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.common.PagedResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.entity.Notification;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.service.NotificationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.aicourse.common.ApiCode;
import com.aicourse.dto.NotificationSendDTO;
import jakarta.validation.Valid;

/**
 * 消息通知接口控制器
 * 提供通知列表查询、未读数量、已读标记、删除及手动发送等API
 */
@Tag(name = "通知管理", description = "通知列表、未读计数、发送通知")
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 分页获取通知列表（支持分类和已读状态筛选）
     * @param auth 认证信息
     * @param category 消息分类（可选）
     * @param isRead 是否已读（可选）
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页通知列表
     */
    @GetMapping("")
    public ApiResponse<PagedResponse<Notification>> getUserNotifications(
            Authentication auth,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = (Long) auth.getPrincipal();
        Page<Notification> result = notificationService.getUserNotifications(userId, category, isRead, page, size);
        return ApiResponse.success(PagedResponse.of(result));
    }

    /**
     * 获取当前用户未读通知数量
     * @param auth 认证信息
     * @return 未读数量
     */
    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Long count = notificationService.getUnreadCount(userId);
        return ApiResponse.success(count);
    }

    /**
     * 标记单条通知为已读
     * @param id 通知ID
     * @param auth 认证信息
     * @return 操作结果
     */
    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        notificationService.markAsRead(id, userId);
        return ApiResponse.success("标记已读成功");
    }

    /**
     * 将所有未读通知标记为已读
     * @param auth 认证信息
     * @return 操作结果
     */
    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        notificationService.markAllAsRead(userId);
        return ApiResponse.success("全部标记已读成功");
    }

    /**
     * 删除单条通知
     * @param id 通知ID
     * @param auth 认证信息
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNotification(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        notificationService.deleteNotification(id, userId);
        return ApiResponse.success("删除成功");
    }

    /**
     * 手动发送通知（仅管理员）
     * @param dto 通知发送请求DTO
     * @return 发送结果
     */
    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> sendNotification(@Valid @RequestBody NotificationSendDTO dto) {
        if (dto.getTargetUserIds() == null || dto.getTargetUserIds().isEmpty()) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "接收用户列表不能为空");
        }
        if (dto.getTitle() == null || dto.getTitle().isEmpty()) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "通知标题不能为空");
        }
        if (dto.getContent() == null || dto.getContent().isEmpty()) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "通知内容不能为空");
        }

        notificationService.batchSendNotifications(dto.getTargetUserIds(), dto.getTitle(), dto.getContent(),
                dto.getCategory(),
                dto.getType() != null ? dto.getType() : "SYSTEM",
                dto.getPriority() != null ? dto.getPriority() : "MEDIUM",
                dto.getActionUrl(), dto.getData());
        return ApiResponse.success("发送成功");
    }
}
