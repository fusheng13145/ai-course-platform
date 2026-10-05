package com.aicourse.integration.kg;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.integration.ContractPaths;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱<b>本地占位实现</b>（当前阶段默认）。
 * <p>
 * 不依赖任何 Python 服务，完全基于消费侧 MySQL 的 {@code knowledge_points} /
 * {@code knowledge_point_relations} 完成：
 * <ul>
 *   <li>{@link #planLearningPath} —— 无向邻接 BFS 最短跳数路径规划（未指定跳数即给出真实最短路径）；</li>
 *   <li>{@link #ingest} —— 仅确认消费侧该课程知识点数量（不真正摄取至引擎）；</li>
 *   <li>{@link #getSubGraph} —— 直接返回消费侧该课程的知识点节点与关系边；</li>
 *   <li>{@link #healthCheck} —— 返回 false（无 Python 引擎）。</li>
 * </ul>
 * 这是「当前阶段仅实现接口定义与占位逻辑，不依赖具体 Python 实现」的落地：系统默认以本类运行，
 * 接入 Python 8001 后经 {@code knowledge-graph.enabled=true} 切换为 {@link HttpKnowledgeGraphProvider}。
 * </p>
 */
@Slf4j
public class LocalKnowledgeGraphProvider implements KnowledgeGraphContract {

    /** 请求未指定跳数（{@code <=0}）时的语义：不设上限，直接给出真实最短路径。 */
    private static final int UNLIMITED_HOPS = Integer.MAX_VALUE;

    private final KnowledgePointService knowledgePointService;
    private final KnowledgePointRelationService relationService;
    /** 仅用于 {@link #getSubGraph} 的子图扩图跳数缺省值（GraphRAG 语境，与路径规划无关）。 */
    private final int defaultMaxHops;

    public LocalKnowledgeGraphProvider(KnowledgePointService knowledgePointService,
                                       KnowledgePointRelationService relationService,
                                       int defaultMaxHops) {
        this.knowledgePointService = knowledgePointService;
        this.relationService = relationService;
        this.defaultMaxHops = Math.max(defaultMaxHops, 1);
    }

    @Override
    public HealthStatus healthCheck() {
        // 本地占位无 Python 引擎，健康状态恒为 false（由 HttpKnowledgeGraphProvider 探活远端）
        return new HealthStatus(false);
    }

    @Override
    public PathPlanResult planLearningPath(PathPlanRequest request) {
        long sourceId = request.sourceId() == null ? -1 : request.sourceId();
        long targetId = request.targetId() == null ? -1 : request.targetId();
        int maxHops = request.maxHops() > 0 ? request.maxHops() : UNLIMITED_HOPS;

        if (request.sourceId() == null || request.targetId() == null) {
            return new PathPlanResult(request.sourceId(), request.targetId(),
                    Collections.emptyList(), Collections.emptyList(), 0, false, Mode.LOCAL,
                    "起点或目标知识点ID为空");
        }

        // 按起点知识点归属课程隔离关系加载：既避免每次全表加载所有课程关系（O(全表)），
        // 也消除跨课程知识点被错误连通的风险（BFS 仅在同课程关系图上运行）。
        // 起点知识点不存在/归属课程未知时回退全量加载，保持占位场景可用性。
        Long sourceCourseId = null;
        try {
            KnowledgePoint sourcePoint = knowledgePointService.getById(sourceId);
            if (sourcePoint != null) {
                sourceCourseId = sourcePoint.getCourseId();
            }
        } catch (Exception e) {
            log.debug("本地路径规划查询起点知识点归属课程失败：{}", e.getMessage());
        }

        List<KnowledgePointRelation> allRelations = sourceCourseId != null
                ? safeList(relationService.listByCourse(sourceCourseId))
                : safeList(relationService.list());
        Map<Long, List<Edge>> adj = new HashMap<>();
        for (KnowledgePointRelation r : allRelations) {
            if (r.getSourceId() == null || r.getTargetId() == null) {
                continue;
            }
            adj.computeIfAbsent(r.getSourceId(), k -> new ArrayList<>())
                    .add(new Edge(r.getSourceId(), r.getTargetId(), r.getRelationType()));
            adj.computeIfAbsent(r.getTargetId(), k -> new ArrayList<>())
                    .add(new Edge(r.getTargetId(), r.getSourceId(), r.getRelationType()));
        }

        if (!adj.containsKey(sourceId)) {
            return new PathPlanResult(request.sourceId(), request.targetId(),
                    Collections.emptyList(), Collections.emptyList(), 0, false, Mode.LOCAL,
                    "起点知识点不存在任何关联关系，无法进行路径规划");
        }

        // 一次全量 BFS 求出起点到各连通点的最短跳数，并记录前驱以回溯路径。
        // 跳数上限只用于「是否采纳」的判定，不参与遍历剪枝：课程图规模有限（数百节点/数百边），
        // 全量遍历开销可忽略，换来的是上限不足时仍能回传真实最短跳数，
        // 避免用户面对一句无信息量的「未找到」。
        Map<Long, Long> prev = new HashMap<>();
        Map<Long, Integer> dist = new HashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(sourceId);
        dist.put(sourceId, 0);
        prev.put(sourceId, null);

        while (!queue.isEmpty()) {
            long cur = queue.removeFirst();
            int d = dist.get(cur);
            for (Edge e : adj.getOrDefault(cur, Collections.emptyList())) {
                if (!dist.containsKey(e.to)) {
                    dist.put(e.to, d + 1);
                    prev.put(e.to, cur);
                    queue.add(e.to);
                }
            }
        }

        Integer shortest = dist.get(targetId);
        if (shortest == null) {
            return new PathPlanResult(request.sourceId(), request.targetId(),
                    Collections.emptyList(), Collections.emptyList(), 0, false, Mode.LOCAL,
                    "起点与目标之间不存在任何可达路径（两者不在同一连通分量内）");
        }
        if (shortest > maxHops) {
            return new PathPlanResult(request.sourceId(), request.targetId(),
                    Collections.emptyList(), Collections.emptyList(), 0, false, Mode.LOCAL,
                    "两点最短需 " + shortest + " 跳，超过所设上限 " + maxHops + " 跳");
        }

        // 回溯路径（有序节点ID）
        List<Long> idPath = new ArrayList<>();
        long step = targetId;
        while (step != -1) {
            idPath.add(step);
            Long p = prev.get(step);
            step = (p == null) ? -1 : p;
        }
        Collections.reverse(idPath);

        int hops = shortest;

        // 收集相邻边（PathEdge）
        List<PathEdge> edges = new ArrayList<>();
        for (int i = 0; i < idPath.size() - 1; i++) {
            long a = idPath.get(i);
            long b = idPath.get(i + 1);
            for (KnowledgePointRelation r : allRelations) {
                if ((r.getSourceId() != null && r.getSourceId().equals(a) && r.getTargetId() != null && r.getTargetId().equals(b))
                        || (r.getSourceId() != null && r.getSourceId().equals(b) && r.getTargetId() != null && r.getTargetId().equals(a))) {
                    edges.add(new PathEdge(a, b, r.getRelationType()));
                    break;
                }
            }
        }

        return new PathPlanResult(request.sourceId(), request.targetId(), idPath, edges, hops,
                true, Mode.LOCAL, "由消费侧本地 BFS 兜底（基于知识点关系表）");
    }

    @Override
    public IngestResult ingest(Long courseId) {
        long pointCount = 0;
        try {
            if (courseId != null) {
                pointCount = knowledgePointService.countByCourse(courseId);
            }
        } catch (Exception e) {
            log.debug("本地摄取统计知识点失败：{}", e.getMessage());
        }
        return new IngestResult(200, null, ContractPaths.MODE_LOCAL, courseId, pointCount,
                "知识图谱引擎未接入，已在消费侧确认知识点数据（未真正摄取至引擎）");
    }

    @Override
    public SubGraphResult getSubGraph(Long courseId, int maxHops) {
        int hops = maxHops > 0 ? maxHops : defaultMaxHops;
        List<KnowledgePoint> nodes = Collections.emptyList();
        List<KnowledgePointRelation> edges = Collections.emptyList();
        try {
            if (courseId != null) {
                nodes = safeList(knowledgePointService.listByCourse(courseId));
                edges = safeList(relationService.listByCourse(courseId));
            }
        } catch (Exception e) {
            log.debug("本地子图查询失败：{}", e.getMessage());
        }
        return new SubGraphResult(200, null, ContractPaths.MODE_LOCAL, courseId, hops,
                new ArrayList<>(nodes), new ArrayList<>(edges),
                "知识图谱引擎未接入，已基于消费侧 MySQL 返回课程子图（本地降级）");
    }

    private static <T> List<T> safeList(List<T> in) {
        return in == null ? Collections.emptyList() : in;
    }

    /** 本地 BFS 用轻量有向边（含关系类型）。 */
    private static class Edge {
        final long from;
        final long to;
        final String relationType;

        Edge(long from, long to, String relationType) {
            this.from = from;
            this.to = to;
            this.relationType = relationType;
        }
    }
}
