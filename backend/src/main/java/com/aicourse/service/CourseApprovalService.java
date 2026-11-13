package com.aicourse.service;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseApprovalRecord;

import java.util.List;

/**
 * 课程审批编排（V13 / B-12-P-08 WP3）。
 *
 * <p>职责：把「课程状态变更」与「审批流水」放在同一事务里，避免出现
 * 状态已改、流水缺失（或反之）的不一致；同时负责按课程负责人回填 {@code courses.college_id}。</p>
 */
public interface CourseApprovalService {

    /**
     * 按课程负责人所属学院回填课程学院；负责人不存在或未归属学院时置空。
     * 客户端传入的 collegeId 一律被覆盖，防止跨院建课。
     */
    void applyOwnerCollege(Course course, Long teacherId);

    /**
     * 记录一次课程提交（创建后调用）。
     */
    void recordSubmission(Long courseId, Long operatorId);

    /**
     * 审批通过：落 {@code approved_by} 并追加 APPROVE 流水。
     */
    Course approve(Long courseId, Long operatorId);

    /**
     * 驳回：落 {@code approval_comment} 并追加 REJECT 流水。
     */
    Course reject(Long courseId, Long operatorId, String reason);

    /**
     * 重新提交：仅 REJECTED → PENDING，历史流水保留。
     */
    Course resubmit(Long courseId, Long operatorId);

    /**
     * 查询某门课程的审批流水（按时间正序）。
     */
    List<CourseApprovalRecord> listByCourse(Long courseId);
}
