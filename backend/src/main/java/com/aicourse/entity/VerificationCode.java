package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 验证码实体类
 * 对应数据表：verification_codes
 */
@TableName("verification_codes")
@Data
public class VerificationCode {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 验证码
     */
    private String code;

    /**
     * 验证码类型
     */
    private String type;

    /**
     * 是否已使用
     */
    @TableField("used")
    private Boolean isUsed;

    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 发送IP地址
     */
    private String ipAddress;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public void setIsUsed(Boolean used) {
        isUsed = used;
    }

}