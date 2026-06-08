package com.aicourse.service;

import com.aicourse.entity.Notification;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 消息通知服务接口
 * 提供通知的发送、查询、已读标记、删除及事件驱动通知等核心能力
 */
public interface NotificationService extends IService<Notification> {

    /**
     * 发送单条通知（核心方法）
     * @param userId 接收通知的用户ID
     * @param title 通知标题
     * @param content 通知内容
     * @param category 消息分类（SYSTEM/ASSIGNMENT/EXAM/COURSE/ANNOUNCEMENT）
     * @param type 消息类型
     * @param priority 优先级（HIGH/MEDIUM/LOW）
     * @param actionUrl 点击跳转地址
     * @param data JSON扩展数据
     * @param relatedId 关联业务ID
     * @param relatedType 关联业务类型
     * @return 创建的通知实体
     */
    Notification sendNotification(Long userId, String title, String content, String category,
                                 String type, String priority, String actionUrl, String data,
                                 Long relatedId, String relatedType);

    /**
     * 批量发送通知
     * @param userIds 接收通知的用户ID列表
     * @param title 通知标题
     * @param content 通知内容
     * @param category 消息分类
     * @param type 消息类型
     * @param priority 优先级
     * @param actionUrl 点击跳转地址
     * @param data JSON扩展数据
     */
    void batchSendNotifications(List<Long> userIds, String title, String content, String category,
                               String type, String priority, String actionUrl, String data);

    /**
     * 标记单条通知为已读
     * @param notificationId 通知ID
     * @param userId 当前用户ID（权限校验）
     */
    void markAsRead(Long notificationId, Long userId);

    /**
     * 将用户所有未读通知标记为已读
     * @param userId 用户ID
     */
    void markAllAsRead(Long userId);

    /**
     * 获取用户未读通知数量
     * @param userId 用户ID
     * @return 未读数量
     */
    Long getUnreadCount(Long userId);

    /**
     * 分页获取用户通知列表（支持分类和已读状态筛选）
     * @param userId 用户ID
     * @param category 消息分类（可选）
     * @param isRead 是否已读（可选）
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页通知列表
     */
    Page<Notification> getUserNotifications(Long userId, String category, Boolean isRead, int page, int size);

    /**
     * 删除通知
     * @param notificationId 通知ID
     * @param userId 当前用户ID（权限校验）
     */
    void deleteNotification(Long notificationId, Long userId);

    /**
     * 事件驱动通知触发器
     * 根据业务事件类型自动创建并推送对应通知
     * @param eventType 事件类型（如 TASK_CREATED/EXAM_PUBLISHED/SUBMISSION_GRADED 等）
     * @param eventData 事件关联数据
     */
    void onEvent(String eventType, Map<String, Object> eventData);
}
