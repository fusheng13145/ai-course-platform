package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建试卷请求DTO
 * 用于接收教师创建考试试卷时的完整请求数据，包含题目配置
 */
@Schema(description = "创建试卷请求")
public class ExamPaperCreateDTO {

    @NotNull(message = "所属课程ID不能为空")
    @Schema(description = "所属课程ID", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long courseId;

    @NotBlank(message = "试卷标题不能为空")
    @Size(max = 100, message = "试卷标题不能超过100个字符")
    @Schema(description = "试卷标题", example = "Java程序设计 - 期中考试",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Size(max = 2000, message = "试卷描述不能超过2000个字符")
    @Schema(description = "试卷描述/说明", example = "本试卷涵盖第1-5章内容，共50题，总分100分...")
    private String description;

    @Min(value = 10, message = "考试时长至少为10分钟")
    @Max(value = 480, message = "考试时长不超过480分钟(8小时)")
    @Schema(description = "考试时长限制（分钟）", example = "120")
    private Integer timeLimit;

    @DecimalMin(value = "0.1", message = "总分最小为0.1")
    @DecimalMax(value = "10000", message = "总分最大为10000")
    @Schema(description = "试卷总分", example = "100")
    private BigDecimal totalScore;

    @Schema(description = "考试开始时间", example = "2024-12-20T09:00:00")
    private LocalDateTime startTime;

    @Schema(description = "考试结束时间", example = "2024-12-20T11:30:00")
    private LocalDateTime endTime;

    @DecimalMin(value = "0", message = "及格分不能为负数")
    @Schema(description = "及格分数", example = "60")
    private BigDecimal passScore;

    @Schema(description = "是否打乱题目顺序", example = "true")
    private Boolean shuffleQuestions = false;

    @Schema(description = "是否考后展示分数", example = "true")
    private Boolean showScore = true;

    @Schema(description = "是否展示正确答案", example = "false")
    private Boolean showAnswer = false;

    @Min(value = 1, message = "最大作答次数至少为1")
    @Schema(description = "最大作答次数", example = "1")
    private Integer maxAttempts = 1;

    @NotEmpty(message = "试题列表不能为空")
    @Schema(description = "试卷中的题目配置列表",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ExamQuestionDTO> questions;

    // ====================== Getter & Setter ======================

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getTimeLimit() { return timeLimit; }
    public void setTimeLimit(Integer timeLimit) { this.timeLimit = timeLimit; }
    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public BigDecimal getPassScore() { return passScore; }
    public void setPassScore(BigDecimal passScore) { this.passScore = passScore; }
    public Boolean getShuffleQuestions() { return shuffleQuestions; }
    public void setShuffleQuestions(Boolean shuffleQuestions) { this.shuffleQuestions = shuffleQuestions; }
    public Boolean getShowScore() { return showScore; }
    public void setShowScore(Boolean showScore) { this.showScore = showScore; }
    public Boolean getShowAnswer() { return showAnswer; }
    public void setShowAnswer(Boolean showAnswer) { this.showAnswer = showAnswer; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
    public List<ExamQuestionDTO> getQuestions() { return questions; }
    public void setQuestions(List<ExamQuestionDTO> questions) { this.questions = questions; }
}
