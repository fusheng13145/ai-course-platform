package com.aicourse.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 学习路径生成请求
 */
@Data
public class LearningPathRequest {
    @NotBlank(message = "课程ID不能为空")
    private String courseId;
    private String goal;
}
