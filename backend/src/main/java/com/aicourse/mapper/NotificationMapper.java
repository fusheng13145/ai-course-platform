package com.aicourse.mapper;

import com.aicourse.entity.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Notification 数据访问 Mapper 接口
 * 继承自 MyBatis-Plus 的 {@code BaseMapper}，提供该实体的基础 CRUD 操作。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 按课程过滤当前用户的通知。
     *
     * <p>通知表没有 course_id 列，课程归属由关联业务反查：考试/作业/提交/讨论帖
     * 各自回连课程，公告通知则用 data.announcementId 回连公告。只查当前用户自己的
     * 通知，避免借课程页读到别人的消息。</p>
     */
    @Select("""
            <script>
            SELECT n.* FROM notifications n
            WHERE n.user_id = #{userId}
              AND (
                JSON_UNQUOTE(JSON_EXTRACT(n.data, '$.courseId')) = CAST(#{courseId} AS CHAR)
                OR (n.related_type = 'EXAM' AND EXISTS (
                    SELECT 1 FROM exam_papers e
                    WHERE e.id = n.related_id AND e.course_id = #{courseId}
                ))
                OR (n.related_type = 'TASK' AND EXISTS (
                    SELECT 1 FROM tasks t
                    WHERE t.id = n.related_id AND t.course_id = #{courseId}
                ))
                OR (n.related_type = 'SUBMISSION' AND EXISTS (
                    SELECT 1 FROM submissions s
                    JOIN tasks st ON st.id = s.task_id
                    WHERE s.id = n.related_id AND st.course_id = #{courseId}
                ))
                OR (n.related_type = 'DISCUSSION_POST' AND EXISTS (
                    SELECT 1 FROM discussion_posts p
                    JOIN discussion_topics d ON d.id = p.topic_id
                    WHERE p.id = n.related_id AND d.course_id = #{courseId}
                ))
                OR (n.type = 'ANNOUNCEMENT' AND EXISTS (
                    SELECT 1 FROM announcements a
                    WHERE a.course_id = #{courseId}
                      AND CAST(a.id AS CHAR) = JSON_UNQUOTE(JSON_EXTRACT(n.data, '$.announcementId'))
                ))
              )
            ORDER BY n.created_at DESC
            </script>
            """)
    Page<Notification> selectCourseNotifications(Page<Notification> page,
                                                 @Param("userId") Long userId,
                                                 @Param("courseId") Long courseId);
}
