package com.aicourse.service;

import com.aicourse.vo.KnowledgePathPlanVO;

/**
 * 知识图谱服务接口。
 * <p>
 * 职责边界：知识图谱引擎（图谱构建、多跳推理、GraphRAG）
 * 整体由团队另一成员负责（Python 微服务，端口 8001）。本端按下游对接契约仅保留
 * 标准化接口与本 HTTP 代理，并通过 {@code knowledge-graph.enabled=true}（{@code KG_ENABLED}）
 * + {@code KG_BASE_URL}（默认 http://localhost:8001）启用，调用 Python 端实现的
 * {@code /api/v1/graph/*} 契约（权威单一事实来源见 docs/项目手册.md 第八部分）。
 * </p>
 * <p>
 * 与 {@code RAGService} 的契约保持一致：服务未启用或不可达时返回 503 占位；
 * 但为满足 PRD {@code POST /api/knowledge/path/plan}「当前即可运行」的要求，
 * {@link #planLearningPath} 在引擎未启用/不可达时由消费侧本地 BFS 兜底
 * （直读 MySQL {@code knowledge_points} / {@code knowledge_point_relations}），
 * 而非降级 503 —— 即「路径规划端点始终返回真实数据」，与其余纯代理方法区分。
 * </p>
 * <p>
 * 本接口修复原《研读报告》§2.7 所述「文档声称预留，代码缺失」的出入：
 * 现接口与方法签名已真实落地并与 {@code KnowledgeController} 接线。
 * </p>
 */
public interface KnowledgeGraphService {

    /**
     * 图谱引擎健康检查
     * @return true 表示 Python 知识图谱微服务（8001）可达
     */
    boolean healthCheck();

    /**
     * 起点到目标知识点的最优（最短跳数）学习路径规划。
     * <p>
     * 优先调用 Python 图谱引擎（8001）执行多跳推理；当引擎未启用或不可达时，
     * 由本端消费侧基于 {@code knowledge_point_relations} 的本地 BFS 兜底，
     * 保证端点始终返回真实路径数据。
     * </p>
     *
     * @param sourceId 起点知识点ID
     * @param targetId 目标知识点ID
     * @param maxHops  最大搜索跳数；{@code <=0} 表示不限跳数，返回两点间真实最短路径
     * @return 路径规划结果（含有序节点、边、跳数与规划模式）
     */
    KnowledgePathPlanVO planLearningPath(Long sourceId, Long targetId, int maxHops);

    /**
     * 向图谱引擎提交知识点/关系（摄取），或触发子图重算。
     * 引擎未启用/不可达时返回 {@code {"code":503}} 占位。
     *
     * @param courseId 课程ID
     * @return 操作结果 JSON
     */
    String ingest(Long courseId);

    /**
     * 获取图谱引擎侧的全量子图（含引擎特有属性，如 GraphRAG 社区摘要）。
     * 引擎未启用/不可达时返回 {@code {"code":503}} 占位。
     *
     * @param courseId 课程ID
     * @param maxHops  子图最大跳数
     * @return 子图 JSON
     */
    String getSubGraph(Long courseId, int maxHops);
}
