package com.aicourse.entity;

import com.aicourse.common.GradeComponentsJson;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 成绩方案（过程性考核权重配置）实体
 * 对应数据表：grade_policies
 * 进阶：每门课程一套可配置多维权重（出勤/作业/考试/课堂互动…），支持 AI 推荐权重
 * components 示例 JSON：
 * [{"key":"SIGN_IN","name":"出勤","weight":10,"source":"sign_in"},
 *  {"key":"HOMEWORK","name":"作业","weight":30,"source":"submission"},
 *  {"key":"EXAM","name":"考试","weight":60,"source":"exam"}]
 */
@Data
@TableName("grade_policies")
public class GradePolicy {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属课程ID */
    private Long courseId;

    /** 方案名称 */
    private String name;

    /** 维度权重 JSON；HTTP 出入参为对象数组（见 {@link GradeComponentsJson}） */
    @JsonDeserialize(using = GradeComponentsJson.Deserializer.class)
    @JsonSerialize(using = GradeComponentsJson.Serializer.class,
            nullsUsing = GradeComponentsJson.NullSerializer.class)
    private String components;

    /** 创建者ID */
    private Long creatorId;

    /** 是否课程默认方案 */
    @TableField("is_default")
    private Boolean isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
