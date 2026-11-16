package com.aicourse.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 学生题库练习判分结果（P0-A1）。
 * correctAnswer / explanation 只在学生提交答案后返回。
 */
@Schema(description = "学生题库练习判分结果")
public class QuestionPracticeResultVO {

    @Schema(description = "题目ID", example = "101")
    private Long questionId;

    @Schema(description = "是否答对", example = "true")
    private boolean correct;

    @Schema(description = "正确答案（已转为纯文本，如 A 或 A,B）", example = "A")
    private String correctAnswer;

    @Schema(description = "题目解析", example = "Java 的基本数据类型包括 int 等。")
    private String explanation;

    public QuestionPracticeResultVO() {
    }

    public QuestionPracticeResultVO(Long questionId, boolean correct, String correctAnswer, String explanation) {
        this.questionId = questionId;
        this.correct = correct;
        this.correctAnswer = correctAnswer;
        this.explanation = explanation;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
}
