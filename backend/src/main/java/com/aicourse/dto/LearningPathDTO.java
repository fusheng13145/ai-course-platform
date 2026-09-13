package com.aicourse.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * AI 学习路径生成请求
 */
@Data
public class LearningPathDTO {
    @NotBlank(message = "课程ID不能为空")
    private String courseId;
    private String goal;
    /** 知识点掌握度数据（可选）：{知识点名称 → 掌握度 0-100}。传 null 或空则使用标准算法。 */
    private Map<String, Object> masteryData;
}
