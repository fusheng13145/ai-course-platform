package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 角色权限关联实体
 * 维护角色与权限的对应关系，支持权限层级、分类、排序及接口路径配置
 */
@TableName("role_permissions")
@Data
public class RolePermission {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色标识
     */
    private String role;

    /**
     * 权限唯一标识（权限关键字）
     */
    private String permissionKey;

    /**
     * 适用作用域 ALL/SCHOOL/COLLEGE（V12，旧数据回填 ALL）
     */
    private String scope;

    /**
     * 权限名称
     */
    private String permissionName;

    /**
     * 权限类型（菜单/按钮/接口等）
     */
    private String permissionType;

    /**
     * 关联资源/接口路径
     */
    private String resourcePath;

    /**
     * 父级权限ID（顶级权限为0/NULL）
     */
    private Long parentId;

    /**
     * 同层级排序序号
     */
    private Integer sortOrder;

    /**
     * 是否启用 0-禁用 1-启用
     */
    @TableField("is_active")
    private Boolean isActive;

    /**
     * 权限描述说明
     */
    private String description;

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