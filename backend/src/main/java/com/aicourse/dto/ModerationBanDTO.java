package com.aicourse.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 封禁作者入参。
 */
@Data
public class ModerationBanDTO {

    /** 封禁原因（建议填写，会展示在审核页的封禁名单里） */
    @Size(max = 255, message = "封禁原因最长 255 字符")
    private String reason;
}
