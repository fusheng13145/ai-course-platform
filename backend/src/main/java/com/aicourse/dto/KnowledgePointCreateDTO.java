package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * 创建知识点请求DTO
 * 用于接收教师/管理员创建课程知识点时的请求数据
 */
@Schema(description = "创建知识点请求")
public class KnowledgePointCreateDTO {

    @NotNull(message = "所属课程ID不能为空")
    @Schema(description = "所属课程ID", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long courseId;

    @NotBlank(message = "知识点名称不能为空")
    @Size(max = 100, message = "知识点名称不能超过100个字符")
    @Schema(description = "知识点名称", example = "面向对象编程",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 2000, message = "描述不能超过2000个字符")
    @Schema(description = "知识点详细描述", example = "面向对象是一种程序设计思想，核心概念包括封装、继承、多态...")
    private String description;

    @Schema(description = "父级知识点ID（顶级节点不填或传null）", example = "101")
    private Long parentId;

    @Min(value = 1, message = "难度等级最低为1")
    @Max(value = 5, message = "难度等级最高为5")
    @Schema(description = "难度等级(1-5)", example = "3")
    private Integer difficultyLevel = 1;

    @Min(value = 1, message = "重要度最低为1")
    @Max(value = 5, message = "重要度最高为5")
    @Schema(description = "重要度(1-5，数值越大越重要)", example = "4")
    private Integer importance = 3;

    @DecimalMin(value = "0.1", message = "预估学习时长至少为0.1小时")
    @DecimalMax(value = "500", message = "预估学习时长不超过500小时")
    @Schema(description = "预估学习时长（小时）", example = "4.5")
    private Double estimatedHours;

    @Schema(description = "标签列表，逗号分隔", example = "核心概念,必考")
    private List<String> tags;

    @Size(max = 20, message = "颜色值不能超过20个字符")
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$|^[a-zA-Z]+$", message = "颜色格式不正确")
    @Schema(description = "显示颜色（十六进制或颜色名）", example = "#4CAF50")
    private String color;

    // ====================== Getter & Setter ======================

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(Integer difficultyLevel) { this.difficultyLevel = difficultyLevel; }
    public Integer getImportance() { return importance; }
    public void setImportance(Integer importance) { this.importance = importance; }
    public Double getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(Double estimatedHours) { this.estimatedHours = estimatedHours; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
