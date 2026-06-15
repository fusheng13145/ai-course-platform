package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 知识图谱数据响应VO
 * 用于返回给前端的知识图谱完整数据，包含节点、边和元数据统计信息
 */
@Schema(description = "知识图谱数据响应")
public class KnowledgeGraphVO {

    @Schema(description = "图谱节点列表")
    private List<KnowledgeGraphNodeVO> nodes;

    @Schema(description = "图谱边列表（节点间关系）")
    private List<KnowledgeGraphEdgeVO> edges;

    @Schema(description = "图谱元数据统计")
    private GraphMetadata metadata;

    /**
     * 图谱元数据内部类
     */
    @Schema(description = "图谱元数据")
    public static class GraphMetadata {

        @Schema(description = "总节点数", example = "45")
        private Integer totalNodes;

        @Schema(description = "总边数", example = "78")
        private Integer totalEdges;

        @Schema(description = "最大层级深度", example = "5")
        private Integer maxDepth;

        // ====================== Getter & Setter ======================

        public Integer getTotalNodes() { return totalNodes; }
        public void setTotalNodes(Integer totalNodes) { this.totalNodes = totalNodes; }
        public Integer getTotalEdges() { return totalEdges; }
        public void setTotalEdges(Integer totalEdges) { this.totalEdges = totalEdges; }
        public Integer getMaxDepth() { return maxDepth; }
        public void setMaxDepth(Integer maxDepth) { this.maxDepth = maxDepth; }
    }

    // ====================== Getter & Setter ======================

    public List<KnowledgeGraphNodeVO> getNodes() { return nodes; }
    public void setNodes(List<KnowledgeGraphNodeVO> nodes) { this.nodes = nodes; }
    public List<KnowledgeGraphEdgeVO> getEdges() { return edges; }
    public void setEdges(List<KnowledgeGraphEdgeVO> edges) { this.edges = edges; }
    public GraphMetadata getMetadata() { return metadata; }
    public void setMetadata(GraphMetadata metadata) { this.metadata = metadata; }
}
