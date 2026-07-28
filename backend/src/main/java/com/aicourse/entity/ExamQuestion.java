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
 * 试卷-题目关联实体
 * 用于维护试卷与题库题目的关联关系，记录题目序号、分值等信息
 */
@TableName("exam_questions")
@Data
public class ExamQuestion {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属试卷ID
     */
    private Long examPaperId;

    /**
     * 题库题目ID
     */
    private Long questionId;

    /**
     * 题目在试卷中的排序序号
     */
    private Integer orderIndex;

    /**
     * 题目分值
     */
    private BigDecimal points;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}