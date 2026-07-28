package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 发起查重检测请求DTO
 * 用于接收教师/系统发起作业查重检测时的参数配置
 */
@Schema(description = "发起查重检测请求")
@Data
public class PlagiarismCheckCreateDTO {

    @NotNull(message = "提交记录ID不能为空")
    @Schema(description = "待检测的提交记录ID", example = "1001",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long submissionId;

    @Schema(description = "查重类型", example = "TEXT",
            allowableValues = {"TEXT", "CODE", "STRUCTURE"})
    private String checkType = "TEXT";

    @Pattern(regexp = "^(ALL|COURSE|SAME_TASK)$", message = "对比范围不合法")
    @Schema(description = "对比范围\n" +
            "- ALL: 全库对比\n" +
            "- COURSE: 同课程内对比\n" +
            "- SAME_TASK: 同一任务下对比",
            example = "SAME_TASK",
            allowableValues = {"ALL", "COURSE", "SAME_TASK"})
    private String compareAgainst = "SAME_TASK";

    @Pattern(regexp = "^(LOW|MEDIUM|HIGH)$", message = "灵敏度不合法")
    @Schema(description = "检测灵敏度", example = "MEDIUM",
            allowableValues = {"LOW", "MEDIUM", "HIGH"})
    private String sensitivity = "MEDIUM";

}
