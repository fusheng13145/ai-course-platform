package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 签到记录实体（每位学生一次签到结果）
 * 对应数据表：sign_in_records
 */
@TableName("sign_in_records")
@Data
public class SignInRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联签到活动ID */
    private Long signInId;

    /** 学生ID */
    private Long studentId;

    /**
     * 签到结果：SIGNED / LATE / ABSENT / LEAVE
     * @see #STATUS_SIGNED 等常量
     */
    private String status;

    /** 签到时间 */
    private LocalDateTime signTime;

    /** 签到IP */
    private String ip;

    /** 用户代理（UA） */
    private String userAgent;

    /** 拍照核验地址（普通/人脸签到使用） */
    private String photoUrl;

    /** 纬度（LOCATION 类型使用） */
    private Double latitude;

    /** 经度（LOCATION 类型使用） */
    private Double longitude;

    /** 学生提交的签到码/二维码（CODE/QR 类型校验用，修复 P1-2） */
    private String submittedCode;

    /** 学生提交的手势（GESTURE 类型校验用，修复 P1-2） */
    private String submittedGesture;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ===================== 状态常量 =====================
    public static final String STATUS_SIGNED = "SIGNED";
    public static final String STATUS_LATE = "LATE";
    public static final String STATUS_ABSENT = "ABSENT";
    public static final String STATUS_LEAVE = "LEAVE";

}
