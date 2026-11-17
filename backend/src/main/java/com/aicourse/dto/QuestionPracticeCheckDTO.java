package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 学生题库练习判分请求（P0-A1）。
 * 学生只提交题目 ID 与自己的答案；正确答案与解析由服务端在提交后返回。
 */
@Schema(description = "学生题库练习判分请求")
public class QuestionPracticeCheckDTO {

    @NotNull(message = "题目ID不能为空")
    @Schema(description = "题目ID", example = "101")
    private Long questionId;

    @NotBlank(message = "答案不能为空")
    @Schema(description = "学生答案：单选 A、多选 A,B、判断 true/false、填空/简答文本", example = "A")
    private String answer;

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
