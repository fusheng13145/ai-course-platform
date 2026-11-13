package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 教学班实体
 * 对应数据表：teaching_classes（V14 / B-12-P-08 WP4）
 * 一个教学班只有一名主讲；学生名单来自 course_enrollments.teaching_class_id。
 */
@TableName("teaching_classes")
@Data
public class TeachingClass {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属课程ID，关联 courses 表
     */
    private Long courseId;

    /**
     * 教学班名称
     */
    private String name;

    /**
     * 教学班编号（可选）
     */
    private String code;

    /**
     * 唯一主讲教师，关联 users 表
     */
    private Long leadTeacherId;

    /**
     * 容量；NULL 表示不限制
     */
    private Integer capacity;

    /**
     * 状态：DRAFT-草稿，ACTIVE-启用，CLOSED-结班
     */
    private String status;

    /**
     * 创建人ID，关联 users 表
     */
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
