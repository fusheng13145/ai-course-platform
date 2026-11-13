package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程审批流水（V13 / B-12-P-08 WP3）。
 *
 * <p>每次提交、审批通过、驳回、重新提交都追加一行，只增不改。
 * 与 {@code courses.approval_status} 的单值快照互补：课程行是当前状态，本表是可追溯的历史。</p>
 */
@TableName("course_approval_records")
@Data
public class CourseApprovalRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程ID */
    private Long courseId;

    /** 操作人ID（提交人为课程负责人，审批人为管理员） */
    private Long operatorId;

    /** 动作：SUBMIT-提交，APPROVE-通过，REJECT-驳回，RESUBMIT-重新提交 */
    private String action;

    /** 变更前审批状态，首次提交为 null */
    private String fromStatus;

    /** 变更后审批状态 */
    private String toStatus;

    /** 审批意见（驳回原因等） */
    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
