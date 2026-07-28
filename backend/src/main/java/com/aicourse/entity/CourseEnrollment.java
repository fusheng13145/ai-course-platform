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
 * 课程选课记录实体类
 * 对应数据表：course_enrollments
 */
@TableName("course_enrollments")
@Data
public class CourseEnrollment {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 学生ID
     */
    private Long studentId;

    /**
     * 课程ID
     */
    private Long courseId;

    /**
     * 选课审核状态
     */
    private String approvalStatus;

    /**
     * 是否有效
     */
    @TableField("is_active")
    private Boolean isActive;

    /**
     * 总成绩
     */
    private BigDecimal finalScore;

    /**
     * 考勤成绩
     */
    private BigDecimal attendanceScore;

    /**
     * 作业成绩
     */
    private BigDecimal assignmentScore;

    /**
     * 考试成绩
     */
    private BigDecimal examScore;

    /**
     * 学习进度
     */
    private BigDecimal progress;

    /**
     * 累计学习时长（单位：分钟）
     */
    private Integer totalStudyTime;

    /**
     * 审核时间
     */
    private LocalDateTime approvalTime;

    /**
     * 审核人ID
     */
    private Long approvedBy;

    /**
     * 审核意见
     */
    private String approvalComment;

    /**
     * 选课时间
     */
    private LocalDateTime enrolledAt;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public void setIsActive(Boolean active) {
        isActive = active;
    }

}