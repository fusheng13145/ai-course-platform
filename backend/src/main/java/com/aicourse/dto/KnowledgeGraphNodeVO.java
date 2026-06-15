package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 知识图谱节点VO
 * 用于描述知识图谱中每个节点的属性信息，供前端可视化渲染使用
 */
@Schema(description = "知识图谱节点")
public class KnowledgeGraphNodeVO {

    @Schema(description = "节点ID", example = "101")
    private Long id;

    @Schema(description = "知识点名称", example = "面向对象编程")
    private String name;

    @Schema(description = "分类/类别", example = "核心概念")
    private String category;

    @Schema(description = "难度等级(1-5)", example = "3")
    private Integer difficultyLevel;

    @Schema(description = "重要度(1-5)", example = "4")
    private Integer importance;

    @Schema(description = "当前用户掌握度(0-100)", example = "75")
    private Integer mastery;

    @Schema(description = "节点显示大小（根据importance自动计算）", example = "40")
    private Integer size;

    @Schema(description = "节点显示颜色", example = "#4CAF50")
    private String color;

    // ====================== Getter & Setter ======================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(Integer difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }

    public Integer getImportance() {
        return importance;
    }

    public void setImportance(Integer importance) {
        this.importance = importance;
    }

    public Integer getMastery() {
        return mastery;
    }

    public void setMastery(Integer mastery) {
        this.mastery = mastery;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
