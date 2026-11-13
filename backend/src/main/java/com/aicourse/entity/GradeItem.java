package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

/**
 * 成绩明细项实体（某一学生某一维度的归一化得分）
 * 对应数据表：grade_items
 * score 为已归一化到 0-100 的分值；rawScore 为原始分（如百分制/五分制）
 */
@Data
@TableName("grade_items")
public class GradeItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属成绩方案ID */
    private Long policyId;

    /** 学生ID */
    private Long studentId;

    /** 维度键（与 grade_policies.components[].key 对应） */
    private String componentKey;

    /** 归一化得分（0-100）；null 表示该维度尚未评分 */
    @DecimalMin(value = "0", message = "成绩得分不能小于 0")
    @DecimalMax(value = "100", message = "成绩得分不能大于 100")
    private BigDecimal score;

    /** 原始分 */
    private BigDecimal rawScore;

    /** 数据来源ID（如 submissionId / examSessionId / signInId） */
    private Long sourceId;

    /** 备注 */
    private String remark;

    /** 录入者ID */
    private Long graderId;

    /** 成绩明细首次落库时学生所在教学班快照（WP7，V16）：仅创建时解析写入，更新分数不回改快照 */
    private Long teachingClassId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
