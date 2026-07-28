package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错题记录实体类
 * 对应数据表：wrong_questions
 */
@TableName("wrong_questions")
@Data
public class WrongQuestion {

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
     * 题目ID
     */
    private Long questionId;

    /**
     * 课程ID
     */
    private Long courseId;

    /**
     * 错误答案
     */
    private String wrongAnswer;

    /**
     * 错误原因/备注
     */
    private String reason;

    /**
     * AI 错因分析（错题产生时由 AI 自动生成，支撑"错题→AI分析→推送练习"闭环）
     */
    private String aiAnalysis;

    // ===== 以下为联表派生的展示字段（不入库，仅用于错题本列表展示题干/答案/解析） =====
    @TableField(exist = false)
    private String questionContent;

    @TableField(exist = false)
    private String questionOptions;

    @TableField(exist = false)
    private String correctAnswer;

    @TableField(exist = false)
    private String questionAnalysis;

    @TableField(exist = false)
    private String knowledgePointName;

    /**
     * 是否已掌握
     */
    private Boolean mastered;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 练习次数
     */
    private Integer practiceCount;

    /**
     * 最近练习时间
     */
    private LocalDateTime lastPracticedAt;

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

}