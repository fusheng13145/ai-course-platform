package com.aicourse.vo;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 课程知识图谱全量出参（节点 + 边 + 统计）。
 * 字段名与历史 Map 键一致（nodes / edges / totalPoints / totalRelations），保证前端零改动。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KnowledgeGraphVO {

    private List<KnowledgePoint> nodes;
    private List<KnowledgePointRelation> edges;
    private Integer totalPoints;
    private Integer totalRelations;

    public KnowledgeGraphVO() {
    }

    public KnowledgeGraphVO(List<KnowledgePoint> nodes, List<KnowledgePointRelation> edges,
                           Integer totalPoints, Integer totalRelations) {
        this.nodes = nodes;
        this.edges = edges;
        this.totalPoints = totalPoints;
        this.totalRelations = totalRelations;
    }
}
