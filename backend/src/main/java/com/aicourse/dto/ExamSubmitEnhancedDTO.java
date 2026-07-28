package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 考试提交DTO
 * 用于接收学生提交考试答案时的请求数据，支持多种题型答案格式
 */
@Schema(description = "考试提交请求")
@Data
public class ExamSubmitEnhancedDTO {

    @NotNull(message = "答案不能为空")
    @Schema(description = "答案���合（key=题目序号，value=答案）\n" +
            "- 单选题：String (如 \"A\")\n" +
            "- 多选题：List<String> (如 [\"A\",\"C\"])\n" +
            "- 判断题：Boolean\n" +
            "- 简答题：String",
            example = "{\"1\":\"A\",\"2\":[\"B\",\"C\"],\"3\":true,\"4\":\"面向对象的三大特性是...\"}")
    private Map<Integer, Object> answers;

    @Min(value = 0, message = "用时不能为负数")
    @Schema(description = "作答耗时（秒）", example = "3600")
    private Integer timeSpent;

    @Schema(description = "客户端IP地址（可选，服务端也可自动获取）", example = "192.168.1.100")
    private String ipAddress;

}
