package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 在线监考会话实体
 * 对应数据表：proctor_sessions
 * 对标超星：考试防作弊监考；进阶支持多维风险事件聚合与风险评分
 */
@Data
@TableName("proctor_sessions")
public class ProctorSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联考试会话ID（exam_sessions） */
    private Long examSessionId;

    /** 考生ID */
    private Long studentId;

    /**
     * 状态：IN_PROGRESS / ENDED / FLAGGED
     */
    private String status;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime endedAt;

    /** 风险评分（事件严重度累加） */
    private Integer riskScore;

    /**
     * 风险等级：LOW / MEDIUM / HIGH
     */
    private String riskLevel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ===================== 状态常量 =====================
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_ENDED = "ENDED";
    public static final String STATUS_FLAGGED = "FLAGGED";

    // ===================== 风险等级常量 =====================
    public static final String LEVEL_LOW = "LOW";
    public static final String LEVEL_MEDIUM = "MEDIUM";
    public static final String LEVEL_HIGH = "HIGH";
}
