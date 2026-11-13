package com.aicourse.integration.rag;

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
import java.util.function.Supplier;

/**
 * RAG <b>HTTP 远端实现</b>（可选，经 {@code rag.enabled=true} 启用）。
 * <p>
 * 按 {@link ContractPaths} 调用 Python RAG 微服务（8002）的 {@code /api/v1/rag/*} 契约，
 * 复用 Sentinel 资源 {@code rag:http}（方法级流控 + 慢调用熔断）与带重试机制（超时不重试）。
 * 服务未启用/不可达时，由 {@link DisabledRagProvider} 返回 {@code 503} 降级占位，与
 * {@code AIServiceImpl.ragAsk}「检测到 code:503 即回退纯 LLM」约定一致。
 * 本类不实现 RAG 引擎逻辑，仅作为本端与 Python 端的对接适配层。
 * </p>
 */
@Slf4j
public class HttpRagProvider implements RagContract {

    private static final ObjectMapper OM = new ObjectMapper();

    private static final String RAG_RESOURCE = "rag:http";

    private final RestClient restClient;
    private final int maxRetries;
    private final String healthPath;
    private final int defaultTopK;

    /** 服务间内部认证头（2026-11-07 验收整改批）：与 Python 端约定常量保持逐字一致 */
    public static final String INTERNAL_KEY_HEADER = "X-Internal-Key";

    /**
     * 兼容旧签名（internalApiKey 为空：不附带内部认证头），供既有单测直构使用。
     */
    public HttpRagProvider(String baseUrl, int connectTimeout, int readTimeout,
                           int maxRetries, String healthPath, int defaultTopK) {
        this(baseUrl, connectTimeout, readTimeout, maxRetries, healthPath, defaultTopK, "");
    }

