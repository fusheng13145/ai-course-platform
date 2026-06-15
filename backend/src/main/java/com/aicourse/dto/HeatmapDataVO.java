package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * 热力图数据点VO
 * 用于描述学习热力图中每一天的学习强度和关联课程信息
 */
@Schema(description = "热力图数据点")
public class HeatmapDataVO {

    @Schema(description = "日期（yyyy-MM-dd格式）", example = "2024-12-15")
    private String date;

    @Schema(description = "当天学习时长（分钟）", example = "120")
    private Integer value;

    @Min(value = 1, message = "强度等级最低为1")
    @Max(value = 4, message = "强度等级最高为4")
    @Schema(description = "热力强度等级(1-4，根据value自动计算)", example = "3")
    private Integer level;

    @Schema(description = "当天学习的课程名称列表", example = "[\"Java程序设计\", \"数据结构\"]")
    private List<String> courses;

    // ====================== Getter & Setter ======================

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public List<String> getCourses() {
        return courses;
    }

    public void setCourses(List<String> courses) {
        this.courses = courses;
    }
}
