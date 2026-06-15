package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Notification;
import com.aicourse.service.NotificationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 消息通知接口控制器
 * 提供通知列表查询、未读数量、已读标记、删除及手动发送等API
 */
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
     * @param params 请求参数（包含 userIds, title, content, category, type, priority, actionUrl, data）
     * @return 发送结果
     */
    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> sendNotification(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Long> userIds = (List<Long>) params.get("userIds");
        String title = (String) params.get("title");
        String content = (String) params.get("content");
        String category = (String) params.get("category");
        String type = (String) params.get("type");
        String priority = (String) params.get("priority");
        String actionUrl = (String) params.get("actionUrl");
        String data = (String) params.get("data");

        if (userIds == null || userIds.isEmpty()) {
            return ApiResponse.error(400, "接收用户列表不能为空");
        }
        if (title == null || title.isEmpty()) {
            return ApiResponse.error(400, "通知标题不能为空");
        }
        if (content == null || content.isEmpty()) {
            return ApiResponse.error(400, "通知内容不能为空");
        }

        notificationService.batchSendNotifications(userIds, title, content, category,
                type != null ? type : "SYSTEM",
                priority != null ? priority : "MEDIUM",
                actionUrl, data);
        return ApiResponse.success("发送成功");
    }
}
