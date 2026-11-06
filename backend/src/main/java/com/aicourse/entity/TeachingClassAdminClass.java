package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 教学班—行政班来源关系实体
 * 对应数据表：teaching_class_admin_classes（V14 / B-12-P-08 WP4）
 */
@TableName("teaching_class_admin_classes")
@Data
public class TeachingClassAdminClass {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 教学班ID，关联 teaching_classes 表
     */
    private Long teachingClassId;

    /**
     * 来源行政班ID，关联 admin_classes 表
     */
    private Long adminClassId;

    /**
     * 状态：ACTIVE / REMOVED
     */
    private String status;

    /**
     * 导入人ID，关联 users 表
     */
    private Long importedBy;

    /**
     * 导入时间
     */
    private LocalDateTime importedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
