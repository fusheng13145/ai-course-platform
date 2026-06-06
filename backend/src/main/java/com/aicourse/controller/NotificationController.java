package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Notification;
import com.aicourse.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<Notification>> list(Authentication auth,
                                                  @RequestParam(defaultValue = "0") int skip,
                                                  @RequestParam(defaultValue = "20") int limit,
                                                  @RequestParam(required = false) Boolean unreadOnly,
                                                  @RequestParam(required = false) String category) {
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
            .eq(Notification::getUserId, userId)
            .eq(unreadOnly != null && unreadOnly, Notification::getIsRead, false)
            .eq(category != null, Notification::getCategory, category)
            .orderByDesc(Notification::getCreatedAt)
            .last("LIMIT " + limit + " OFFSET " + skip);
        return ApiResponse.success(notificationService.list(wrapper));
    }

    @GetMapping("/summary")
    public ApiResponse<Object> summary(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        long unread = notificationService.count(
            new LambdaQueryWrapper<Notification>().eq(Notification::getUserId, userId).eq(Notification::getIsRead, false));
        return ApiResponse.success(java.util.Map.of("unreadCount", unread));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        Notification n = notificationService.getById(id);
        if (n != null) {
            n.setIsRead(true);
            notificationService.updateById(n);
        }
        return ApiResponse.success("标记已读成功", null);
    }

    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Notification> send(@RequestBody Notification notification, Authentication auth) {
        notification.setCreatedAt(java.time.LocalDateTime.now());
        notificationService.save(notification);
        return ApiResponse.success("发送成功", notification);
    }
}
