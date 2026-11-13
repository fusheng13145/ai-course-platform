package com.aicourse.integration.kg;

import com.aicourse.integration.ContractPaths;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱<b>HTTP 远端实现</b>（可选，经 {@code knowledge-graph.enabled=true} 启用）。
 * <p>
 * 按 {@link ContractPaths} 调用 Python 知识图谱微服务（8001）的 {@code /api/v1/graph/*} 契约，
 * 复用 Sentinel 资源 {@code kg:http}（方法级流控 + 慢调用熔断）与带重试机制（超时不重试）。
 * 降级策略：
 * <ul>
 *   <li>{@link #planLearningPath} 引擎未找到路径或不可达时，回退 {@code localFallback}（本地 BFS），保证端点始终返回真实路径；</li>
 *   <li>{@link #ingest} / {@link #getSubGraph} 不可达时返回 {@code 503} 降级占位（与运维手册 §11.7 一致）。</li>
 * </ul>
 * 本类不实现图谱引擎逻辑，仅作为本端与 Python 端的对接适配层。
 * </p>
 */
@Slf4j
public class HttpKnowledgeGraphProvider implements KnowledgeGraphContract {

    private static final ObjectMapper OM = new ObjectMapper();

    private static final String KG_RESOURCE = "kg:http";

    private final RestClient restClient;
    private final int maxRetries;
    private final String healthPath;
    private final int defaultMaxHops;
    private final KnowledgeGraphContract localFallback;

    /** 服务间内部认证头（2026-11-07 验收整改批）：与 Python 端约定常量保持逐字一致 */
    public static final String INTERNAL_KEY_HEADER = "X-Internal-Key";

    /**
     * 兼容旧签名（internalApiKey 为空：不附带内部认证头），供既有单测直构使用。
     */
    public HttpKnowledgeGraphProvider(String baseUrl, int connectTimeout, int readTimeout,
                                      int maxRetries, String healthPath, int defaultMaxHops,
                                      KnowledgeGraphContract localFallback) {
        this(baseUrl, connectTimeout, readTimeout, maxRetries, healthPath, defaultMaxHops, localFallback, "");
    }

    public HttpKnowledgeGraphProvider(String baseUrl, int connectTimeout, int readTimeout,
                                      int maxRetries, String healthPath, int defaultMaxHops,
                                      KnowledgeGraphContract localFallback, String internalApiKey) {
        this.maxRetries = maxRetries;
        this.healthPath = healthPath;
        this.defaultMaxHops = Math.max(defaultMaxHops, 1);
        this.localFallback = localFallback;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));
        var builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json");
        // 服务间内部认证（可选）：internalApiKey 非空时对全部请求附带 X-Internal-Key，
        // Python 端在 INTERNAL_API_KEY 配置一致时校验；为空时不发送（本机裸跑保持直连）。
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            builder = builder.defaultHeader(INTERNAL_KEY_HEADER, internalApiKey);
        }
        this.restClient = builder.build();
    }

    @Override
    public HealthStatus healthCheck() {
        try {
            restClient.get().uri(healthPath).retrieve().toBodilessEntity();
            return new HealthStatus(true);
        } catch (Exception e) {
            log.debug("知识图谱服务健康检查失败: {}", e.getMessage());
            return new HealthStatus(false);
        }
    }

    @Override
    public PathPlanResult planLearningPath(PathPlanRequest request) {
        int hops = request.maxHops() > 0 ? request.maxHops() : defaultMaxHops;
        try (Entry entry = SphU.entry(KG_RESOURCE, EntryType.OUT)) {
            Exception lastException = null;
            for (int i = 0; i <= maxRetries; i++) {
                try {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put(ContractPaths.F_SOURCE_ID, request.sourceId());
                    body.put(ContractPaths.F_TARGET_ID, request.targetId());
                    body.put(ContractPaths.F_MAX_HOPS, hops);
                    String raw = restClient.post().uri(ContractPaths.KG_PATH_PLAN)
                            .body(body).retrieve().body(String.class);
                    PathPlanResult parsed = parseEnginePlan(raw, request.sourceId(), request.targetId());
                    if (parsed != null && parsed.found()) {
                        return parsed;
                    }
                    // 引擎可达但未找到路径：交由本地 BFS 兜底
                    return localFallback.planLearningPath(request);
                } catch (Exception e) {
                    lastException = e;
                    if (isReadTimeout(e)) {
                        // 读超时（含经 RestClient 包装的情况）不重试：直接降级到本地 BFS
                        break;
                    }
                    if (i < maxRetries) {
                        log.warn("图谱引擎路径规划失败，第{}次重试: {}", i + 1, e.getMessage());
                        sleep(i + 1);
                    }
                }
            }
            Tracer.trace(lastException);
        } catch (BlockException e) {
            log.warn("[Sentinel] 图谱下游(resource={}) 被限流/熔断，路径规划回退本地 BFS", KG_RESOURCE);
        }
        return localFallback.planLearningPath(request);
    }

    @Override
    public IngestResult ingest(IngestRequest request) {
        return doExecute(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(ContractPaths.F_COURSE_ID, request.courseId());
            body.put("nodes", toNodePayload(request.nodes()));
            body.put("relations", toRelationPayload(request.relations()));
            String raw = restClient.post().uri(ContractPaths.KG_INGEST)
                    .body(body).retrieve().body(String.class);
            return parseIngest(raw, request.courseId());
        }, "图谱摄取", () -> unavailableIngest(request.courseId()));
    }

    /** 知识点快照转 Python 契约（snake_case）：字段名在跨语言边界必须逐字一致。 */
    private List<Map<String, Object>> toNodePayload(List<IngestNode> nodes) {
        List<Map<String, Object>> payload = new ArrayList<>();
        if (nodes == null) {
            return payload;
        }
        for (IngestNode node : nodes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", node.id());
            item.put("course_id", node.courseId());
            item.put("name", node.name());
            item.put("content", node.content());
            item.put("type", node.type());
            payload.add(item);
        }
        return payload;
    }

    /** 知识点关系快照转 Python 契约（snake_case）。 */
    private List<Map<String, Object>> toRelationPayload(List<IngestRelation> relations) {
        List<Map<String, Object>> payload = new ArrayList<>();
        if (relations == null) {
            return payload;
        }
        for (IngestRelation relation : relations) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", relation.id());
            item.put("source_id", relation.sourceId());
            item.put("target_id", relation.targetId());
            item.put("relation_type", relation.relationType());
            payload.add(item);
        }
        return payload;
    }

    @Override
    public SubGraphResult getSubGraph(Long courseId, int maxHops) {
        int hops = maxHops > 0 ? maxHops : defaultMaxHops;
        return doExecute(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(ContractPaths.F_COURSE_ID, courseId);
            body.put(ContractPaths.F_MAX_HOPS, hops);
            String raw = restClient.post().uri(ContractPaths.KG_SUBGRAPH)
                    .body(body).retrieve().body(String.class);
            return parseSubGraph(raw, courseId, hops);
        }, "获取子图", () -> unavailableSubGraph(courseId, hops));
    }

    // ===================== 解析 =====================

    private PathPlanResult parseEnginePlan(String raw, Long sourceId, Long targetId) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OM.readTree(raw);
            JsonNode pathNode = root.has(ContractPaths.R_PATH) ? root.get(ContractPaths.R_PATH)
                    : (root.has("data") && root.get("data").has(ContractPaths.R_PATH)
                    ? root.get("data").get(ContractPaths.R_PATH) : null);
            if (pathNode == null || !pathNode.isArray() || pathNode.isEmpty()) {
                return null;
            }
            List<Long> ids = new ArrayList<>();
            for (JsonNode n : pathNode) {
                ids.add(n.asLong());
            }
            List<PathEdge> edges = new ArrayList<>();
            JsonNode edgesNode = root.has(ContractPaths.R_EDGES) ? root.get(ContractPaths.R_EDGES) : null;
            if (edgesNode != null && edgesNode.isArray()) {
                for (JsonNode e : edgesNode) {
                    edges.add(new PathEdge(
                            e.has(ContractPaths.F_SOURCE_ID) ? e.get(ContractPaths.F_SOURCE_ID).asLong() : null,
                            e.has(ContractPaths.F_TARGET_ID) ? e.get(ContractPaths.F_TARGET_ID).asLong() : null,
                            e.has(ContractPaths.F_RELATION_TYPE) ? e.get(ContractPaths.F_RELATION_TYPE).asText() : null));
                }
            }
            int hops = root.has(ContractPaths.R_HOPS) ? root.get(ContractPaths.R_HOPS).asInt() : Math.max(ids.size() - 1, 0);
            return new PathPlanResult(sourceId, targetId, ids, edges, hops, true, Mode.ENGINE,
                    "由知识图谱引擎（8001）计算返回");
        } catch (Exception e) {
            log.debug("图谱引擎返回解析失败：{}", e.getMessage());
            return null;
        }
    }

    private IngestResult parseIngest(String raw, Long courseId) {
        if (raw == null || raw.isBlank()) {
            return unavailableIngest(courseId);
        }
        try {
            JsonNode root = OM.readTree(raw);
            String status = root.has(ContractPaths.R_STATUS) ? root.get(ContractPaths.R_STATUS).asText() : null;
            long synced = root.has("synced_nodes") ? root.get("synced_nodes").asLong()
                    : (root.has("syncedPoints") ? root.get("syncedPoints").asLong() : 0);
            long relations = root.has("upserted_relations") ? root.get("upserted_relations").asLong() : 0;
            return new IngestResult(200, status, ContractPaths.MODE_ENGINE, courseId, synced, relations,
                    "由知识图谱引擎（8001）摄取返回");
        } catch (Exception e) {
            log.debug("图谱摄取返回解析失败：{}", e.getMessage());
            return unavailableIngest(courseId);
        }
    }

    private SubGraphResult parseSubGraph(String raw, Long courseId, int maxHops) {
        if (raw == null || raw.isBlank()) {
            return unavailableSubGraph(courseId, maxHops);
        }
        try {
            JsonNode root = OM.readTree(raw);
            String status = root.has(ContractPaths.R_STATUS) ? root.get(ContractPaths.R_STATUS).asText() : null;
            List<Object> nodes = new ArrayList<>();
            if (root.has("nodes") && root.get("nodes").isArray()) {
                for (JsonNode n : root.get("nodes")) {
                    nodes.add(OM.treeToValue(n, Object.class));
                }
            }
            List<Object> edges = new ArrayList<>();
            if (root.has("edges") && root.get("edges").isArray()) {
                for (JsonNode e : root.get("edges")) {
                    edges.add(OM.treeToValue(e, Object.class));
                }
            }
            return new SubGraphResult(200, status, ContractPaths.MODE_ENGINE, courseId, maxHops,
                    nodes, edges, "由知识图谱引擎（8001）子图返回");
        } catch (Exception e) {
            log.debug("图谱子图返回解析失败：{}", e.getMessage());
            return unavailableSubGraph(courseId, maxHops);
        }
    }

    private IngestResult unavailableIngest(Long courseId) {
        return new IngestResult(ContractPaths.CODE_UNAVAILABLE, null, ContractPaths.MODE_UNAVAILABLE,
                courseId, 0, 0, ContractPaths.MSG_KG_UNAVAILABLE);
    }

    private SubGraphResult unavailableSubGraph(Long courseId, int maxHops) {
        return new SubGraphResult(ContractPaths.CODE_UNAVAILABLE, null, ContractPaths.MODE_UNAVAILABLE,
                courseId, maxHops, new ArrayList<>(), new ArrayList<>(), ContractPaths.MSG_KG_UNAVAILABLE);
    }

    // ===================== 重试执行器 =====================

    @FunctionalInterface
    private interface HttpCall<T> {
        T execute() throws Exception;
    }

    private <T> T doExecute(HttpCall<T> call, String operation, HttpCall<T> fallback) {
        Exception lastException = null;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                return call.execute();
            } catch (Exception e) {
                lastException = e;
                if (isReadTimeout(e)) {
                    // 读超时（含经 RestClient 包装的情况）不重试：直接降级
                    break;
                }
                if (i < maxRetries) {
                    log.warn("{}失败，第{}次重试: {}", operation, i + 1, e.getMessage());
                    sleep(i + 1);
                }
            }
        }
        log.error("{}多次失败，已达最大重试次数", operation, lastException);
        Tracer.trace(lastException);
        try {
            return fallback.execute();
        } catch (Exception ex) {
            return null;
        }
    }

    private void sleep(int attempt) {
        try {
            Thread.sleep(1000L * attempt);
        } catch (InterruptedException ie) {
            log.warn("重试等待被中断", ie);
            Thread.currentThread().interrupt();
        }
    }

    /** 是否为读超时：RestClient 会将底层 {@link SocketTimeoutException} 包装为 ResourceAccessException。 */
    private static boolean isReadTimeout(Exception e) {
        return e instanceof SocketTimeoutException
                || (e.getCause() instanceof SocketTimeoutException);
    }
}
