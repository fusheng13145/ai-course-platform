package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 系统配置实体类
 * 对应数据表：system_configs
 */
@TableName("system_configs")
@Data
public class SystemConfig {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配置键名
     */
    private String configKey;

    /**
     * 配置值
     */
    private String configValue;

    /**
     * 配置分组
     */
    private String configGroup;

    /**
     * 配置类型
     */
    private String configType;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 是否公开可见
     */
    @TableField("is_public")
    private Boolean isPublic;

    /**
     * 排序序号
     */
    private Integer sortOrder;

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

    public void setIsPublic(Boolean publicFlag) {
        isPublic = publicFlag;
    }

}