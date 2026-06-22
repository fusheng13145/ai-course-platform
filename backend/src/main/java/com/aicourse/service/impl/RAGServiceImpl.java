package com.aicourse.service.impl;

import com.aicourse.service.RAGService;
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
 * RAG服务实现类
 * 通过HTTP调用Python RAG微服务（端口 8002）
 * <p>
 * Python端需实现的API契约见 docs/Python接口契约-RAG.md
 */
@Service
public class RAGServiceImpl implements RAGService {

    private static final Logger log = LoggerFactory.getLogger(RAGServiceImpl.class);
    private static final String SERVICE_UNAVAILABLE = "{\"error\":\"RAG服务暂不可用\",\"code\":503}";

    private final RestClient restClient;
    private final boolean enabled;
    private final int maxRetries;
    private final int defaultTopK;
    private final String healthPath;

    public RAGServiceImpl(
            @Value("${rag.enabled:true}") boolean enabled,
            @Value("${rag.base-url:http://localhost:8002}") String baseUrl,
            @Value("${rag.connect-timeout:5}") int connectTimeout,
            @Value("${rag.read-timeout:60}") int readTimeout,
            @Value("${rag.max-retries:2}") int maxRetries,
            @Value("${rag.default-top-k:5}") int defaultTopK,
            @Value("${rag.health-path:/api/v1/rag/health}") String healthPath) {
        this.enabled = enabled;
        this.maxRetries = maxRetries;
        this.defaultTopK = defaultTopK;
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
            log.debug("RAG服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String ask(String question, Long courseId, int topK) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        int k = topK > 0 ? topK : defaultTopK;
        return executeWithRetry(() ->
                restClient.post()
                        .uri("/api/v1/rag/ask")
                        .body(Map.of("question", question, "course_id", courseId, "top_k", k))
                        .retrieve().body(String.class), "RAG问答");
    }

    @Override
    public String addDocument(Long courseId, String documentName, String content) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.post()
                        .uri("/api/v1/rag/documents")
                        .body(Map.of("course_id", courseId, "document_name", documentName, "content", content))
                        .retrieve().body(String.class), "添加文档");
    }

    @Override
    public String deleteDocument(Long documentId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.delete().uri("/api/v1/rag/documents/{documentId}", documentId).retrieve().body(String.class),
                "删除文档");
    }

    @Override
    public String listDocuments(Long courseId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.get().uri("/api/v1/rag/documents?course_id={courseId}", courseId).retrieve().body(String.class),
                "获取文档列表");
    }

    @Override
    public String updateDocument(Long documentId, String content) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.put()
                        .uri("/api/v1/rag/documents/{documentId}", documentId)
                        .body(Map.of("content", content))
                        .retrieve().body(String.class), "更新文档");
    }

    @Override
    public String buildIndex(Long courseId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.post()
                        .uri("/api/v1/rag/index/build/{courseId}", courseId)
                        .retrieve().body(String.class), "构建索引");
    }

    @Override
    public String clearIndex(Long courseId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.delete().uri("/api/v1/rag/index/{courseId}", courseId).retrieve().body(String.class),
                "清理索引");
    }

    @Override
    public String retrieve(String query, Long courseId, int topK) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        int k = topK > 0 ? topK : defaultTopK;
        return executeWithRetry(() ->
                restClient.post()
                        .uri("/api/v1/rag/retrieve")
                        .body(Map.of("query", query, "course_id", courseId, "top_k", k))
                        .retrieve().body(String.class), "文档检索");
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