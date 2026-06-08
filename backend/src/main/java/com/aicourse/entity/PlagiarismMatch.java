package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 查重匹配明细实体
 * 存储单次查重任务中，每一处相似片段的匹配来源、原文、相似文本及相似度信息
 */
@TableName("plagiarism_matches")
public class PlagiarismMatch {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属查重任务ID
     */
    private Long plagiarismCheckId;

    /**
     * 匹配到的他人提交记录ID
     */
    private Long matchedSubmissionId;

    /**
     * 匹配来源描述
     */
    private String matchedSource;

    /**
     * 片段相似度
     */
    private BigDecimal similarity;

    /**
     * 原文内容
     */
    private String originalText;

    /**
     * 匹配到的相似文本
     */
    private String matchedText;

    /**
     * 匹配类型（整段匹配、局部匹配等）
     */
    private String matchType;

    /**
     * 记录创建时间（自动填充）
     */
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