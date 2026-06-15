package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * 学习路径节点传输DTO
 * 用于描述学习路径中每个节点的信息，包括关联知识点、类型、资源和前置依赖
 */
@Schema(description = "学习路径节点传输对象")
public class LearningPathNodeDTO {

    @Schema(description = "节点ID", example = "1001")
    private Long nodeId;

    @NotNull(message = "关联知识点ID不能为空")
    @Schema(description = "关联的知识点ID", example = "101",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long knowledgePointId;

    @Schema(description = "知识点名称", example = "Java基础语法")
    private String knowledgePointName;

    @Pattern(regexp = "^(LEARNING|PRACTICE|EXAM|REVIEW)$", message = "节点类型不合法")
    @Schema(description = "节点类型", example = "LEARNING",
            allowableValues = {"LEARNING", "PRACTICE", "EXAM", "REVIEW"})
    private String nodeType = "LEARNING";

    @Min(value = 0, message = "排序序号不能为负数")
    @Schema(description = "在路径中的排序索引（从0开始）", example = "0")
    private Integer orderIndex = 0;

    @Min(value = 1, message = "计划分钟数至少为1")
    @Schema(description = "预计学习时长（分钟）", example = "60")
    private Integer plannedMinutes;

    @Min(value = 1, message = "难度最低为1")
    @Max(value = 5, message = "难度最高为5")
    @Schema(description = "难度等级(1-5)", example = "2")
    private Integer difficulty;

    @Schema(description = "前置节点ID列表（必须先完成这些节点才能学习本节点）", example = "[1000]")
    private List<Long> prerequisites;

    @Schema(description = "关联资源列表", example = "[{\"name\":\"视频\",\"url\":\"https://...\"}]")
    private List<ResourceRef> resources;

    /**
     * 资源引用内部类
     */
    @Schema(description = "资源引用")
    public static class ResourceRef {

        @Schema(description = "资源名称", example = "第一章视频")
        private String name;

        @Schema(description = "资源URL地址", example = "https://example.com/video/ch1.mp4")
        private String url;

        @Schema(description = "资源类型", example = "VIDEO")
        private String type;

        // ====================== Getter & Setter ======================

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    // ====================== Getter & Setter ======================

    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
    public Long getKnowledgePointId() { return knowledgePointId; }
    public void setKnowledgePointId(Long knowledgePointId) { this.knowledgePointId = knowledgePointId; }
    public String getKnowledgePointName() { return knowledgePointName; }
    public void setKnowledgePointName(String knowledgePointName) { this.knowledgePointName = knowledgePointName; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }
    public Integer getPlannedMinutes() { return plannedMinutes; }
    public void setPlannedMinutes(Integer plannedMinutes) { this.plannedMinutes = plannedMinutes; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public List<Long> getPrerequisites() { return prerequisites; }
    public void setPrerequisites(List<Long> prerequisites) { this.prerequisites = prerequisites; }
    public List<ResourceRef> getResources() { return resources; }
    public void setResources(List<ResourceRef> resources) { this.resources = resources; }
}
