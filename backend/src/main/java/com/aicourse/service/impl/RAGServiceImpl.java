package com.aicourse.service.impl;


import com.aicourse.service.RAGService;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * RAG 服务实现（预留接口 · HTTP 代理到 Python RAG 服务 8002）。
 * <p>
 * 职责边界（2026-08-13 用户明确）：RAG 检索引擎（BM25 / 向量检索 / embedding / 融合）整体由团队另一成员负责，
 * 本端不再内置引擎实现，仅保留 {@link RAGService} 标准化接口与本 HTTP 代理。接入后经
 * {@code rag.enabled=true} + {@code RAG_BASE_URL}（默认 http://localhost:8002）启用，调用 Python 端
 * 实现的 {@code /api/v1/rag/*} 契约（见 docs/运维手册.md §11.4）。
 * <p>
 * 服务未启用或不可达时返回 {@code {"error":...,"code":503}} ——与 {@code AIServiceImpl.ragAsk}
 * 的降级约定一致（检测到 {@code "code":503} 即回退纯 LLM 调用），问答链路不中断。
 */
@Service
@Slf4j
public class RAGServiceImpl implements RAGService {


    private static final String SERVICE_UNAVAILABLE =
            "{\"error\":\"RAG 引擎由团队另一成员负责，本端仅预留接口；服务未接入或不可用\",\"code\":503}";

    /**
     * Sentinel 资源名：汇聚本服务所有对 Python RAG 微服务（8002）的下游 HTTP 调用。
     * 对该资源施加方法级流控 + 熔断降级，避免 RAG 不可达 / 慢响应时请求在重试中堆积，
     * 熔断开启时快速失败并复用既有 SERVICE_UNAVAILABLE(503) 契约 —— 与 AIServiceImpl.ragAsk
     * 「检测到 code:503 即回退纯 LLM」的降级约定完全兼容，问答链路不中断。
     */
    private static final String RAG_RESOURCE = "rag:http";

    private final RestClient restClient;
    private final boolean enabled;
    private final int maxRetries;
    private final String healthPath;
    private final int defaultTopK;

    public RAGServiceImpl(
            @Value("${rag.enabled:false}") boolean enabled,
            @Value("${rag.base-url:http://localhost:8002}") String baseUrl,
            @Value("${rag.connect-timeout:5}") int connectTimeout,
            @Value("${rag.read-timeout:60}") int readTimeout,
            @Value("${rag.max-retries:2}") int maxRetries,
            @Value("${rag.health-path:/api/v1/rag/health}") String healthPath,
            @Value("${rag.default-top-k:5}") int defaultTopK) {
        this.enabled = enabled;
        this.maxRetries = maxRetries;
        this.healthPath = healthPath;
        this.defaultTopK = defaultTopK;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }


    /** 检索增强相关操作 */
    @Override
    public boolean healthCheck() {
        if (!enabled) {
            return false;
        }
        try {
            restClient.get().uri(healthPath).retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.debug("RAG 服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }


    /** 检索增强相关操作 */
    @Override
    public String ask(String question, Long courseId, int topK) {
        if (!enabled) {
            return SERVICE_UNAVAILABLE;
        }
        return executeWithRetry(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("question", question);
            if (courseId != null) {
                body.put("course_id", courseId);
            }
            body.put("top_k", topK > 0 ? topK : defaultTopK);
            return restClient.post().uri("/api/v1/rag/ask")
                    .body(body).retrieve().body(String.class);
        }, "RAG 问答");
    }


    /** 新增检索增强 */
    @Override
    public String addDocument(Long courseId, String documentName, String content) {
        log.info("新增检索增强");

        if (!enabled) {
            return SERVICE_UNAVAILABLE;
        }
        return executeWithRetry(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("course_id", courseId);
            body.put("document_name", documentName);
            body.put("content", content);
            return restClient.post().uri("/api/v1/rag/documents")
                    .body(body).retrieve().body(String.class);
        }, "添加知识库文档");
    }


    /** 删除检索增强 */
    @Override
    public String deleteDocument(Long documentId) {
        log.info("删除检索增强");

        if (!enabled) {
            return SERVICE_UNAVAILABLE;
        }
        return executeWithRetry(() ->
                restClient.delete().uri("/api/v1/rag/documents/{documentId}", documentId)
                        .retrieve().body(String.class), "删除知识库文档");
    }


    /** 查询检索增强 */
    @Override
    public String listDocuments(Long courseId) {
        if (!enabled) {
            return SERVICE_UNAVAILABLE;
        }
        return executeWithRetry(() -> {
            String uri = courseId != null
                    ? "/api/v1/rag/documents?course_id=" + courseId
                    : "/api/v1/rag/documents";
            return restClient.get().uri(uri).retrieve().body(String.class);
        }, "查询知识库文档列表");
    }


    /** 更新检索增强 */
    @Override
    public String updateDocument(Long documentId, String content) {
        log.info("更新检索增强");

        if (!enabled) {
            return SERVICE_UNAVAILABLE;
        }
        return executeWithRetry(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("content", content);
            return restClient.put().uri("/api/v1/rag/documents/{documentId}", documentId)
                    .body(body).retrieve().body(String.class);
        }, "更新知识库文档");
    }


    /** 构建检索增强 */
    @Override
    public String buildIndex(Long courseId) {
        log.info("构建检索增强");

        if (!enabled) {
            return SERVICE_UNAVAILABLE;
        }
        return executeWithRetry(() ->
                restClient.post().uri("/api/v1/rag/index/build/{courseId}", courseId)
                        .retrieve().body(String.class), "构建课程索引");
    }


    /** 清空检索增强 */
    @Override
    public String clearIndex(Long courseId) {
        log.info("清空检索增强");

        if (!enabled) {
            return SERVICE_UNAVAILABLE;
        }
        return executeWithRetry(() ->
                restClient.delete().uri("/api/v1/rag/index/{courseId}", courseId)
                        .retrieve().body(String.class), "清理课程索引");
    }


    /** 检索增强相关操作 */
    @Override
    public String retrieve(String query, Long courseId, int topK) {
        if (!enabled) {
            return SERVICE_UNAVAILABLE;
        }
        return executeWithRetry(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", query);
            if (courseId != null) {
                body.put("course_id", courseId);
            }
            body.put("top_k", topK > 0 ? topK : defaultTopK);
            return restClient.post().uri("/api/v1/rag/retrieve")
                    .body(body).retrieve().body(String.class);
        }, "RAG 检索");
    }

    /**
     * 带重试的 HTTP 调用执行器（与 {@code LLMFineTuneServiceImpl} 同款，超时不重试）。
     */
    private String executeWithRetry(HttpCall call, String operation) {
        try (Entry entry = SphU.entry(RAG_RESOURCE, EntryType.OUT)) {
            return doExecuteWithRetry(call, operation);
        } catch (BlockException e) {
            log.warn("[Sentinel] RAG 下游(resource={}) 被限流/熔断，操作={}，降级返回 503 占位",
                    RAG_RESOURCE, operation);
            return SERVICE_UNAVAILABLE;
        }
    }

    /**
     * 实际带重试执行（在 Sentinel 资源 {@code rag:http} 作用域内运行，RT 被准确统计用于慢调用熔断）。
     */
    private String doExecuteWithRetry(HttpCall call, String operation) {
        Exception lastException = null;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                return call.execute();
            } catch (Exception e) {
                lastException = e;
                if (i < maxRetries && !(e instanceof SocketTimeoutException)) {
                    log.warn("{}失败，第{}次重试: {}", operation, i + 1, e.getMessage());
                    try {
                        Thread.sleep(1000L * (i + 1));
                    } catch (InterruptedException ie) {
                        log.warn("重试等待被中断", ie);
                        Thread.currentThread().interrupt();
                        return SERVICE_UNAVAILABLE;
                    }
                }
            }
        }
        log.error("{}多次失败，已达最大重试次数", operation, lastException);
        // 计入 Sentinel 异常指标，支撑异常比例 / 异常数熔断（异常须在资源作用域内 trace 才生效）
        Tracer.trace(lastException);
        return SERVICE_UNAVAILABLE;
    }

    @FunctionalInterface
    private interface HttpCall {
        String execute() throws Exception;
    }
}
