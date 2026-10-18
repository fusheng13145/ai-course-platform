package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师 AI 出题限制名单
 * 表名：ai_generation_restrictions
 *
 * <p>角色矩阵 ④ 的修订落点：按**特定教师**限制 AI 出题权限（V10 新增）。
 * 与平台级开关（{@code system_configs}）并存：平台开关是"总闸"，本表是"逐人名单"。</p>
 */
@TableName("ai_generation_restrictions")
@Data
public class AiGenerationRestriction {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被限制的教师用户ID */
    @TableField("user_id")
    private Long userId;

    /** 限制原因（管理员填写） */
    private String reason;

    /** 操作管理员ID（留痕） */
    @TableField("restricted_by")
    private Long restrictedBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
