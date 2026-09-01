package com.aicourse.vo;

import lombok.Data;

import java.util.List;

/**
 * 课程知识图谱可视化图数据出参（供 ECharts/D3 渲染）。
 * 字段名与历史 Map 键一致（nodes / links）。
 * nodes / links 原为 {@code List<Map<String,Object>>} 嵌套，一并结构化为
 * {@link GraphDataNodeVO} / {@link GraphDataLinkVO} 列表，JSON 键保持不变。
 * 原实现无条件 put 此两键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 */
@Data
public class KnowledgeGraphDataVO {

    private List<GraphDataNodeVO> nodes;
    private List<GraphDataLinkVO> links;

    public KnowledgeGraphDataVO() {
    }

    public KnowledgeGraphDataVO(List<GraphDataNodeVO> nodes, List<GraphDataLinkVO> links) {
        this.nodes = nodes;
        this.links = links;
    }
}
