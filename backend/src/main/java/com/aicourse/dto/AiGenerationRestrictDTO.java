package com.aicourse.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 加入「教师 AI 出题限制名单」入参。
 */
@Data
public class AiGenerationRestrictDTO {

    @NotNull(message = "被限制的教师ID不能为空")
    private Long userId;

    @Size(max = 255, message = "限制原因最长 255 字符")
    private String reason;
}
