package com.aicourse.dto;

import com.aicourse.common.GradeComponentsJson;
import com.aicourse.entity.GradePolicy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 过程性成绩方案入参（入参收实体加固）。
 *
 * <p>{@code creatorId} 由服务层按登录身份写入；实体直绑会让请求体可以自带创建者。</p>
 */
@Schema(name = "GradePolicySaveDTO", description = "成绩方案入参；创建者由服务端写入")
@Data
public class GradePolicySaveDTO {

    @Schema(description = "课程ID", example = "12")
    private Long courseId;

    @Schema(description = "方案名称", example = "默认方案")
    private String name;

    @Schema(description = "考核项配置；HTTP 出入参为对象数组，落库为 JSON 文本（见 {@code GradeComponentsJson}）")
    @JsonDeserialize(using = GradeComponentsJson.Deserializer.class)
    private String components;

    @Schema(description = "是否设为默认方案")
    private Boolean isDefault;

    public GradePolicy toEntity() {
        GradePolicy policy = new GradePolicy();
        policy.setCourseId(courseId);
        policy.setName(name);
        policy.setComponents(components);
        policy.setIsDefault(isDefault);
        return policy;
    }
}
