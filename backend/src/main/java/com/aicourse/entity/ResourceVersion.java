package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 资源版本实体
 * 记录课程资源的历史版本信息，用于版本回溯与多版本管理
 */
@TableName("resource_versions")
@Data
public class ResourceVersion {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联资源主表ID
     */
    private Long resourceId;

    /**
     * 版本号
     */
    private Integer versionNumber;

    /**
     * 文件名称
     */
    private String fileName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 资源访问地址
     */
    private String url;

    /**
     * 版本更新说明
     */
    private String description;

    /**
     * 文件大小（单位：字节）
     */
    private Long fileSize;

    /**
     * 版本上传人ID
     */
    private Long uploaderId;

    /**
     * 是否为当前启用版本 0-禁用 1-启用
     */
    @TableField("is_active")
    private Boolean isActive;

    /**
     * 记录创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}