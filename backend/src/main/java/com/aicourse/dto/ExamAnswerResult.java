package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 单题作答结果VO
 * 用于返回考试中每一道题目的作答详情、正确答案和得分情况
 */
@Schema(description = "单题作答结果")
public class ExamAnswerResult {

    @Schema(description = "题目ID", example = "101")
    private Long questionId;

    @Schema(description = "用户提交的答案", example = "A")
    private Object userAnswer;

    @Schema(description = "正确答案", example = "A")
    private Object correctAnswer;

    @Schema(description = "是否正确", example = "true")
    private Boolean isCorrect;

    @Schema(description = "该题得分", example = "10.0")
    private BigDecimal score;

    @Schema(description = "该题满分", example = "10.0")
    private BigDecimal maxScore;

    // ====================== Getter & Setter ======================

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public Object getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(Object userAnswer) {
        this.userAnswer = userAnswer;
    }

    public Object getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(Object correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
    }
}
