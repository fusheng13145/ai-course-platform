package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 查重结果响应VO
 * 用于返回给前端的查重检测结果，包含总体相似度和详细匹配列表
 */
@Schema(description = "查重结果响应")
public class PlagiarismResultVO {

    @Schema(description = "查重记录ID", example = "8001")
    private Long checkId;

    @Schema(description = "整体相似度(0-100)", example = "25.8")
    private BigDecimal overallSimilarity;

    @Pattern(regexp = "^(PENDING|RUNNING|COMPLETED|FAILED)$")
    @Schema(description = "检测状态", example = "COMPLETED",
            allowableValues = {"PENDING", "RUNNING", "COMPLETED", "FAILED"})
    private String status;

    @Schema(description = "匹配来源列表（按相似度降序排列）")
    private List<PlagiarismMatchVO> matches;

    @Schema(description = "检测完成时间", example = "2024-12-31T10:30:00")
    private LocalDateTime checkedAt;

    // ====================== Getter & Setter ======================

    public Long getCheckId() { return checkId; }
    public void setCheckId(Long checkId) { this.checkId = checkId; }
    public BigDecimal getOverallSimilarity() { return overallSimilarity; }
    public void setOverallSimilarity(BigDecimal overallSimilarity) { this.overallSimilarity = overallSimilarity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<PlagiarismMatchVO> getMatches() { return matches; }
    public void setMatches(List<PlagiarismMatchVO> matches) { this.matches = matches; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
}
