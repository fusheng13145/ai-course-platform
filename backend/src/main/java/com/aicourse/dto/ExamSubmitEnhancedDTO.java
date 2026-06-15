package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

import java.util.Map;

/**
 * 考试提交DTO
 * 用于接收学生提交考试答案时的请求数据，支持多种题型答案格式
 */
@Schema(description = "考试提交请求")
public class ExamSubmitEnhancedDTO {

    @Schema(description = "答案集合（key=题目序号，value=答案）\n" +
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

    // ====================== Getter & Setter ======================

    public Map<Integer, Object> getAnswers() {
        return answers;
    }

    public void setAnswers(Map<Integer, Object> answers) {
        this.answers = answers;
    }

    public Integer getTimeSpent() {
        return timeSpent;
    }

    public void setTimeSpent(Integer timeSpent) {
        this.timeSpent = timeSpent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}
