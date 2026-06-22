package com.aicourse.service.impl;

import com.aicourse.service.KnowledgeGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;

/**
 * 知识图谱服务实现类
 * 通过HTTP调用Python知识图谱微服务（端口 8001）
 * <p>
 * Python端需实现的API契约见 docs/Python接口契约-知识图谱.md
 */
@Service
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphServiceImpl.class);
    private static final String SERVICE_UNAVAILABLE = "{\"error\":\"知识图谱服务暂不可用\",\"code\":503}";

    private final RestClient restClient;
    private final boolean enabled;
    private final int maxRetries;
    private final String healthPath;

    public KnowledgeGraphServiceImpl(
            @Value("${knowledge-graph.enabled:true}") boolean enabled,
            @Value("${knowledge-graph.base-url:http://localhost:8001}") String baseUrl,
            @Value("${knowledge-graph.connect-timeout:5}") int connectTimeout,
            @Value("${knowledge-graph.read-timeout:30}") int readTimeout,
            @Value("${knowledge-graph.max-retries:2}") int maxRetries,
            @Value("${knowledge-graph.health-path:/api/v1/graph/health}") String healthPath) {
        this.enabled = enabled;
        this.maxRetries = maxRetries;
        this.healthPath = healthPath;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public boolean healthCheck() {
        if (!enabled) return false;
        try {
            restClient.get().uri(healthPath).retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.debug("知识图谱服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getGraph(Long courseId, Long nodeId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() -> {
            String uri = nodeId != null
                    ? "/api/v1/graph/nodes/" + nodeId
                    : "/api/v1/graph/course/" + courseId;
            return restClient.get().uri(uri).retrieve().body(String.class);
        }, "查询知识图谱");
    }

    @Override
    public String getNodeRelations(Long nodeId, int depth) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.get()
                        .uri("/api/v1/graph/nodes/{nodeId}/relations?depth={depth}", nodeId, depth)
                        .retrieve().body(String.class), "获取节点关系");
    }

    @Override
    public String addNode(Long courseId, String name, String content, String type) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.post()
                        .uri("/api/v1/graph/nodes")
                        .body(Map.of("course_id", courseId, "name", name, "content", content, "type", type))
                        .retrieve().body(String.class), "添加知识节点");
    }

    @Override
    public String addRelation(Long sourceId, Long targetId, String relationType) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.post()
                        .uri("/api/v1/graph/relations")
                        .body(Map.of("source_id", sourceId, "target_id", targetId, "relation_type", relationType))
                        .retrieve().body(String.class), "添加节点关系");
    }

    @Override
    public String deleteNode(Long nodeId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.delete().uri("/api/v1/graph/nodes/{nodeId}", nodeId).retrieve().body(String.class),
                "删除知识节点");
    }

    @Override
    public String deleteRelation(Long relationId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.delete().uri("/api/v1/graph/relations/{relationId}", relationId).retrieve().body(String.class),
                "删除节点关系");
    }

    /**
     * 带重试的HTTP调用执行器
     */
    private String executeWithRetry(HttpCall call, String operation) {
        Exception lastException = null;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                return call.execute();
            } catch (Exception e) {
                lastException = e;
                if (i < maxRetries && !(e instanceof SocketTimeoutException)) {
                    log.warn("{}失败，第{}次重试: {}", operation, i + 1, e.getMessage());
                    try { Thread.sleep(500L * (i + 1)); } catch (InterruptedException ignored) {}
                }
            }
        }
        log.error("{}多次失败，已达最大重试次数", operation, lastException);
        return SERVICE_UNAVAILABLE;
    }

    @FunctionalInterface
    private interface HttpCall {
        String execute() throws Exception;
    }
}