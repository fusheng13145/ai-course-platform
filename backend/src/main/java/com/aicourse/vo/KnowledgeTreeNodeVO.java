package com.aicourse.vo;

import lombok.Data;

import java.util.List;

/**
 * 知识树节点出参（GET /api/knowledge/points/tree/{courseId}）。
 * 字段名与历史 Map 键一致（id / name / description / difficultyLevel / importance /
 * estimatedTime / pointType / orderIndex / children）。
 * 递归自引用树结构（children 为子节点列表，无子节点时空列表），
 * 精确复刻历史 LinkedHashMap 组装顺序与恒含键集。
 */
@Data
public class KnowledgeTreeNodeVO {

    private Long id;
    private String name;
    private String description;
    private String difficultyLevel;
    private Integer importance;
    private Integer estimatedTime;
    private String pointType;
    private Integer orderIndex;
    private List<KnowledgeTreeNodeVO> children;

    public KnowledgeTreeNodeVO() {
    }

    public KnowledgeTreeNodeVO(Long id, String name, String description, String difficultyLevel,
                               Integer importance, Integer estimatedTime, String pointType,
                               Integer orderIndex, List<KnowledgeTreeNodeVO> children) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.difficultyLevel = difficultyLevel;
        this.importance = importance;
        this.estimatedTime = estimatedTime;
        this.pointType = pointType;
        this.orderIndex = orderIndex;
        this.children = children;
    }
}
