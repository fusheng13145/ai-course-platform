package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 提交作业请求 DTO（白名单）
 * 仅暴露学生可被允许写入的字段。严禁直接使用 Submission 实体作为入参，
 * 否则学生可在请求体中注入 score / graderId / status 等敏感列。
 */
@Schema(description = "提交作业请求")
@lombok.Data
public class SubmissionCreateDTO {

    @NotNull(message = "任务ID不能为空")
    @Schema(description = "所属作业任务ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long taskId;

    @Schema(description = "文字作答内容")
    @Size(max = 50000, message = "作答内容过长")
    private String content;

    @Schema(description = "附件URL列表")
    private List<String> fileUrls;
}
