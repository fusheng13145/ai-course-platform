package com.aicourse.vo;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 单个知识点的关联关系出参（节点 + 边）。
 * 字段名与历史 Map 键一致（nodes / edges），保证前端零改动。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KnowledgePointRelationsVO {

    private List<KnowledgePoint> nodes;
    private List<KnowledgePointRelation> edges;

    public KnowledgePointRelationsVO() {
    }

    public KnowledgePointRelationsVO(List<KnowledgePoint> nodes, List<KnowledgePointRelation> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }
}
