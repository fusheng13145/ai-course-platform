package com.aicourse.service.impl;


import com.aicourse.common.ApiCode;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Notification;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.NotificationMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.NotificationService;
import com.aicourse.websocket.WebSocketSessionManager;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.springframework.web.util.HtmlUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息通知服务实现类
 * 实现通知的发送、查询、已读标记、删除及事件驱动通知等核心逻辑
 */
@Service
@Slf4j
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements NotificationService {


    private final CourseEnrollmentService courseEnrollmentService;
    private final WebSocketSessionManager webSocketSessionManager;

    public NotificationServiceImpl(CourseEnrollmentService courseEnrollmentService,
                                   WebSocketSessionManager webSocketSessionManager) {
        this.courseEnrollmentService = courseEnrollmentService;
        this.webSocketSessionManager = webSocketSessionManager;
    }

    /**
     * 构建通知实体（不落库）
     * 供单条发送与批量插入复用，避免重复拼接字段
     */
    private Notification buildNotification(Long userId, String title, String content, String category,
                                            String type, String priority, String actionUrl, String data,
                                            Long relatedId, String relatedType) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setCategory(category);
        notification.setType(type);
        notification.setPriority(priority);
        notification.setActionUrl(actionUrl);
        notification.setData(data);
        notification.setRelatedId(relatedId);
        notification.setRelatedType(relatedType);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        return notification;
    }

    /**
     * 发送单条通知
     * 创建通知实体并保存，默认未读状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Notification sendNotification(Long userId, String title, String content, String category,
                                        String type, String priority, String actionUrl, String data,
                                        Long relatedId, String relatedType) {
                                            log.info("发送通知");

        Notification notification = buildNotification(userId, title, content, category, type, priority, actionUrl, data, relatedId, relatedType);
        this.save(notification);
        pushRealtime(notification);
        return notification;
    }

    /**
     * 批量发送通知
     * 构建通知列表后一次性 saveBatch 批量插入，避免逐条 save 产生的 N 次单条 INSERT
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSendNotifications(List<Long> userIds, String title, String content, String category,
                                       String type, String priority, String actionUrl, String data) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<Notification> notifications = new ArrayList<>(userIds.size());
        for (Long userId : userIds) {
            notifications.add(buildNotification(userId, title, content, category, type, priority, actionUrl, data, null, null));
        }
        this.saveBatch(notifications);
        for (Notification n : notifications) {
            pushRealtime(n);
        }
    }

    /**
     * 实时推送单条通知到收件人在线连接（Best-Effort）。
     * 令牌已在握手阶段校验，此处仅做尽力推送；失败不影响通知落库。
     */
    private void pushRealtime(Notification notification) {
        if (notification == null || notification.getUserId() == null) {
            return;
        }
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("id", notification.getId());
            data.put("title", HtmlUtils.htmlEscape(notification.getTitle() != null ? notification.getTitle() : ""));
            data.put("content", HtmlUtils.htmlEscape(notification.getContent() != null ? notification.getContent() : ""));
            data.put("category", notification.getCategory());
            data.put("type", notification.getType());
            data.put("priority", notification.getPriority());
            if (notification.getActionUrl() != null) data.put("actionUrl", notification.getActionUrl());
            if (notification.getRelatedId() != null) data.put("relatedId", notification.getRelatedId());
            if (notification.getRelatedType() != null) data.put("relatedType", notification.getRelatedType());
            if (notification.getCreatedAt() != null) data.put("createdAt", notification.getCreatedAt().toString());

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "notification.new");
            payload.put("data", data);

            webSocketSessionManager.sendToUser(notification.getUserId(), payload);
        } catch (Exception e) {
            log.warn("实时推送通知失败: notificationId={}, userId={}", notification.getId(), notification.getUserId(), e);
        }
    }

    /**
     * 标记单条通知为已读
     * 校验通知归属权后更新已读状态和读取时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = this.getById(notificationId);
        if (notification == null) {
            throw new BusinessException("通知不存在");
        }
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权操作该通知");
        }
        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());
        this.updateById(notification);
    }

    /**
     * 将用户所有未读通知批量标记为已读
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(Long userId) {
        this.lambdaUpdate()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false)
                .set(Notification::getIsRead, true)
                .set(Notification::getReadAt, LocalDateTime.now())
                .update();
    }

    /**
     * 获取用户未读通知数量
     */
    @Override
    public Long getUnreadCount(Long userId) {
        return this.count(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false));
    }

    /**
     * 分页获取用户通知列表（支持分类和已读状态筛选）
     */
    @Override
    public Page<Notification> getUserNotifications(Long userId, String category, Boolean isRead, int page, int size) {
        Page<Notification> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Notification> queryWrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(category != null && !category.isEmpty(), Notification::getCategory, category)
                .eq(isRead != null, Notification::getIsRead, isRead)
                .orderByDesc(Notification::getCreatedAt);
        Page<Notification> result = this.page(pageParam, queryWrapper);
        // XSS输出编码
        if (result.getRecords() != null) {
            for (Notification n : result.getRecords()) {
                if (n.getTitle() != null) {
                    n.setTitle(HtmlUtils.htmlEscape(n.getTitle()));
                }
                if (n.getContent() != null) {
                    n.setContent(HtmlUtils.htmlEscape(n.getContent()));
                }
            }
        }
        return result;
    }

    /**
     * 删除通知（校验归属权）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotification(Long notificationId, Long userId) {
        log.info("删除通知");

        Notification notification = this.getById(notificationId);
        if (notification == null) {
            throw new BusinessException("通知不存在");
        }
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权删除该通知");
        }
        this.removeById(notificationId);
    }

    /**
     * 事件驱动通知触发器
     * 根据业务事件类型自动创建并推送对应通知给相关用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onEvent(String eventType, Map<String, Object> eventData) {
        switch (eventType) {
            case "TASK_CREATED":
                // 任务创建通知：给选课学生发送作业通知
                handleTaskCreated(eventData);
                break;
            case "EXAM_PUBLISHED":
                // 考试发布通知：给选课学生发送考试通知
                handleExamPublished(eventData);
                break;
            case "SUBMISSION_GRADED":
                // 作业批阅完成通知：给学生发送成绩通知
                handleSubmissionGraded(eventData);
                break;
            case "DISCUSSION_REPLIED":
                // 讨论回复通知：通知被回复者/话题发起者
                handleDiscussionReplied(eventData);
                break;
            case "DISCUSSION_MENTIONED":
                // 讨论@提及通知：通知被@的用户
                handleDiscussionMentioned(eventData);
                break;
            default:
                // 未知事件类型不做处理
                break;
        }
    }

    /**
     * 处理任务创建事件
     * 查询课程下所有选课学生，批量发送任务创建通知（saveBatch 一次多行插入）
     */
    private void handleTaskCreated(Map<String, Object> eventData) {
        Long courseId = eventData.get("courseId") != null ? Long.valueOf(eventData.get("courseId").toString()) : null;
        String taskTitle = eventData.get("taskTitle") != null ? eventData.get("taskTitle").toString() : "";
        Long taskId = eventData.get("taskId") != null ? Long.valueOf(eventData.get("taskId").toString()) : null;
        if (courseId == null) {
            return;
        }
        // 查询课程下的所有选课学生
        List<CourseEnrollment> enrollments = courseEnrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getCourseId, courseId)
        );
        if (enrollments == null || enrollments.isEmpty()) {
            return;
        }
        List<Notification> notifications = new ArrayList<>(enrollments.size());
        for (CourseEnrollment enrollment : enrollments) {
            notifications.add(buildNotification(
                    enrollment.getStudentId(),
                    "新作业发布",
                    "课程发布了新作业：" + taskTitle + "，请及时完成提交。",
                    "ASSIGNMENT",
                    "TASK_CREATED",
                    "MEDIUM",
                    "/tasks/" + taskId,
                    null,
                    taskId,
                    "TASK"
            ));
        }
        this.saveBatch(notifications);
    }

    /**
     * 处理考试发布事件
     * 查询课程下所有选课学生，批量发送考试通知（saveBatch 一次多行插入）
     */
    private void handleExamPublished(Map<String, Object> eventData) {
        Long courseId = eventData.get("courseId") != null ? Long.valueOf(eventData.get("courseId").toString()) : null;
        String examTitle = eventData.get("examTitle") != null ? eventData.get("examTitle").toString() : "";
        Long examId = eventData.get("examId") != null ? Long.valueOf(eventData.get("examId").toString()) : null;
        if (courseId == null) {
            return;
        }
        List<CourseEnrollment> enrollments = courseEnrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getCourseId, courseId)
        );
        if (enrollments == null || enrollments.isEmpty()) {
            return;
        }
        List<Notification> notifications = new ArrayList<>(enrollments.size());
        for (CourseEnrollment enrollment : enrollments) {
            notifications.add(buildNotification(
                    enrollment.getStudentId(),
                    "新考试通知",
                    "课程发布了新考试：" + examTitle + "，请提前做好准备。",
                    "EXAM",
                    "EXAM_PUBLISHED",
                    "HIGH",
                    "/exams/" + examId,
                    null,
                    examId,
                    "EXAM"
            ));
        }
        this.saveBatch(notifications);
    }

    /**
     * 处理作业批阅完成事件
     * 给对应学生发送成绩通知（单收件人，单条发送即可）
     */
    private void handleSubmissionGraded(Map<String, Object> eventData) {
        Long studentId = eventData.get("studentId") != null ? Long.valueOf(eventData.get("studentId").toString()) : null;
        String taskTitle = eventData.get("taskTitle") != null ? eventData.get("taskTitle").toString() : "";
        Long submissionId = eventData.get("submissionId") != null ? Long.valueOf(eventData.get("submissionId").toString()) : null;
        if (studentId == null) {
            return;
        }
        sendNotification(
                studentId,
                "作业批阅完成",
                "您的作业《" + taskTitle + "》已完成批阅，请查看评分和反馈。",
                "ASSIGNMENT",
                "SUBMISSION_GRADED",
                "MEDIUM",
                    "/submissions/" + submissionId,
                null,
                submissionId,
                    "SUBMISSION"
        );
    }

    /**
     * 处理讨论回复事件
     * 给被回复者（或话题发起者）发送讨论回复通知（单收件人，单条发送即可）
     */
    private void handleDiscussionReplied(Map<String, Object> eventData) {
        Long recipientId = eventData.get("recipientId") != null ? Long.valueOf(eventData.get("recipientId").toString()) : null;
        Long topicId = eventData.get("topicId") != null ? Long.valueOf(eventData.get("topicId").toString()) : null;
        String topicTitle = eventData.get("topicTitle") != null ? eventData.get("topicTitle").toString() : "";
        Long postId = eventData.get("postId") != null ? Long.valueOf(eventData.get("postId").toString()) : null;
        if (recipientId == null) {
            return;
        }
        sendNotification(
                recipientId,
                "收到新的讨论回复",
                "您在讨论《" + topicTitle + "》中收到了新的回复，点击查看详情。",
                "DISCUSSION",
                "DISCUSSION_REPLIED",
                "MEDIUM",
                "/discussion/topic/" + topicId,
                null,
                postId,
                "DISCUSSION_POST"
        );
    }

    /**
     * 处理讨论@提及事件
     * 给被@的用户发送提及通知（单收件人，单条发送即可）
     */
    private void handleDiscussionMentioned(Map<String, Object> eventData) {
        Long recipientId = eventData.get("recipientId") != null ? Long.valueOf(eventData.get("recipientId").toString()) : null;
        Long topicId = eventData.get("topicId") != null ? Long.valueOf(eventData.get("topicId").toString()) : null;
        String topicTitle = eventData.get("topicTitle") != null ? eventData.get("topicTitle").toString() : "";
        Long postId = eventData.get("postId") != null ? Long.valueOf(eventData.get("postId").toString()) : null;
        if (recipientId == null) {
            return;
        }
        sendNotification(
                recipientId,
                "有人在讨论中@了你",
                "您在讨论《" + topicTitle + "》中被提及，点击查看详情。",
                "DISCUSSION",
                "DISCUSSION_MENTIONED",
                "MEDIUM",
                "/discussion/topic/" + topicId,
                null,
                postId,
                "DISCUSSION_POST"
        );
    }

    /**
     * 按分类分组获取用户未读通知数
     */
    @Override
    public Map<String, Long> getUnreadGroupedByCategory(Long userId) {
        // 查询用户所有未读通知
        LambdaQueryWrapper<Notification> queryWrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false);
        List<Notification> unreadNotifications = this.list(queryWrapper);

        // 按category分组统计
        Map<String, Long> grouped = unreadNotifications.stream()
                .collect(Collectors.groupingBy(
                        n -> n.getCategory() != null ? n.getCategory() : "OTHER",
                        Collectors.counting()
                ));

        return grouped;
    }
}
