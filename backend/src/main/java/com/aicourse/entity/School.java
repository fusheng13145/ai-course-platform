package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 学校根实体（单校部署，表内恰一行）
 * 对应数据表：schools（V12 / B-12-P-08 WP1）
 */
@TableName("schools")
@Data
public class School {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 学校编码
     */
    private String code;

    /**
     * 学校名称
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
