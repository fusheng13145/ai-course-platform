package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 评分请求体DTO
 * 用于教师批改作业/主观题时的评分数据
 */
@Schema(description = "评分请求体")
public class SubmissionGradeDTO {

    @Schema(description = "提交记录ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long submissionId;

    @Schema(description = "得分", example = "85.5")
    private BigDecimal score;

    @Schema(description = "教师评语/反馈", example = "代码结构清晰，但需要注意异常处理")
    private String feedback;

    // ====================== Getter & Setter ======================

    public Long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }
}
