package com.aicourse.service.impl;

import com.aicourse.service.KnowledgeGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 知识图谱服务实现类
 * 通过HTTP调用Python知识图谱微服务
 */
@Service
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphServiceImpl.class);
    private static final String SERVICE_UNAVAILABLE = "{\"error\":\"知识图谱服务暂不可用\",\"code\":503}";

    private final RestClient restClient;
    private final boolean enabled;

    public KnowledgeGraphServiceImpl(
            @Value("${knowledge-graph.enabled:true}") boolean enabled,
            @Value("${knowledge-graph.base-url:http://localhost:8000}") String baseUrl) {
        this.enabled = enabled;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String getGraph(Long courseId, Long nodeId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        try {
            String uri = nodeId != null 
                ? "/api/v1/graph/nodes/" + nodeId 
                : "/api/v1/graph/course/" + courseId;
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("调用知识图谱服务失败", e);
            return SERVICE_UNAVAILABLE;
        }
    }

    @Override
    public String getNodeRelations(Long nodeId, int depth) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        try {
            return restClient.get()
                    .uri("/api/v1/graph/nodes/{nodeId}/relations?depth={depth}", nodeId, depth)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("获取节点关系失败", e);
            return SERVICE_UNAVAILABLE;
        }
    }

    @Override
    public String addNode(Long courseId, String name, String content, String type) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        try {
            return restClient.post()
                    .uri("/api/v1/graph/nodes")
                    .body(Map.of(
                            "course_id", courseId,
                            "name", name,
                            "content", content,
                            "type", type
                    ))
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("添加知识节点失败", e);
            return SERVICE_UNAVAILABLE;
        }
    }

    @Override
    public String addRelation(Long sourceId, Long targetId, String relationType) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        try {
            return restClient.post()
                    .uri("/api/v1/graph/relations")
                    .body(Map.of(
                            "source_id", sourceId,
                            "target_id", targetId,
                            "relation_type", relationType
                    ))
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("添加节点关系失败", e);
            return SERVICE_UNAVAILABLE;
        }
    }

    @Override
    public String deleteNode(Long nodeId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        try {
            return restClient.delete()
                    .uri("/api/v1/graph/nodes/{nodeId}", nodeId)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("删除知识节点失败", e);
            return SERVICE_UNAVAILABLE;
        }
    }

    @Override
    public String deleteRelation(Long relationId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        try {
            return restClient.delete()
                    .uri("/api/v1/graph/relations/{relationId}", relationId)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("删除节点关系失败", e);
            return SERVICE_UNAVAILABLE;
        }
    }
}