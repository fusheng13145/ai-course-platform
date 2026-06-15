package com.aicourse.service.impl;

import com.aicourse.service.RAGService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * RAG服务实现类
 * 通过HTTP调用Python RAG微服务
 */
@Service
public class RAGServiceImpl implements RAGService {

    private static final Logger log = LoggerFactory.getLogger(RAGServiceImpl.class);
    private static final String SERVICE_UNAVAILABLE = "{\"error\":\"RAG服务暂不可用\",\"code\":503}";

    private final RestClient restClient;
    private final boolean enabled;

    public RAGServiceImpl(
            @Value("${rag.enabled:true}") boolean enabled,
            @Value("${rag.base-url:http://localhost:8000}") String baseUrl) {
        this.enabled = enabled;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String ask(String question, Long courseId, int topK) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        try {
            return restClient.post()
                    .uri("/api/v1/rag/ask")
                    .body(Map.of(
                            "question", question,
                            "course_id", courseId,
                            "top_k", topK
                    ))
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("RAG问答服务调用失败", e);
            return SERVICE_UNAVAILABLE;
        }
    }

    @Override
    public String addDocument(Long courseId, String documentName, String content) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        try {
            return restClient.post()
                    .uri("/api/v1/rag/documents")
                    .body(Map.of(
                            "course_id", courseId,
                            "document_name", documentName,
                            "content", content
                    ))
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("添加文档失败", e);
            return SERVICE_UNAVAILABLE;
        }
    }

    @Override
    public String deleteDocument(Long documentId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        try {
            return restClient.delete()
                    .uri("/api/v1/rag/documents/{documentId}", documentId)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("删除文档失败", e);
            return SERVICE_UNAVAILABLE;
        }
    }

    @Override
    public String listDocuments(Long courseId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        try {
            return restClient.get()
                    .uri("/api/v1/rag/documents?course_id={courseId}", courseId)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("获取文档列表失败", e);
            return SERVICE_UNAVAILABLE;
        }
    }

    @Override
    public String updateDocument(Long documentId, String content) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        try {
            return restClient.put()
                    .uri("/api/v1/rag/documents/{documentId}", documentId)
                    .body(Map.of("content", content))
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("更新文档失败", e);
            return SERVICE_UNAVAILABLE;
        }
    }

    @Override
    public String buildIndex(Long courseId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        try {
            return restClient.post()
                    .uri("/api/v1/rag/index/build/{courseId}", courseId)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("构建索引失败", e);
            return SERVICE_UNAVAILABLE;
        }
    }

    @Override
    public String clearIndex(Long courseId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        try {
            return restClient.delete()
                    .uri("/api/v1/rag/index/{courseId}", courseId)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("清理索引失败", e);
            return SERVICE_UNAVAILABLE;
        }
    }

    @Override
    public String retrieve(String query, Long courseId, int topK) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        try {
            return restClient.post()
                    .uri("/api/v1/rag/retrieve")
                    .body(Map.of(
                            "query", query,
                            "course_id", courseId,
                            "top_k", topK
                    ))
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("文档检索失败", e);
            return SERVICE_UNAVAILABLE;
        }
    }
}