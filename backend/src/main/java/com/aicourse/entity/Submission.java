package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 作业提交记录实体
 * 存储学生作业提交内容、附件、批阅结果、分数、评语及提交状态等全量信息
 */
@TableName("submissions")
@Data
public class Submission {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联任务ID
     */
    private Long taskId;

    /**
     * 提交学生ID
     */
    private Long studentId;

    /**
     * 文字作答内容
     */
    private String content;

    /**
     * 附件链接集合（JSON格式存储）
     */
    private String fileUrls;

    /**
     * 提交扩展数据（JSON格式存储）
     */
    private String submissionData;

    /**
     * 提交状态（待批阅、已批阅、已退回等）
     */
    private String status;

    /**
     * 提交时间
     */
    private LocalDateTime submittedAt;

    /**
     * 批阅完成时间
     */
    private LocalDateTime gradedAt;

    /**
     * 作业退回时间
     */
    private LocalDateTime returnedAt;

    /**
     * 实际得分
     */
    private BigDecimal score;

    /**
     * 满分分值（取自关联任务 tasks.max_score，非本表字段，仅用于展示/VO 映射）
     */
    @TableField(exist = false)
    private BigDecimal maxScore;

    /**
     * 得分百分比（由 score / 任务满分 计算，非本表字段，仅用于展示/VO 映射）
     */
    @TableField(exist = false)
    private BigDecimal percentage;

    /**
     * 教师人工评语
     */
    private String feedback;

    /**
     * AI智能评语
     */
    private String aiFeedback;

    /**
     * 批阅人ID
     */
    private Long graderId;

    /**
     * 提交次数（第N次作答）
     */
    private Integer attemptNumber;

    /**
     * 是否逾期提交 0-否 1-是
     */
    @TableField("is_late")
    private Boolean isLate;

    /**
     * 逾期扣分分值
     */
    private BigDecimal latePenalty;

    /**
     * 作答耗时（单位：分钟）
     */
    private Integer timeSpent;

    /**
     * 作业查看次数
     */
    private Integer viewCount;

    /**
     * 记录创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 记录更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

}