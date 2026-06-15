package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * 趋势查询参数DTO
 * 用于接收前端查询数据趋势时的筛选和时间范围参数
 */
@Schema(description = "趋势查询参数")
public class TrendQueryDTO {

    @Schema(description = "查询起始日期", example = "2024-11-01")
    private LocalDate startDate;

    @Schema(description = "查询结束日期", example = "2024-12-31")
    private LocalDate endDate;

    @Pattern(regexp = "^(DAILY|WEEKLY|MONTHLY)$", message = "时间粒度不合法")
    @Schema(description = "时间聚合粒度", example = "WEEKLY",
            allowableValues = {"DAILY", "WEEKLY", "MONTHLY"})
    private String granularity = "DAILY";

    @Pattern(regexp = "^(USER|COURSE|SUBJECT)$", message = "分析维度不合法")
    @Schema(description = "分析维度", example = "USER",
            allowableValues = {"USER", "COURSE", "SUBJECT"})
    private String dimension = "USER";

    @Schema(description = "实体ID（按特定用户/课程/学科筛选时使用）", example = "1001")
    private Long entityId;

    // ====================== Getter & Setter ======================

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getGranularity() {
        return granularity;
    }

    public void setGranularity(String granularity) {
        this.granularity = granularity;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }
}
