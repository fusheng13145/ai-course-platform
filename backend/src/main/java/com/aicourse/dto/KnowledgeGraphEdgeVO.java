package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * 知识图谱边VO
 * 用于描述知识图谱中两个节点之间的关系类型、权重和标签
 */
@Schema(description = "知识图谱边")
public class KnowledgeGraphEdgeVO {

    @Schema(description = "起始节点ID", example = "101")
    private Long source;

    @Schema(description = "目标节点ID", example = "102")
    private Long target;

    @Pattern(regexp = "^(PREREQUISITE|RELATED|SIMILAR)$", message = "关系类型不合法")
    @Schema(description = "关系类型", example = "PREREQUISITE",
            allowableValues = {"PREREQUISITE", "RELATED", "SIMILAR"})
    private String relationType;

    @Schema(description = "关系权重（影响连线的粗细或透明度）", example = "0.8")
    private Double weight;

    @Schema(description = "边上显示的文字标签", example = "前置依赖")
    private String label;

    // ====================== Getter & Setter ======================

    public Long getSource() {
        return source;
    }

    public void setSource(Long source) {
        this.source = source;
    }

    public Long getTarget() {
        return target;
    }

    public void setTarget(Long target) {
        this.target = target;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
