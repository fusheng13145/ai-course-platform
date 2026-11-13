package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 专业实体
 * 对应数据表：majors（V12 / B-12-P-08 WP1）
 */
@TableName("majors")
@Data
public class Major {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属学院ID，关联 colleges 表
     */
    private Long collegeId;

    /**
     * 专业编码（院内唯一）
     */
    private String code;

    /**
     * 专业名称
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
