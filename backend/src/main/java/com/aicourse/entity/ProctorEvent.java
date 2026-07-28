package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 监考风险事件实体
 * 对应数据表：proctor_events
 * 由前端上报（切屏 / 人脸消失 / 多脸 / 手机 / 复制粘贴 / 退出全屏 等）
 */
@Data
@TableName("proctor_events")
public class ProctorEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联监考会话ID */
    private Long proctorSessionId;

    /**
     * 事件类型：TAB_SWITCH / FACE_MISSING / MULTIPLE_FACE /
     * MOBILE_DETECTED / COPY_PASTE / FULLSCREEN_EXIT / UNKNOWN
     */
    private String type;

    /**
     * 严重度：LOW / MEDIUM / HIGH
     */
    private String severity;

    /** 事件详情描述 */
    private String detail;

    /** 证据截图/录屏地址 */
    private String evidenceUrl;

    /** 事件捕获时间 */
    private LocalDateTime capturedAt;

    /**
     * 复核状态：PENDING（待复核）/ REVIEWED（已确认违规）/ EXEMPTED（已豁免）
     * 默认 PENDING，由教师/管理员在治理页确认或豁免
     */
    private String status;

    /** 复核结论备注 */
    private String reviewNote;

    /** 复核时间 */
    private LocalDateTime reviewedAt;

    /** 复核人ID（教师/管理员） */
    private Long reviewerId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // ===================== 事件类型常量 =====================
    public static final String TYPE_TAB_SWITCH = "TAB_SWITCH";
    public static final String TYPE_FACE_MISSING = "FACE_MISSING";
    public static final String TYPE_MULTIPLE_FACE = "MULTIPLE_FACE";
    public static final String TYPE_MOBILE_DETECTED = "MOBILE_DETECTED";
    public static final String TYPE_COPY_PASTE = "COPY_PASTE";
    public static final String TYPE_FULLSCREEN_EXIT = "FULLSCREEN_EXIT";
    public static final String TYPE_UNKNOWN = "UNKNOWN";

    // ===================== 严重度常量 =====================
    public static final String SEV_LOW = "LOW";
    public static final String SEV_MEDIUM = "MEDIUM";
    public static final String SEV_HIGH = "HIGH";

    // ===================== 复核状态常量 =====================
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_REVIEWED = "REVIEWED";
    public static final String STATUS_EXEMPTED = "EXEMPTED";
}