    public HttpRagProvider(String baseUrl, int connectTimeout, int readTimeout,
                           int maxRetries, String healthPath, int defaultTopK, String internalApiKey) {
        this.maxRetries = maxRetries;
        this.healthPath = healthPath;
        this.defaultTopK = defaultTopK;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));
        var builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json");
        // 服务间内部认证（可选）：internalApiKey 非空时对全部请求（含健康检查）附带 X-Internal-Key，
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
            log.debug("RAG 服务健康检查失败: {}", e.getMessage());
            return new HealthStatus(false);
        }
    }

    @Override
    public RagQueryResult ask(RagAskRequest request) {
        return executeWithRetry(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(ContractPaths.F_QUESTION, request.question());
            if (request.courseId() != null) {
                body.put(ContractPaths.F_COURSE_ID, request.courseId());
            }
            body.put(ContractPaths.F_TOP_K, request.topK() > 0 ? request.topK() : defaultTopK);
            String raw = restClient.post().uri(ContractPaths.RAG_ASK)
                    .body(body).retrieve().body(String.class);
            return parseAsk(raw);
        }, "RAG 问答", () -> unavailableQuery());
    }

    @Override
    public RagDocument addDocument(Long courseId, String documentName, String content) {
        return executeWithRetry(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(ContractPaths.F_COURSE_ID, courseId);
            body.put(ContractPaths.F_DOCUMENT_NAME, documentName);
            body.put(ContractPaths.F_CONTENT, content);
            String raw = restClient.post().uri(ContractPaths.RAG_DOCUMENTS)
                    .body(body).retrieve().body(String.class);
            return parseDocument(raw);
        }, "添加知识库文档", () -> unavailableDocument());
    }

    @Override
    public RagDocument deleteDocument(Long documentId) {
        return executeWithRetry(() -> restClient.delete()
                        .uri(ContractPaths.RAG_DOCUMENTS + "/{documentId}", documentId)
                        .retrieve().body(String.class) != null
                        ? new RagDocument(documentId, null, null, null, ContractPaths.MODE_ENGINE, null, null)
                        : unavailableDocument(),
                "删除知识库文档", () -> unavailableDocument());
    }

    @Override
    public RagDocumentList listDocuments(Long courseId) {
        return executeWithRetry(() -> {
            String uri = courseId != null
                    ? ContractPaths.RAG_DOCUMENTS + "?course_id=" + courseId
                    : ContractPaths.RAG_DOCUMENTS;
            String raw = restClient.get().uri(uri).retrieve().body(String.class);
            return parseDocumentList(raw);
        }, "查询知识库文档列表", () -> new RagDocumentList(null, ContractPaths.MSG_RAG_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE));
    }

    @Override
    public RagDocument updateDocument(Long documentId, String content) {
        return executeWithRetry(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(ContractPaths.F_CONTENT, content);
            String raw = restClient.put()
                    .uri(ContractPaths.RAG_DOCUMENTS + "/{documentId}", documentId)
                    .body(body).retrieve().body(String.class);
            return parseDocument(raw);
        }, "更新知识库文档", () -> unavailableDocument());
    }

    @Override
    public IndexResult buildIndex(Long courseId) {
        return executeWithRetry(() -> {
            String raw = restClient.post()
                    .uri(ContractPaths.RAG_INDEX_BUILD, courseId)
                    .retrieve().body(String.class);
            return parseIndex(raw);
        }, "构建课程索引", () -> unavailableIndex());
    }

    @Override
    public IndexResult clearIndex(Long courseId) {
        return executeWithRetry(() -> {
            String raw = restClient.delete()
                    .uri(ContractPaths.RAG_INDEX, courseId)
                    .retrieve().body(String.class);
            return parseIndex(raw);
        }, "清理课程索引", () -> unavailableIndex());
    }

    @Override
    public RagRetrievalResult retrieve(RagRetrieveRequest request) {
        return executeWithRetry(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(ContractPaths.F_QUERY, request.query());
            if (request.courseId() != null) {
                body.put(ContractPaths.F_COURSE_ID, request.courseId());
            }
            body.put(ContractPaths.F_TOP_K, request.topK() > 0 ? request.topK() : defaultTopK);
            String raw = restClient.post().uri(ContractPaths.RAG_RETRIEVE)
                    .body(body).retrieve().body(String.class);
            return parseRetrieve(raw);
        }, "RAG 检索", () -> unavailableRetrieve());
    }

    // ===================== 解析 =====================

    private RagQueryResult parseAsk(String raw) {
        if (raw == null || raw.isBlank()) {
            return unavailableQuery();
        }
        try {
            JsonNode root = OM.readTree(raw);
            // 修复 D7（跨语言契约）：Python 侧引擎不可用时返回 HTTP 200 + body{code:503, mode:"UNAVAILABLE", error}，
            // 原实现把 mode 硬编码成 ENGINE、忽略 code/error，导致「引擎不可用」被误判为「检索成功但结果为空」，
            // 上层降级回退（AIServiceImpl / AiRagComponent 靠 code:503 嗅探）永不触发。现真实解析三字段。
            if (isEngineUnavailable(root)) {
                return unavailableQuery();
            }
            String answer = root.has(ContractPaths.R_ANSWER) ? root.get(ContractPaths.R_ANSWER).asText() : null;
            List<RagSource> sources = parseSources(root.get(ContractPaths.R_SOURCES));
            // mode 取自响应体；缺省时按引擎正常态兜底为 ENGINE（而非无条件硬编码）。
            String mode = textOrNull(root, ContractPaths.R_MODE);
            return new RagQueryResult(answer, sources, mode != null ? mode : ContractPaths.MODE_ENGINE, null, null);
        } catch (Exception e) {
            log.debug("RAG 问答返回解析失败：{}", e.getMessage());
            return unavailableQuery();
        }
    }

    private RagRetrievalResult parseRetrieve(String raw) {
        if (raw == null || raw.isBlank()) {
            return unavailableRetrieve();
        }
        try {
            JsonNode root = OM.readTree(raw);
            // 修复 D7：同 parseAsk，检索路径同样须识别 body 的 503/UNAVAILABLE 降级语义，
            // 否则「引擎不可用」被当作「检索成功但结果为空」，上层降级永不触发。
            if (isEngineUnavailable(root)) {
                return unavailableRetrieve();
            }
            List<RagSource> sources = parseSources(root.get(ContractPaths.R_RESULTS));
            // mode 与 ask 同口径取自响应体：缺省时按引擎正常态兜底，PLACEHOLDER/UNAVAILABLE 原样透传，
            // 由上层（AiRagComponent）判定「是否真的来自引擎」。
            String mode = textOrNull(root, ContractPaths.R_MODE);
            return new RagRetrievalResult(sources, mode != null ? mode : ContractPaths.MODE_ENGINE, null, null);
        } catch (Exception e) {
            log.debug("RAG 检索返回解析失败：{}", e.getMessage());
            return unavailableRetrieve();
        }
    }

    /**
     * 判断 Python RAG 响应体是否声明「引擎不可用」：body.code==503 或 body.mode==UNAVAILABLE。
     * 复用 {@link ContractPaths} 的常量，不引入字符串字面量。
     */
    private boolean isEngineUnavailable(JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }
        if (root.has(ContractPaths.R_CODE)
                && root.get(ContractPaths.R_CODE).asInt() == ContractPaths.CODE_UNAVAILABLE) {
            return true;
        }
        return ContractPaths.MODE_UNAVAILABLE.equals(textOrNull(root, ContractPaths.R_MODE));
    }

    /** 读取字符串字段，缺失或为 null 时返回 null。 */
    private static String textOrNull(JsonNode root, String field) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return null;
        }
        return root.get(field).asText();
    }

    private List<RagSource> parseSources(JsonNode node) {
        List<RagSource> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode s : node) {
                Long docId = null;
                if (s.has(ContractPaths.F_DOCUMENT_ID)) {
                    docId = s.get(ContractPaths.F_DOCUMENT_ID).asLong();
                }
                String docName = null;
                if (s.has(ContractPaths.F_DOCUMENT_NAME)) {
                    docName = s.get(ContractPaths.F_DOCUMENT_NAME).asText();
                }
                Double relevance = null;
                if (s.has("relevance_score")) {
                    relevance = s.get("relevance_score").asDouble();
                }
                String snippet = null;
                if (s.has("snippet")) {
                    snippet = s.get("snippet").asText();
                }
                list.add(new RagSource(docId, docName, relevance, snippet));
            }
        }
        return list;
    }

    private RagDocument parseDocument(String raw) {
        if (raw == null || raw.isBlank()) {
            return unavailableDocument();
        }
        try {
            JsonNode root = OM.readTree(raw);
            Long id = null;
            if (root.has("id")) {
                id = root.get("id").asLong();
            } else if (root.has(ContractPaths.F_DOCUMENT_ID)) {
                id = root.get(ContractPaths.F_DOCUMENT_ID).asLong();
            }
            String name = null;
            if (root.has(ContractPaths.F_DOCUMENT_NAME)) {
                name = root.get(ContractPaths.F_DOCUMENT_NAME).asText();
            } else if (root.has("name")) {
                name = root.get("name").asText();
            }
            Long cid = null;
            if (root.has(ContractPaths.F_COURSE_ID)) {
                cid = root.get(ContractPaths.F_COURSE_ID).asLong();
            }
            String content = null;
            if (root.has(ContractPaths.F_CONTENT)) {
                content = root.get(ContractPaths.F_CONTENT).asText();
            }
            return new RagDocument(id, name, cid, content, ContractPaths.MODE_ENGINE, null, null);
        } catch (Exception e) {
            log.debug("RAG 文档返回解析失败：{}", e.getMessage());
            return unavailableDocument();
        }
    }

    private RagDocumentList parseDocumentList(String raw) {
        if (raw == null || raw.isBlank()) {
            return new RagDocumentList(null, ContractPaths.MSG_RAG_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE);
        }
        try {
            JsonNode root = OM.readTree(raw);
            List<RagDocument> docs = new ArrayList<>();
            JsonNode arr = root.isArray() ? root : (root.has("documents") ? root.get("documents") : null);
            if (arr != null && arr.isArray()) {
                for (JsonNode d : arr) {
                    docs.add(parseDocument(d.toString()));
                }
            }
            return new RagDocumentList(docs, null, null);
        } catch (Exception e) {
            log.debug("RAG 文档列表返回解析失败：{}", e.getMessage());
            return new RagDocumentList(null, ContractPaths.MSG_RAG_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE);
        }
    }

    private IndexResult parseIndex(String raw) {
        if (raw == null || raw.isBlank()) {
            return unavailableIndex();
        }
        try {
            JsonNode root = OM.readTree(raw);
            Boolean success = null;
            if (root.has(ContractPaths.R_SUCCESS)) {
                success = root.get(ContractPaths.R_SUCCESS).asBoolean();
            }
            Integer chunks = null;
            if (root.has("indexed_chunks")) {
                chunks = root.get("indexed_chunks").asInt();
            }
            return new IndexResult(success, chunks, null, null);
        } catch (Exception e) {
            log.debug("RAG 索引返回解析失败：{}", e.getMessage());
            return unavailableIndex();
        }
    }

    private RagQueryResult unavailableQuery() {
        // D7：降级结果同时带 mode=UNAVAILABLE 与 code=503，使 Java 侧「引擎不可用」语义与 Python 侧对齐，
        // 便于上层（AIServiceImpl / AiRagComponent）按 code:503 稳定嗅探并触发纯 LLM 回退。
        return new RagQueryResult(null, null, ContractPaths.MODE_UNAVAILABLE,
                ContractPaths.MSG_RAG_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE);
    }

    private RagDocument unavailableDocument() {
        return new RagDocument(null, null, null, null, null, ContractPaths.MSG_RAG_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE);
    }

    private RagRetrievalResult unavailableRetrieve() {
        return new RagRetrievalResult(null, ContractPaths.MODE_UNAVAILABLE,
                ContractPaths.MSG_RAG_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE);
    }

    private IndexResult unavailableIndex() {
        return new IndexResult(null, null, ContractPaths.MSG_RAG_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE);
    }

    // ===================== 重试执行器 =====================

    @FunctionalInterface
    private interface HttpCall<T> {
        T execute() throws Exception;
    }

    private <T> T executeWithRetry(HttpCall<T> call, String operation, Supplier<T> fallback) {
        try (Entry entry = SphU.entry(RAG_RESOURCE, EntryType.OUT)) {
            Exception lastException = null;
            for (int i = 0; i <= maxRetries; i++) {
                try {
                    return call.execute();
                } catch (Exception e) {
                    lastException = e;
                    if (isReadTimeout(e)) {
                        // 读超时（含经 RestClient 包装为 ResourceAccessException 的情况）不重试：
                        // 直接降级，避免无效等待放大雪崩
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
            return fallback.get();
        } catch (BlockException e) {
            log.warn("[Sentinel] RAG 下游(resource={}) 被限流/熔断，操作={}，降级返回 503", RAG_RESOURCE, operation);
            return fallback.get();
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
