package com.aicourse.integration.kg;

import com.aicourse.integration.ContractPaths;

import java.util.List;

/**
 * 知识图谱集成契约（标准化接口）。
 * <p>
 * 职责边界（2026-08-13 用户明确）：知识图谱引擎（图谱构建、多跳推理、GraphRAG）由团队另一成员
 * 以 Python 微服务（端口 8001）实现。本接口是本端与 Python 端的<b>唯一对接契约</b>——定义了
 * 清晰的函数签名、数据结构（请求/响应记录）与调用约定，便于另一成员独立实现、也便于本端
 * 在不依赖具体 Python 实现的前提下完成消费侧逻辑。
 * </p>
 * <p>
 * <b>两种实现</b>：
 * <ul>
 *   <li>{@link LocalKnowledgeGraphProvider} —— <b>当前阶段默认占位实现</b>，不依赖任何 Python 服务，
 *       直接基于消费侧 MySQL（{@code knowledge_points}/{@code knowledge_point_relations}）做本地 BFS 与子图查询；</li>
 *   <li>{@link HttpKnowledgeGraphProvider} —— 可选远端实现，经 {@code knowledge-graph.enabled=true}
 *       启用，按 {@link ContractPaths} 调用 Python 8001 的 {@code /api/v1/graph/*} 契约；
 *       路径规划在引擎不可达时回退本地 BFS，摄取/子图在不可达时返回 {@code 503} 降级。</li>
 * </ul>
 * </p>
 *
 * <h3>调用约定（HTTP 方法 + 路径 + JSON 字段，详见 {@link ContractPaths}）</h3>
 * <table border="1">
 *   <tr><th>方法</th><th>路径</th><th>请求体关键字段</th><th>响应关键字段</th></tr>
 *   <tr><td>GET</td><td>{@code /api/v1/graph/health}</td><td>-</td><td>任意 2xx 即健康</td></tr>
 *   <tr><td>POST</td><td>{@code /api/v1/graph/path/plan}</td><td>source_id, target_id, max_hops</td><td>path[], edges[], hops</td></tr>
 *   <tr><td>POST</td><td>{@code /api/v1/graph/ingest}</td><td>course_id, nodes[], relations[]</td><td>status, course_id, synced_nodes, upserted_nodes, upserted_relations, pruned_nodes</td></tr>
 *   <tr><td>POST</td><td>{@code /api/v1/graph/subgraph}</td><td>course_id, max_hops</td><td>status, nodes[], edges[]</td></tr>
 * </table>
 * 状态码语义：{@code 200} 成功；{@code 503} 引擎未接入/不可用（降级占位）。
 */
public interface KnowledgeGraphContract {

    /** 引擎健康检查。占位实现（无 Python）返回 false；启用远端时返回 Python 8001 可达性。 */
    HealthStatus healthCheck();

    /**
     * 起点到目标知识点的最优（最短跳数）学习路径规划。
     *
     * @param request 含起点/目标知识点ID与最大跳数
     * @return 路径规划结果（有序节点ID、边、跳数、是否找到、模式 ENGINE/LOCAL）
     */
    PathPlanResult planLearningPath(PathPlanRequest request);

    /**
     * 向图谱引擎提交某课程的知识点/关系<b>全量快照</b>（幂等摄取）。
     * <p>快照由消费侧 MySQL 生成，引擎按 {@code id} MERGE 节点、重建该课程出边并清理快照外的
     * 陈旧节点——两侧数据以 MySQL 为唯一事实来源，不再各算各的。</p>
     *
     * @param request 课程ID + 该课程知识点/关系快照
     * @return 摄取结果（code/mode/status/syncedNodes/syncedRelations）
     */
    IngestResult ingest(IngestRequest request);

    /**
     * 获取课程全量子图（节点 + 边）。
     *
     * @param courseId 课程ID
     * @param maxHops  子图最大跳数（<=0 取实现默认）
     * @return 子图结果（nodes/edges/mode）
     */
    SubGraphResult getSubGraph(Long courseId, int maxHops);

    /** 规划来源模式：ENGINE=Python 引擎计算；LOCAL=消费侧本地兜底；UNAVAILABLE=降级占位。 */
    enum Mode {
        ENGINE,
        LOCAL,
        UNAVAILABLE
    }

    /** 健康状态。 */
    record HealthStatus(boolean healthy) {
    }

    /** 路径规划请求。 */
    record PathPlanRequest(Long sourceId, Long targetId, int maxHops) {
    }

    /** 路径中的有向边（含关系类型）。 */
    record PathEdge(Long sourceId, Long targetId, String relationType) {
    }

    /** 摄取节点：消费侧 MySQL 知识点快照的一条（id 与 MySQL 知识点主键一致）。 */
    record IngestNode(Long id, Long courseId, String name, String content, String type) {
    }

    /** 摄取关系：消费侧 MySQL 知识点关系快照的一条（id 与关系主键一致）。 */
    record IngestRelation(Long id, Long sourceId, Long targetId, String relationType) {
    }

    /** 摄取请求：某课程的全量快照。 */
    record IngestRequest(Long courseId, List<IngestNode> nodes, List<IngestRelation> relations) {
    }

    /**
     * 路径规划结果（数据结构定义）。
     * <ul>
     *   <li>{@code path}：有序知识点ID列表（从起点到目标，含两端）；空表示未找到；</li>
     *   <li>{@code edges}：路径相邻节点间的关系边；</li>
     *   <li>{@code hops}：实际跳数（边数）；</li>
     *   <li>{@code found}：是否找到可达路径；</li>
     *   <li>{@code mode}：来源模式（ENGINE/LOCAL）；</li>
     *   <li>{@code note}：兜底/降级说明。</li>
     * </ul>
     */
    record PathPlanResult(Long sourceId, Long targetId, List<Long> path, List<PathEdge> edges,
                          int hops, boolean found, Mode mode, String note) {
    }

    /**
     * 摄取结果（数据结构定义）。
     * <ul>
     *   <li>{@code code}：200 成功 / 503 降级；</li>
     *   <li>{@code status}：引擎返回的同步状态（占位实现为 null）；</li>
     *   <li>{@code mode}：LOCAL（占位）/ ENGINE；</li>
     *   <li>{@code syncedNodes}：摄取后引擎内该课程的节点总数；</li>
     *   <li>{@code syncedRelations}：本次实际写入/确认的关系数量。</li>
     * </ul>
     */
    record IngestResult(int code, String status, String mode, Long courseId,
                        long syncedNodes, long syncedRelations, String note) {
    }

    /**
     * 子图结果（数据结构定义）。
     * <ul>
     *   <li>{@code code}：200 成功 / 503 降级；</li>
     *   <li>{@code nodes}/{@code edges}：课程知识点节点与关系边；</li>
     *   <li>{@code mode}：LOCAL（占位）/ ENGINE。</li>
     * </ul>
     */
    record SubGraphResult(int code, String status, String mode, Long courseId, int maxHops,
                          List<Object> nodes, List<Object> edges, String note) {
    }
}
