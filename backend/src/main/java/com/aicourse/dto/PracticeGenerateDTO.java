package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 错题练习生成请求DTO
 * 用于学生根据错题本生成针对性练习题
 */
@Schema(description = "错题练习生成请求")
@Data
public class PracticeGenerateDTO {

    @Schema(description = "课程ID（可选，不传则使用全部课程的错题）", example = "1")
    private Long courseId;

    @Schema(description = "生成题目数量", example = "10")
    private Integer count;

}
