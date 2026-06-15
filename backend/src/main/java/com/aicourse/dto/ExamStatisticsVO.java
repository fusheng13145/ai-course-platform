package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 试卷统计数据VO
 * 用于返回试卷的整体统计信息、分数分布和各题统计等分析数据
 */
@Schema(description = "试卷统计数据响应")
public class ExamStatisticsVO {

    @Schema(description = "试卷ID", example = "201")
    private Long examPaperId;

    @Schema(description = "总参与人数", example = "156")
    private Integer totalParticipants;

    @Schema(description = "已完成人数", example = "148")
    private Integer completedCount;

    @Schema(description = "平均分", example = "72.8")
    private BigDecimal averageScore;

    @Schema(description = "最高分", example = "98.5")
    private BigDecimal highestScore;

    @Schema(description = "最低分", example = "25.0")
    private BigDecimal lowestScore;

    @Schema(description = "及格率（小数形式，如0.85表示85%）", example = "0.85")
    private BigDecimal passRate;

    @Schema(description = "分数段分布（key=分数段描述，value=人数）",
            example = "{\"90-100\":15, \"80-89\":42, \"70-79\":55, \"60-69\":28, \"0-59\":16}")
    private Map<String, Integer> scoreDistribution;

    @Schema(description = "平均作答耗时（秒）", example = "6540")
    private Integer avgTimeSpent;

    @Schema(description = "各题统计列表")
    private List<QuestionStat> questionStats;

    /**
     * 单题统计内部类
     */
    @Schema(description = "单题统计数据")
    public static class QuestionStat {

        @Schema(description = "题目ID", example = "101")
        private Long questionId;

        @Schema(description = "题目排序号", example = "1")
        private Integer sortOrder;

        @Schema(description = "该题平均分", example = "7.2")
        private BigDecimal avgScore;

        @Schema(description = "该题正确率（小数形式）", example = "0.72")
        private BigDecimal correctRate;

        @Schema(description = "各选项选择分布（key=选项，value=选择人数）",
                example = "{\"A\":45, \"B\":38, \"C\":52, \"D\":21}")
        private Map<String, Integer> optionDistribution;

        // ====================== Getter & Setter ======================

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public BigDecimal getAvgScore() { return avgScore; }
        public void setAvgScore(BigDecimal avgScore) { this.avgScore = avgScore; }
        public BigDecimal getCorrectRate() { return correctRate; }
        public void setCorrectRate(BigDecimal correctRate) { this.correctRate = correctRate; }
        public Map<String, Integer> getOptionDistribution() { return optionDistribution; }
        public void setOptionDistribution(Map<String, Integer> optionDistribution) { this.optionDistribution = optionDistribution; }
    }

    // ====================== Getter & Setter ======================

    public Long getExamPaperId() { return examPaperId; }
    public void setExamPaperId(Long examPaperId) { this.examPaperId = examPaperId; }
    public Integer getTotalParticipants() { return totalParticipants; }
    public void setTotalParticipants(Integer totalParticipants) { this.totalParticipants = totalParticipants; }
    public Integer getCompletedCount() { return completedCount; }
    public void setCompletedCount(Integer completedCount) { this.completedCount = completedCount; }
    public BigDecimal getAverageScore() { return averageScore; }
    public void setAverageScore(BigDecimal averageScore) { this.averageScore = averageScore; }
    public BigDecimal getHighestScore() { return highestScore; }
    public void setHighestScore(BigDecimal highestScore) { this.highestScore = highestScore; }
    public BigDecimal getLowestScore() { return lowestScore; }
    public void setLowestScore(BigDecimal lowestScore) { this.lowestScore = lowestScore; }
    public BigDecimal getPassRate() { return passRate; }
    public void setPassRate(BigDecimal passRate) { this.passRate = passRate; }
    public Map<String, Integer> getScoreDistribution() { return scoreDistribution; }
    public void setScoreDistribution(Map<String, Integer> scoreDistribution) { this.scoreDistribution = scoreDistribution; }
    public Integer getAvgTimeSpent() { return avgTimeSpent; }
    public void setAvgTimeSpent(Integer avgTimeSpent) { this.avgTimeSpent = avgTimeSpent; }
    public List<QuestionStat> getQuestionStats() { return questionStats; }
    public void setQuestionStats(List<QuestionStat> questionStats) { this.questionStats = questionStats; }
}
