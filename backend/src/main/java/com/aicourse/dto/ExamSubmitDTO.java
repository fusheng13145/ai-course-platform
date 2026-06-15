package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 考试提交请求体DTO
 * 用于接收学生提交试卷时的答案数据
 */
@Schema(description = "考试提交请求体")
public class ExamSubmitDTO {

    @Schema(description = "考试会话ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sessionId;

    @Schema(description = "答案集合（JSON对象格式，key为题目ID，value为学生答案）",
            example = "{\"question_1\":\"A\",\"question_2\":\"B,C\",\"question_3\":\"true\"}")
    private Map<String, Object> answers;

    // ====================== Getter & Setter ======================

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Map<String, Object> getAnswers() {
        return answers;
    }

    public void setAnswers(Map<String, Object> answers) {
        this.answers = answers;
    }
}
