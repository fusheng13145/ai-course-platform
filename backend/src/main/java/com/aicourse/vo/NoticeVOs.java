package com.aicourse.vo;

import com.aicourse.entity.Announcement;
import com.aicourse.entity.Reminder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提醒与公告出参。
 *
 * <p>字段与实体一一对应、零删减。提醒端点是「本人提醒」，公告按可见范围过滤
 * （`pageVisible` / `getVisible`），两者都是行级权限，不靠序列化层裁剪。
 */
public final class NoticeVOs {

    private NoticeVOs() {
    }

    /** 提醒出参。 */
    @Data
    public static class ReminderVO {
        private Long id;
        private String reminderType;
        private String status;
        private Long userId;
        private Long taskId;
        private Long courseId;
        private String title;
        private String message;
        private LocalDateTime dueDate;
        private Integer triggerDay;
        private LocalDateTime scheduledAt;
        private LocalDateTime sentAt;
        private LocalDateTime createdAt;

        public static ReminderVO from(Reminder reminder) {
            if (reminder == null) {
                return null;
            }
            ReminderVO vo = new ReminderVO();
            vo.setId(reminder.getId());
            vo.setReminderType(reminder.getReminderType());
            vo.setStatus(reminder.getStatus());
            vo.setUserId(reminder.getUserId());
            vo.setTaskId(reminder.getTaskId());
            vo.setCourseId(reminder.getCourseId());
            vo.setTitle(reminder.getTitle());
            vo.setMessage(reminder.getMessage());
            vo.setDueDate(reminder.getDueDate());
            vo.setTriggerDay(reminder.getTriggerDay());
            vo.setScheduledAt(reminder.getScheduledAt());
            vo.setSentAt(reminder.getSentAt());
            vo.setCreatedAt(reminder.getCreatedAt());
            return vo;
        }

        public static List<ReminderVO> from(List<Reminder> reminders) {
            return reminders == null ? List.of() : reminders.stream().map(ReminderVO::from).toList();
        }
    }

    /** 公告出参。 */
    @Data
    public static class AnnouncementVO {
        private Long id;
        private Long courseId;
        private Long teachingClassId;
        private Long adminClassId;
        private String title;
        private String content;
        private Long publisherId;
        private String publisherRole;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static AnnouncementVO from(Announcement announcement) {
            if (announcement == null) {
                return null;
            }
            AnnouncementVO vo = new AnnouncementVO();
            vo.setId(announcement.getId());
            vo.setCourseId(announcement.getCourseId());
            vo.setTeachingClassId(announcement.getTeachingClassId());
            vo.setAdminClassId(announcement.getAdminClassId());
            vo.setTitle(announcement.getTitle());
            vo.setContent(announcement.getContent());
            vo.setPublisherId(announcement.getPublisherId());
            vo.setPublisherRole(announcement.getPublisherRole());
            vo.setCreatedAt(announcement.getCreatedAt());
            vo.setUpdatedAt(announcement.getUpdatedAt());
            return vo;
        }

        public static List<AnnouncementVO> from(List<Announcement> announcements) {
            return announcements == null ? List.of() : announcements.stream().map(AnnouncementVO::from).toList();
        }
    }
}
