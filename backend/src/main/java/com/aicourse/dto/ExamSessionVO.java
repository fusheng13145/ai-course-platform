package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 考试会话详情VO
 * 用于返回给前端的学生某次考试完整信息，包含成绩、每题结果和作弊标记
 */
@Schema(description = "考试会话详情响应")
public class ExamSessionVO {

    @Schema(description = "考试会话ID", example = "5001")
    private Long sessionId;

    @Schema(description = "试卷基本信息")
    private ExamPaperInfoVO examPaper;

    @Schema(description = "考试状态", example = "COMPLETED",
            allowableValues = {"NOT_STARTED", "IN_PROGRESS", "SUBMITTED", "TIMEOUT", "ABANDONED"})
    private String status;

    @Schema(description = "考试开始时间", example = "2024-12-20T09:02:30")
    private LocalDateTime startedAt;

    @Schema(description = "提交时间", example = "2024-12-20T11:05:00")
    private LocalDateTime submittedAt;

    @Schema(description = "实际作答耗时（秒）", example = "7350")
    private Integer timeSpent;

    @Schema(description = "得分", example = "82.5")
    private BigDecimal score;

    @Schema(description = "得分百分比", example = "82.5")
    private BigDecimal percentage;

    @Schema(description = "等级评定", example = "B",
            allowableValues = {"A", "B", "C", "D", "F"})
    private String grade;

    @Schema(description = "每题作答结果（key=题目序号）")
    private Map<Integer, ExamAnswerResult> answers;

    @Schema(description = "作弊标记列表", example = "[\"频繁切换标签页\", \"异常短时完成\"]")
    private List<String> cheatingFlags;

    /**
     * 试卷基本信息内部类
     */
    @Schema(description = "试卷基本信息")
    public static class ExamPaperInfoVO {

        @Schema(description = "试卷ID", example = "201")
        private Long id;

        @Schema(description = "试卷标题", example = "Java程序设计 - 期中考试")
        private String title;

        @Schema(description = "考试时长（分钟）", example = "120")
        private Integer timeLimit;

        @Schema(description = "题目总数", example = "50")
        private Integer questionCount;

        @Schema(description = "试卷总分", example = "100")
        private BigDecimal totalScore;

        // ====================== Getter & Setter ======================

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Integer getTimeLimit() { return timeLimit; }
        public void setTimeLimit(Integer timeLimit) { this.timeLimit = timeLimit; }
        public Integer getQuestionCount() { return questionCount; }
        public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }
        public BigDecimal getTotalScore() { return totalScore; }
        public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }
    }

    // ====================== Getter & Setter ======================

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public ExamPaperInfoVO getExamPaper() { return examPaper; }
    public void setExamPaper(ExamPaperInfoVO examPaper) { this.examPaper = examPaper; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Integer getTimeSpent() { return timeSpent; }
    public void setTimeSpent(Integer timeSpent) { this.timeSpent = timeSpent; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public Map<Integer, ExamAnswerResult> getAnswers() { return answers; }
    public void setAnswers(Map<Integer, ExamAnswerResult> answers) { this.answers = answers; }
    public List<String> getCheatingFlags() { return cheatingFlags; }
    public void setCheatingFlags(List<String> cheatingFlags) { this.cheatingFlags = cheatingFlags; }
}
