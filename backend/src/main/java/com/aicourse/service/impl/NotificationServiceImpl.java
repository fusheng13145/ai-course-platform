package com.aicourse.service.impl;

import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Notification;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.NotificationMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 消息通知服务实现类
 * 实现通知的发送、查询、已读标记、删除及事件驱动通知等核心逻辑
 */
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements NotificationService {

    private final CourseEnrollmentService courseEnrollmentService;

    public NotificationServiceImpl(CourseEnrollmentService courseEnrollmentService) {
        this.courseEnrollmentService = courseEnrollmentService;
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
        this.save(notification);
        return notification;
    }

    /**
     * 批量发送通知
     * 循环调用 sendNotification 逐条发送
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSendNotifications(List<Long> userIds, String title, String content, String category,
                                       String type, String priority, String actionUrl, String data) {
        for (Long userId : userIds) {
            sendNotification(userId, title, content, category, type, priority, actionUrl, data, null, null);
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
            throw new BusinessException("无权操作该通知");
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
        return this.page(pageParam, queryWrapper);
    }

    /**
     * 删除通知（校验归属权）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotification(Long notificationId, Long userId) {
        Notification notification = this.getById(notificationId);
        if (notification == null) {
            throw new BusinessException("通知不存在");
        }
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException("无权删除该通知");
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
            default:
                // 未知事件类型不做处理
                break;
        }
    }

    /**
     * 处理任务创建事件
     * 查询课程下所有选课学生，发送任务创建通知
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
        for (CourseEnrollment enrollment : enrollments) {
            sendNotification(
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
            );
        }
    }

    /**
     * 处理考试发布事件
     * 查询课程下所有选课学生，发送考试通知
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
        for (CourseEnrollment enrollment : enrollments) {
            sendNotification(
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
            );
        }
    }

    /**
     * 处理作业批阅完成事件
     * 给对应学生发送成绩通知
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
