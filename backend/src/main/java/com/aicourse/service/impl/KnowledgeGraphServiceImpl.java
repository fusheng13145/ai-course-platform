package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.integration.ContractPaths;
import com.aicourse.integration.kg.HttpKnowledgeGraphProvider;
import com.aicourse.integration.kg.KnowledgeGraphContract;
import com.aicourse.integration.kg.KnowledgeGraphContract.PathEdge;
import com.aicourse.integration.kg.KnowledgeGraphContract.PathPlanRequest;
import com.aicourse.integration.kg.KnowledgeGraphContract.PathPlanResult;
import com.aicourse.integration.kg.LocalKnowledgeGraphProvider;
import com.aicourse.service.KnowledgeGraphService;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.vo.KnowledgePathPlanVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱服务实现（门面）。
 * <p>
 * 本类实现业务接口 {@link KnowledgeGraphService}，内部按 {@code knowledge-graph.enabled}
 * 委派到标准化契约 {@link KnowledgeGraphContract} 的具体实现：
 * <ul>
 *   <li>{@code false}（默认）→ {@link LocalKnowledgeGraphProvider}：本地占位，不依赖 Python；</li>
 *   <li>{@code true} → {@link HttpKnowledgeGraphProvider}：HTTP 对接 Python 知识图谱微服务 8001。</li>
 * </ul>
 * 契约接口、数据结构与调用约定集中在 {@code com.aicourse.integration.kg} 包，便于另一成员独立实现 Python 端。
 * </p>
 */
@Service
@Slf4j
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    private static final ObjectMapper OM = new ObjectMapper();

    private final KnowledgeGraphContract provider;
    private final KnowledgePointService knowledgePointService;
    private final KnowledgePointRelationService relationService;

    /**
     * 兼容旧签名（internalApiKey 为空：不发内部认证头），供既有单测直构使用。
     */
    public KnowledgeGraphServiceImpl(
            boolean enabled,
            String baseUrl,
            int connectTimeout,
            int readTimeout,
            int maxRetries,
            String healthPath,
            int defaultMaxHops,
            KnowledgePointService knowledgePointService,
            KnowledgePointRelationService relationService) {
        this(enabled, baseUrl, connectTimeout, readTimeout, maxRetries, healthPath, defaultMaxHops,
                knowledgePointService, relationService, "");
    }

    @Autowired
    public KnowledgeGraphServiceImpl(
            @Value("${knowledge-graph.enabled:false}") boolean enabled,
            @Value("${knowledge-graph.base-url:http://localhost:8001}") String baseUrl,
            @Value("${knowledge-graph.connect-timeout:5}") int connectTimeout,
            @Value("${knowledge-graph.read-timeout:30}") int readTimeout,
            @Value("${knowledge-graph.max-retries:2}") int maxRetries,
            @Value("${knowledge-graph.health-path:/api/v1/graph/health}") String healthPath,
            @Value("${knowledge-graph.rag.max-hops:2}") int defaultMaxHops,
            KnowledgePointService knowledgePointService,
            KnowledgePointRelationService relationService,
            @Value("${app.internal.service-api-key:}") String internalApiKey) {
        this.knowledgePointService = knowledgePointService;
        this.relationService = relationService;
        KnowledgeGraphContract local = new LocalKnowledgeGraphProvider(knowledgePointService, relationService, defaultMaxHops);
        this.provider = enabled
                ? new HttpKnowledgeGraphProvider(baseUrl, connectTimeout, readTimeout, maxRetries, healthPath, defaultMaxHops, local, internalApiKey)
                : local;
    }

    @Override
    public boolean healthCheck() {
        return provider.healthCheck().healthy();
    }

    @Override
    public KnowledgePathPlanVO planLearningPath(Long sourceId, Long targetId, int maxHops) {
        PathPlanResult r = provider.planLearningPath(new PathPlanRequest(sourceId, targetId, maxHops));
        return toVo(r);
    }

    @Override
    public String ingest(Long courseId) {
        return serialize(provider.ingest(buildSnapshot(courseId)));
    }

    /**
     * 从 MySQL 生成该课程的全量快照（知识点 + 知识点关系）。
     * <p>摄取语义是「MySQL 为准的幂等对齐」而不是「让引擎自己数一遍」：只有把权威快照推过去，
     * 引擎才能补齐缺失节点、修正过期属性并清掉已删除知识点，两侧数量才对得上。</p>
     */
    private KnowledgeGraphContract.IngestRequest buildSnapshot(Long courseId) {
        List<KnowledgeGraphContract.IngestNode> nodes = new ArrayList<>();
        List<KnowledgeGraphContract.IngestRelation> relations = new ArrayList<>();
        if (courseId != null) {
            List<KnowledgePoint> points = knowledgePointService.listByCourse(courseId);
            if (points != null) {
                for (KnowledgePoint point : points) {
                    nodes.add(new KnowledgeGraphContract.IngestNode(point.getId(), point.getCourseId(),
                            point.getName(), point.getDescription(), point.getPointType()));
                }
            }
            List<KnowledgePointRelation> edges = relationService.listByCourse(courseId);
            if (edges != null) {
                for (KnowledgePointRelation edge : edges) {
                    relations.add(new KnowledgeGraphContract.IngestRelation(edge.getId(), edge.getSourceId(),
                            edge.getTargetId(), edge.getRelationType()));
                }
            }
        }
        return new KnowledgeGraphContract.IngestRequest(courseId, nodes, relations);
    }

    @Override
    public String getSubGraph(Long courseId, int maxHops) {
        return serialize(provider.getSubGraph(courseId, maxHops));
    }

    /**
     * 将契约 {@link PathPlanResult}（含节点ID与边）映射为控制器使用的 {@link KnowledgePathPlanVO}
     * （含 {@link KnowledgePoint} 实体与 {@link KnowledgePointRelation} 边）。
     */
    private KnowledgePathPlanVO toVo(PathPlanResult r) {
        KnowledgePathPlanVO vo = new KnowledgePathPlanVO();
        vo.setSourceId(r.sourceId());
        vo.setTargetId(r.targetId());
        vo.setHops(r.hops());
        vo.setFound(r.found());
        vo.setMode(r.mode() == null ? null : r.mode().name());
        vo.setNote(r.note());

        List<Long> ids = r.path() == null ? List.of() : r.path();
        List<KnowledgePoint> nodes = new ArrayList<>();
        if (!ids.isEmpty()) {
            List<KnowledgePoint> found = knowledgePointService.listByIds(ids);
            Map<Long, KnowledgePoint> map = new HashMap<>();
            if (found != null) {
                for (KnowledgePoint kp : found) {
                    map.put(kp.getId(), kp);
                }
            }
            for (Long id : ids) {
                nodes.add(map.get(id)); // 缺失节点以 null 占位，前端忽略
            }
        }
        vo.setPath(nodes);

        List<KnowledgePointRelation> edges = new ArrayList<>();
        if (r.edges() != null) {
            for (PathEdge e : r.edges()) {
                KnowledgePointRelation rel = new KnowledgePointRelation();
                rel.setSourceId(e.sourceId());
                rel.setTargetId(e.targetId());
                rel.setRelationType(e.relationType());
                edges.add(rel);
            }
        }
        vo.setEdges(edges);
        return vo;
    }

    private String serialize(Object obj) {
        try {
            return OM.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("知识图谱结果序列化失败：{}", e.getMessage());
            return "{\"code\":" + ContractPaths.CODE_UNAVAILABLE + "}";
        }
    }
}
