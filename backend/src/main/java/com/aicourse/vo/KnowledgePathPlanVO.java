package com.aicourse.vo;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;

import lombok.Data;

import java.util.List;

/**
 * 知识学习路径规划结果 VO。
 * <p>
 * 由 {@code KnowledgeGraphService.planLearningPath} 产出，封装从起点到目标知识点的
 * 最优（最短跳数）学习路径。当图谱引擎（Python 8001）未启用/不可达时，由消费侧
 * 本地 BFS 兜底生成（{@code mode=LOCAL}），保证端点始终返回真实可运行结果。
 */
@Data
public class KnowledgePathPlanVO {

    /** 起点知识点ID */
    private Long sourceId;

    /** 目标知识点ID */
    private Long targetId;

    /** 有序路径节点（从起点到目标，含两端） */
    private List<KnowledgePoint> path;

    /** 路径上相邻节点间的边（关系） */
    private List<KnowledgePointRelation> edges;

    /** 实际跳数（路径边数） */
    private int hops;

    /** 是否找到可达路径 */
    private boolean found;

    /**
     * 规划来源模式：
     * <ul>
     *   <li>{@code ENGINE} —— 由 Python 知识图谱微服务（8001）计算返回；</li>
     *   <li>{@code LOCAL} —— 引擎未启用/不可达，由本端消费侧 BFS 兜底；</li>
     *   <li>{@code UNAVAILABLE} —— 引擎不可用且本地无消费侧数据（降级占位）。</li>
     * </ul>
     */
    private String mode;

    /** 降级/兜底说明（可选，便于前端提示） */
    private String note;
}
