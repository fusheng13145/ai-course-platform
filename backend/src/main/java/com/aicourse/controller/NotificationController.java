package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Notification;
import com.aicourse.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 消息通知接口控制器
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 分页查询消息列表
     * @param auth 认证信息
     * @param skip 偏移量
     * @param limit 每页条数
     * @param unreadOnly 是否仅查询未读消息
     * @param category 消息分类
     * @return 消息列表
     */
    @GetMapping
    public ApiResponse<List<Notification>> list(
            Authentication auth,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(required = false) String category) {
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<Notification> queryWrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(unreadOnly != null && unreadOnly, Notification::getIsRead, false)
                .eq(category != null, Notification::getCategory, category)
                .orderByDesc(Notification::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + skip);
        List<Notification> notificationList = notificationService.list(queryWrapper);
        return ApiResponse.success(notificationList);
    }

    /**
     * 查询消息统计摘要
     * @param auth 认证信息
     * @return 未读消息数量
     */
    @GetMapping("/summary")
    public ApiResponse<Object> summary(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<Notification> queryWrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false);
        long unreadCount = notificationService.count(queryWrapper);
        return ApiResponse.success(Map.of("unreadCount", unreadCount));
    }

    /**
     * 单条消息标记为已读
     * @param id 消息ID
     * @return 操作结果
     */
    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        Notification notification = notificationService.getById(id);
        if (notification != null) {
            notification.setIsRead(true);
            notificationService.updateById(notification);
        }
        return ApiResponse.success("标记已读成功", null);
    }

    /**
     * 全部消息标记为已读
     * @param auth 认证信息
     * @return 操作结果
     */
    @PutMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        notificationService.lambdaUpdate()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false)
                .set(Notification::getIsRead, true)
                .update();
        return ApiResponse.success("全部标记已读成功", null);
    }

    /**
     * 删除单条消息
     * @param id 消息ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        notificationService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 发送消息（仅管理员）
     * @param notification 消息内容
     * @param auth 认证信息
     * @return 发送后的消息数据
     */
    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Notification> send(@RequestBody Notification notification, Authentication auth) {
        notification.setCreatedAt(LocalDateTime.now());
        notificationService.save(notification);
        return ApiResponse.success("发送成功", notification);
    }
}