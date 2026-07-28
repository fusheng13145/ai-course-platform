package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 错题练习卷生成请求DTO
 * 用于从错题库中随机选取题目生成练习卷
 */
@Schema(description = "错题练习卷生成请求")
@Data
public class WrongQuestionPracticeDTO {

    @Schema(description = "课程ID（可选，不填则从所有课程的错题中选取）", example = "1")
    private Long courseId;

    @Schema(description = "生成题目数量（默认10）", example = "10")
    private Integer count;

}
