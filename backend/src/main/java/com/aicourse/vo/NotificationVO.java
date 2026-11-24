package com.aicourse.vo;

import com.aicourse.entity.Notification;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知出参（14 字段，实体镜像）。
 */
@Data
public class NotificationVO {

    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String category;
    private String type;
    private Boolean isRead;
    private String priority;
    private String actionUrl;
    private String data;
    private Long relatedId;
    private String relatedType;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    public static NotificationVO from(Notification notification) {
        if (notification == null) {
            return null;
        }
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setUserId(notification.getUserId());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setCategory(notification.getCategory());
        vo.setType(notification.getType());
        vo.setIsRead(notification.getIsRead());
        vo.setPriority(notification.getPriority());
        vo.setActionUrl(notification.getActionUrl());
        vo.setData(notification.getData());
        vo.setRelatedId(notification.getRelatedId());
        vo.setRelatedType(notification.getRelatedType());
        vo.setCreatedAt(notification.getCreatedAt());
        vo.setReadAt(notification.getReadAt());
        return vo;
    }

    public static List<NotificationVO> from(List<Notification> notifications) {
        return notifications == null ? List.of() : notifications.stream().map(NotificationVO::from).toList();
    }
}
