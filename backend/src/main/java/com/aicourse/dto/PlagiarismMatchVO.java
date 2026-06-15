package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 查重单个匹配结果VO
 * 用于描述与某一个来源的相似匹配详情，包括相似段落和可疑片段
 */
@Schema(description = "查重单个匹配结果")
public class PlagiarismMatchVO {

    @Schema(description = "来源提交记录ID", example = "1002")
    private Long sourceSubmissionId;

    @Schema(description = "来源学生姓名", example = "李四")
    private String sourceStudentName;

    @Schema(description = "与该来源的相似度(0-100)", example = "35.2")
    private BigDecimal similarity;

    @Schema(description = "相似段落列表（连续相同或高度相似的文本片段）")
    private List<MatchedSegment> matchedSegments;

    @Schema(description = "可疑片段列表（疑似抄袭但非完全相同的段落）")
    private List<SuspiciousSection> suspiciousSections;

    /**
     * 相似段落内部类
     */
    @Schema(description = "相似段落")
    public static class MatchedSegment {

        @Schema(description = "原文中的起始位置（字符偏移量）", example = "120")
        private Integer startOffset;

        @Schema(description = "原文中的结束位置", example = "285")
        private Integer endOffset;

        @Schema(description = "相似文本内容摘要", example = "public class StudentManager implements Serializable {...}")
        private String textSnippet;

        @Schema(description = "该段落的相似度(0-100)", example = "98.5")
        private BigDecimal similarity;

        // ====================== Getter & Setter ======================

        public Integer getStartOffset() { return startOffset; }
        public void setStartOffset(Integer startOffset) { this.startOffset = startOffset; }
        public Integer getEndOffset() { return endOffset; }
        public void setEndOffset(Integer endOffset) { this.endOffset = endOffset; }
        public String getTextSnippet() { return textSnippet; }
        public void setTextSnippet(String textSnippet) { this.textSnippet = textSnippet; }
        public BigDecimal getSimilarity() { return similarity; }
        public void setSimilarity(BigDecimal similarity) { this.similarity = similarity; }
    }

    /**
     * 可疑片段内部类
     */
    @Schema(description = "可疑片段")
    public static class SuspiciousSection {

        @Schema(description = "原文中的起始位置", example = "300")
        private Integer startOffset;

        @Schema(description = "原文中的结束位置", example = "420")
        private Integer endOffset;

        @Schema(description = "可疑文本内容", example = "该方法首先验证输入参数的有效性...")
        private String content;

        @Schema(description = "可疑程度(0-100)", example = "72.0")
        private BigDecimal suspiciousScore;

        @Schema(description = "可疑原因说明", example = "结构高度相似，仅变量名不同")
        private String reason;

        // ====================== Getter & Setter ======================

        public Integer getStartOffset() { return startOffset; }
        public void setStartOffset(Integer startOffset) { this.startOffset = startOffset; }
        public Integer getEndOffset() { return endOffset; }
        public void setEndOffset(Integer endOffset) { this.endOffset = endOffset; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public BigDecimal getSuspiciousScore() { return suspiciousScore; }
        public void setSuspiciousScore(BigDecimal suspiciousScore) { this.suspiciousScore = suspiciousScore; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    // ====================== Getter & Setter ======================

    public Long getSourceSubmissionId() { return sourceSubmissionId; }
    public void setSourceSubmissionId(Long sourceSubmissionId) { this.sourceSubmissionId = sourceSubmissionId; }
    public String getSourceStudentName() { return sourceStudentName; }
    public void setSourceStudentName(String sourceStudentName) { this.sourceStudentName = sourceStudentName; }
    public BigDecimal getSimilarity() { return similarity; }
    public void setSimilarity(BigDecimal similarity) { this.similarity = similarity; }
    public List<MatchedSegment> getMatchedSegments() { return matchedSegments; }
    public void setMatchedSegments(List<MatchedSegment> matchedSegments) { this.matchedSegments = matchedSegments; }
    public List<SuspiciousSection> getSuspiciousSections() { return suspiciousSections; }
    public void setSuspiciousSections(List<SuspiciousSection> suspiciousSections) { this.suspiciousSections = suspiciousSections; }
}
