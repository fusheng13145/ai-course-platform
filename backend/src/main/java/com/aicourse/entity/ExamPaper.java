package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试卷实体类
 * 对应数据表：exam_papers
 */
@TableName("exam_papers")
@Data
public class ExamPaper {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 试卷标题
     */
    @NotBlank(message = "试卷标题不能为空")
    private String title;

    /**
     * 试卷描述
     */
    private String description;

    /**
     * 所属课程ID
     */
    private Long courseId;

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 试卷状态
     */
    private String status;

    /**
     * 考试须知
     */
    private String instructions;

    /**
     * 考试开始时间
     */
    private LocalDateTime startTime;

    /**
     * 考试结束时间
     */
    private LocalDateTime endTime;

    /**
     * 试卷总分
     */
    private BigDecimal totalScore;

    /**
     * 考试时长（分钟）
     */
    private Integer duration;

    /**
     * 题目总数
     */
    private Integer questionCount;

    /**
     * 是否打乱题目顺序
     */
    private Boolean shuffleQuestions;

    /**
     * 是否打乱选项顺序
     */
    private Boolean shuffleOptions;

    /**
     * 是否展示分数
     */
    private Boolean showScore;

    /**
     * 是否展示正确答案
     */
    private Boolean showCorrectAnswer;

    /**
     * 最大作答次数
     */
    private Integer maxAttempts;

    /**
     * 作答限时
     */
    private Integer timeLimit;

    /**
     * 是否允许查看试卷回顾
     */
    private Boolean allowReview;

    /**
     * 题目生成规则
     */
    private String generationRules;

    /**
     * 是否启用
     */
    @TableField("is_active")
    private Boolean isActive;

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

    /**
     * 所属课程名称（非本表字段，由 courseId 关联查询回填，仅用于列表展示）。
     *
     * <p>修复（2026-09-23，P1-4）：此前实体只含 {@code courseId}，学生端考试卡片因取不到
     * {@code courseName} 而恒显示「未知课程」。</p>
     */
    @TableField(exist = false)
    private String courseName;

    public void setIsActive(Boolean active) {
        isActive = active;
    }

}