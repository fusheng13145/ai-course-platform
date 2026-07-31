package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 题库题目实体
 * 存储各类题型题干、选项、答案、解析、难度、分值及使用统计等信息
 */
@TableName("questions")
@Data
public class Question {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 题目类型（单选/多选/判断/简答等）
     */
    private String type;

    /**
     * 题干内容
     */
    @NotBlank(message = "题干不能为空")
    private String content;

    /**
     * 选项集合（JSON格式存储）
     */
    private String options;

    /**
     * 正确答案（多答案按规则分隔/JSON存储）
     */
    private String correctAnswers;

    /**
     * 难度等级
     */
    private String difficulty;

    /**
     * 题目分值
     */
    private BigDecimal score;

    /**
     * 关联知识点集合（JSON/逗号分隔）
     */
    private String knowledgePoints;

    /**
     * 题目解析
     */
    private String explanation;

    /**
     * 被试卷引用次数
     */
    private Integer usageCount;

    /**
     * 答题正确率
     */
    private BigDecimal correctRate;

    /**
     * 所属课程ID
     */
    private Long courseId;

    /**
     * 创建人用户ID
     */
    private Long createdBy;

    /**
     * 是否启用 0-禁用 1-启用
     */
    @TableField("is_active")
    private Boolean isActive;

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