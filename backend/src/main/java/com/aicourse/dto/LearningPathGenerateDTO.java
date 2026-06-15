package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * AI生成学习路径请求DTO
 * 用于接收前端发起AI智能生成个性化学习路径时的参数
 */
@Schema(description = "AI生成学习路径请求")
public class LearningPathGenerateDTO {

    @NotNull(message = "课程ID不能为空")
    @Schema(description = "目标课程ID", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long courseId;

    @Min(value = 1, message = "目标天数至少为1天")
    @Max(value = 365, message = "目标天数不超过365天")
    @Schema(description = "计划完成天数", example = "30")
    private Integer targetDays;

    @DecimalMin(value = "0.5", message = "每日学习时长至少0.5小时")
    @DecimalMax(value = "24", message = "每日学习时长不超过24小时")
    @Schema(description = "每日计划学习时长（小时）", example = "2.0")
    private Double dailyHours;

    @Pattern(regexp = "^(BEGINNER|INTERMEDIATE|ADVANCED)$", message = "当前水平不合法")
    @Schema(description = "当前水平", example = "BEGINNER",
            allowableValues = {"BEGINNER", "INTERMEDIATE", "ADVANCED"})
    private String currentLevel = "BEGINNER";

    @Schema(description = "重点关注的知识领域/知识点列表", example = "[\"面向对象\", \"集合框架\", \"异常处理\"]")
    private List<String> focusAreas;

    // ====================== Getter & Setter ======================

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getTargetDays() { return targetDays; }
    public void setTargetDays(Integer targetDays) { this.targetDays = targetDays; }
    public Double getDailyHours() { return dailyHours; }
    public void setDailyHours(Double dailyHours) { this.dailyHours = dailyHours; }
    public String getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(String currentLevel) { this.currentLevel = currentLevel; }
    public List<String> getFocusAreas() { return focusAreas; }
    public void setFocusAreas(List<String> focusAreas) { this.focusAreas = focusAreas; }
}
