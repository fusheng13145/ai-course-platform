package com.aicourse.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程知识图谱可视化图数据出参（供 ECharts/D3 渲染）。
 * 字段名与历史 Map 键一致（nodes / links）。
 * nodes / links 原为 {@code List<Map<String,Object>>} 嵌套，一并结构化为
 * {@link GraphDataNodeVO} / {@link GraphDataLinkVO} 列表，JSON 键保持不变。
 * snapshotVersion/generatedAt 为阶段 3 新增的数据集标识，旧前端可安全忽略。
 */
@Data
public class KnowledgeGraphDataVO {

    private List<GraphDataNodeVO> nodes;
    private List<GraphDataLinkVO> links;
    private String snapshotVersion;
    private LocalDateTime generatedAt;

    public KnowledgeGraphDataVO() {
    }

    public KnowledgeGraphDataVO(List<GraphDataNodeVO> nodes, List<GraphDataLinkVO> links) {
        this.nodes = nodes;
        this.links = links;
    }

    public KnowledgeGraphDataVO(List<GraphDataNodeVO> nodes, List<GraphDataLinkVO> links,
                                String snapshotVersion, LocalDateTime generatedAt) {
        this.nodes = nodes;
        this.links = links;
        this.snapshotVersion = snapshotVersion;
        this.generatedAt = generatedAt;
    }
}
