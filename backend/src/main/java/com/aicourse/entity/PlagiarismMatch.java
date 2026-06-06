package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("plagiarism_matches")
public class PlagiarismMatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long plagiarismCheckId;
    private Long matchedSubmissionId;
    private String matchedSource;
    private BigDecimal similarity;
    private String originalText;
    private String matchedText;
    private String matchType;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPlagiarismCheckId() {
        return plagiarismCheckId;
    }

    public void setPlagiarismCheckId(Long plagiarismCheckId) {
        this.plagiarismCheckId = plagiarismCheckId;
    }

    public Long getMatchedSubmissionId() {
        return matchedSubmissionId;
    }

    public void setMatchedSubmissionId(Long matchedSubmissionId) {
        this.matchedSubmissionId = matchedSubmissionId;
    }

    public String getMatchedSource() {
        return matchedSource;
    }

    public void setMatchedSource(String matchedSource) {
        this.matchedSource = matchedSource;
    }

    public BigDecimal getSimilarity() {
        return similarity;
    }

    public void setSimilarity(BigDecimal similarity) {
        this.similarity = similarity;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getMatchedText() {
        return matchedText;
    }

    public void setMatchedText(String matchedText) {
        this.matchedText = matchedText;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
