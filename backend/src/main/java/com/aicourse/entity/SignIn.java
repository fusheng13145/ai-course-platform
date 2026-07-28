package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 课堂签到实体
 * 对应数据表：sign_ins
 * 对标超星：支持普通/手势/位置/二维码/签到码 五种签到
 */
@Data
@TableName("sign_ins")
public class SignIn {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属课程ID */
    private Long courseId;

    /** 所属章节ID（可空，表示整课签到） */
    private Long chapterId;

    /** 签到标题 */
    private String title;

    /**
     * 签到类型：NORMAL / GESTURE / LOCATION / QR / CODE
     * @see #TYPE_NORMAL 等常量
     */
    private String type;

    /**
     * 状态：PENDING / ONGOING / CLOSED
     */
    private String status;

    /** 签到窗口时长（分钟） */
    private Integer durationMinutes;

    /** 手势签到手势码（GESTURE 类型使用） */
    private String gesture;

    /** 二维码/签到码（QR/CODE 类型使用） */
    private String signCode;

    /** 发起教师ID */
    private Long creatorId;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime closedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ===================== 类型常量 =====================
    public static final String TYPE_NORMAL = "NORMAL";
    public static final String TYPE_GESTURE = "GESTURE";
    public static final String TYPE_LOCATION = "LOCATION";
    public static final String TYPE_QR = "QR";
    public static final String TYPE_CODE = "CODE";

    // ===================== 状态常量 =====================
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ONGOING = "ONGOING";
    public static final String STATUS_CLOSED = "CLOSED";
}
