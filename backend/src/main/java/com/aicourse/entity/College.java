package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 学院实体
 * 对应数据表：colleges（V12 / B-12-P-08 WP1）
 */
@TableName("colleges")
@Data
public class College {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属学校ID，关联 schools 表
     */
    private Long schoolId;

    /**
     * 学院编码（校内唯一）
     */
    private String code;

    /**
     * 学院名称
     */
    private String name;

    /**
     * 状态：ACTIVE-启用，ARCHIVED-归档
     */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
