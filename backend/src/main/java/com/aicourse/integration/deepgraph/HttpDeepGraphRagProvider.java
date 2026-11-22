package com.aicourse.integration.deepgraph;

import com.aicourse.integration.ContractPaths;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * DeepGraph RAG <b>HTTP 远端实现</b>（经 {@code deepgraph-rag.enabled=true} 启用）。
 * <p>
 * 按 {@link ContractPaths} 调用 Python deepgraph_rag 微服务（8501）的
 * {@code /api/v1/deepgraph/*} 契约，复用 Sentinel 资源 {@code deepgraph:http}（流控 + 慢调用熔断）
 * 与带重试机制（读超时/连接拒绝/503 不重试，口径与 {@code HttpRagProvider} 一致——
 * 三者重试只会把问答端到端拖长）。服务不可达时由 fallback 返回 503 降级占位，
 * 上层（{@code CourseEvidenceComponent}）按 {@code engineReady()} 判定后静默忽略本引擎。
 * 本类不实现任何检索逻辑，仅作为本端与已验收 DeepSeek-RAG-Chatbot 的对接适配层。
 * </p>
 */
@Slf4j
public class HttpDeepGraphRagProvider implements DeepGraphRagContract {

    private static final ObjectMapper OM = new ObjectMapper();

    private static final String DEEPGRAPH_RESOURCE = "deepgraph:http";

    /** 服务间内部认证头：与 Python 端约定常量保持逐字一致。 */
    public static final String INTERNAL_KEY_HEADER = "X-Internal-Key";
    /** 请求链路追踪/用户身份透传头；Python 适配器只回显/记录，不做授权判定。 */
    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String USER_HEADER = "X-User-Id";

    private final RestClient restClient;
    private final int maxRetries;
    private final String healthPath;

    /** 兼容签名（internalApiKey 为空：不附带内部认证头）。 */
    public HttpDeepGraphRagProvider(String baseUrl, int connectTimeout, int readTimeout,
                                    int maxRetries, String healthPath) {
        this(baseUrl, connectTimeout, readTimeout, maxRetries, healthPath, "");
    }

    public HttpDeepGraphRagProvider(String baseUrl, int connectTimeout, int readTimeout,
                                    int maxRetries, String healthPath, String internalApiKey) {
        this.maxRetries = maxRetries;
        this.healthPath = healthPath;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));
        var builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json");
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            builder = builder.defaultHeader(INTERNAL_KEY_HEADER, internalApiKey);
        }
        this.restClient = builder.build();
    }

    /**
     * 把 Java 侧 traceId（{@code TraceIdFilter} 写入的 MDC 键）与当前登录身份透传到 8501。
     * 仅用于日志关联/审计，Python 端不据此做课程权限判定；课程 ACL 仍在 Java 控制器完成。
     */
    private void applyIdentityHeaders(HttpHeaders headers) {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            headers.set(TRACE_HEADER, traceId);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getName() != null && !auth.getName().isBlank()
                && !"anonymousUser".equals(auth.getName())) {
            headers.set(USER_HEADER, auth.getName());
        }
    }

    @Override
    public HealthStatus healthCheck() {
        try {
            restClient.get().uri(healthPath).headers(this::applyIdentityHeaders)
                    .retrieve().toBodilessEntity();
            return new HealthStatus(true);
        } catch (Exception e) {
            log.debug("DeepGraph RAG 服务健康检查失败: {}", e.getMessage());
            return new HealthStatus(false);
        }
    }

    @Override
    public DeepGraphRetrievalResult retrieve(DeepGraphRetrieveRequest request) {
        return executeWithRetry(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(ContractPaths.F_QUERY, request.query());
            if (request.courseId() != null) {
                body.put(ContractPaths.F_COURSE_ID, request.courseId());
            }
            body.put(ContractPaths.F_TOP_K, request.topK());
            String raw = restClient.post().uri(ContractPaths.DEEPGRAPH_RETRIEVE)
                    .headers(this::applyIdentityHeaders)
                    .body(body).retrieve().body(String.class);
            return parseRetrieve(raw);
        }, "DeepGraph RAG 检索", () -> unavailableRetrieve());
    }

    @Override
    public DeepGraphIngestResult ingest(Long courseId, String documentName, String content) {
        return executeWithRetry(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(ContractPaths.F_COURSE_ID, courseId);
            body.put(ContractPaths.F_DOCUMENT_NAME, documentName);
            body.put(ContractPaths.F_CONTENT, content);
            String raw = restClient.post().uri(ContractPaths.DEEPGRAPH_INGEST)
                    .headers(this::applyIdentityHeaders)
                    .body(body).retrieve().body(String.class);
            return parseIngest(raw);
        }, "DeepGraph RAG 文档入库", () -> unavailableIngest());
    }

    @Override
    public DeepGraphIngestResult ingestFile(Long courseId, String documentName,
                                            byte[] content, String contentType) {
        return executeWithRetry(() -> {
            String uri = ContractPaths.DEEPGRAPH_INGEST_FILE
                    + "?course_id={courseId}&document_name={documentName}&build_chapter=true";
            MediaType mediaType = (contentType == null || contentType.isBlank())
                    ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
            String raw = restClient.post().uri(uri, courseId, documentName)
                    .headers(this::applyIdentityHeaders)
                    .contentType(mediaType)
                    .body(content).retrieve().body(String.class);
            return parseIngest(raw);
        }, "DeepGraph RAG 二进制文档入库", () -> unavailableIngest());
    }

    @Override
    public DeepGraphDocumentList listDocuments(Long courseId) {
        return executeWithRetry(() -> {
            String uri = courseId != null
                    ? ContractPaths.DEEPGRAPH_DOCUMENTS + "?course_id=" + courseId + "&page=1&page_size=200"
                    : ContractPaths.DEEPGRAPH_DOCUMENTS + "?page=1&page_size=200";
            String raw = restClient.get().uri(uri).headers(this::applyIdentityHeaders)
                    .retrieve().body(String.class);
            return parseDocumentList(raw);
        }, "查询 DeepGraph 文档列表",
                () -> new DeepGraphDocumentList(null, ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE,
                        ContractPaths.CODE_UNAVAILABLE));
    }

    @Override
    public DeepGraphDeleteResult deleteDocument(Long courseId, String documentName) {
        return executeWithRetry(() -> {
            String uri = ContractPaths.DEEPGRAPH_DOCUMENT
                    + (courseId != null ? "?course_id={courseId}" : "");
            String raw = courseId != null
                    ? restClient.delete().uri(uri, documentName, courseId)
                            .headers(this::applyIdentityHeaders)
                            .retrieve().body(String.class)
                    : restClient.delete().uri(uri, documentName)
                            .headers(this::applyIdentityHeaders)
                            .retrieve().body(String.class);
            return parseDeleteResult(documentName, raw);
        }, "删除 DeepGraph 文档",
                () -> new DeepGraphDeleteResult(null, documentName,
                        ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE));
    }

    @Override
    public DeepGraphChapterGraphResult chapterGraph(Long courseId, String documentName,
                                                    String chapterName, String chapterText) {
        return executeWithRetry(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(ContractPaths.F_COURSE_ID, courseId);
            body.put(ContractPaths.F_DOCUMENT_NAME, documentName);
            if (chapterName != null && !chapterName.isBlank()) {
                body.put("chapter_name", chapterName);
            }
            if (chapterText != null && !chapterText.isBlank()) {
                body.put("chapter_text", chapterText);
            }
            String raw = restClient.post().uri(ContractPaths.DEEPGRAPH_CHAPTER_GRAPH)
                    .headers(this::applyIdentityHeaders)
                    .body(body).retrieve().body(String.class);
            return parseChapterGraph(raw);
        }, "DeepGraph 章节图谱构建", () -> unavailableChapterGraph());
    }

    @Override
    public DeepGraphLearningPathResult learningPath(String target, Long courseId, int maxDepth,
                                                    boolean searchAllSources) {
        return executeWithRetry(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("target", target);
            if (courseId != null) {
                body.put(ContractPaths.F_COURSE_ID, courseId);
            }
            body.put("max_depth", maxDepth);
            body.put("search_all_sources", searchAllSources);
            String raw = restClient.post().uri(ContractPaths.DEEPGRAPH_LEARNING_PATH)
                    .headers(this::applyIdentityHeaders)
                    .body(body).retrieve().body(String.class);
            return parseLearningPath(raw);
        }, "DeepGraph 学习路径规划", () -> unavailableLearningPath());
    }

    @Override
    public DeepGraphCourseGraphData getCourseGraph(Long courseId, String documentName) {
        return executeWithRetry(() -> {
            // 文档名含中文/括号：必须走 URI 模板变量（手工编码会被 RestClient 二次编码）
            String uri = ContractPaths.DEEPGRAPH_COURSE_GRAPH
                    + "?course_id={courseId}&document_name={documentName}";
            String raw = restClient.get()
                    .uri(uri, courseId, documentName)
                    .headers(this::applyIdentityHeaders)
                    .retrieve().body(String.class);
            return parseCourseGraph(raw);
        }, "DeepGraph 课程图谱聚合", () -> unavailableCourseGraph());
    }

    // ===================== 解析 =====================

    private DeepGraphRetrievalResult parseRetrieve(String raw) {
        if (raw == null || raw.isBlank()) {
            return unavailableRetrieve();
        }
        try {
            JsonNode root = OM.readTree(raw);
            // 与 HttpRagProvider 同口径：Python 侧可能以 body{code:503, mode:UNAVAILABLE} 表达降级
            if (ContractPaths.isUnavailable(root) || isExplicitFailure(root)) {
                return unavailableRetrieve();
            }
            List<DeepGraphSource> sources = parseSources(root.get(ContractPaths.R_RESULTS));
            String graphContext = textOrNull(root, ContractPaths.R_GRAPH_CONTEXT);
            String mode = textOrNull(root, ContractPaths.R_MODE);
            return new DeepGraphRetrievalResult(sources, graphContext,
                    mode != null ? mode : ContractPaths.MODE_ENGINE, null, null);
        } catch (Exception e) {
            log.debug("DeepGraph RAG 检索返回解析失败：{}", e.getMessage());
            return unavailableRetrieve();
        }
    }

    private DeepGraphIngestResult parseIngest(String raw) {
        if (raw == null || raw.isBlank()) {
            return unavailableIngest();
        }
        try {
            JsonNode root = OM.readTree(raw);
            if (ContractPaths.isUnavailable(root) || isExplicitFailure(root)) {
                return unavailableIngest();
            }
            return new DeepGraphIngestResult(
                    root.path(ContractPaths.R_SUCCESS).asBoolean(false),
                    root.has(ContractPaths.F_COURSE_ID) ? root.get(ContractPaths.F_COURSE_ID).asLong() : null,
                    textOrNull(root, ContractPaths.R_STORED_AS),
                    root.has("chapters") ? root.get("chapters").asInt() : null,
                    root.has(ContractPaths.R_CHUNK_COUNT) ? root.get(ContractPaths.R_CHUNK_COUNT).asInt() : null,
                    root.has(ContractPaths.R_HAS_GRAPH) ? root.get(ContractPaths.R_HAS_GRAPH).asBoolean() : null,
                    root.has(ContractPaths.R_GRAPH_EDGES) ? root.get(ContractPaths.R_GRAPH_EDGES).asInt() : null,
                    ContractPaths.MODE_ENGINE, null, null);
        } catch (Exception e) {
            log.debug("DeepGraph RAG 入库返回解析失败：{}", e.getMessage());
            return unavailableIngest();
        }
    }

    private DeepGraphDocumentList parseDocumentList(String raw) {
        if (raw == null || raw.isBlank()) {
            return new DeepGraphDocumentList(null, ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE,
                    ContractPaths.CODE_UNAVAILABLE);
        }
        try {
            JsonNode root = OM.readTree(raw);
            if (ContractPaths.isUnavailable(root) || isExplicitFailure(root)) {
                return new DeepGraphDocumentList(null, ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE,
                        ContractPaths.CODE_UNAVAILABLE);
            }
            List<DeepGraphDocument> docs = new ArrayList<>();
            // 兼容裸数组与 {documents: []} 两种形态（与 8002 parseDocumentList 同款鲁棒性）
            JsonNode arr = root.isArray() ? root
                    : (root.has("documents") && root.get("documents").isArray()
                        ? root.get("documents") : null);
            if (arr != null) {
                for (JsonNode d : arr) {
                    docs.add(new DeepGraphDocument(
                            textOrNull(d, ContractPaths.F_DOCUMENT_NAME),
                            d.has(ContractPaths.F_COURSE_ID) ? d.get(ContractPaths.F_COURSE_ID).asLong() : null,
                            d.has("chapters_count") ? d.get("chapters_count").asInt() : null,
                            d.has("has_global_graph") ? d.get("has_global_graph").asBoolean() : null,
                            d.has("has_faiss_index") ? d.get("has_faiss_index").asBoolean() : null));
                }
            }
            return new DeepGraphDocumentList(docs, null, null);
        } catch (Exception e) {
            log.debug("DeepGraph 文档列表返回解析失败：{}", e.getMessage());
            return new DeepGraphDocumentList(null, ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE,
                    ContractPaths.CODE_UNAVAILABLE);
        }
    }

    private DeepGraphChapterGraphResult parseChapterGraph(String raw) {
        if (raw == null || raw.isBlank()) {
            return unavailableChapterGraph();
        }
        try {
            JsonNode root = OM.readTree(raw);
            if (ContractPaths.isUnavailable(root) || isExplicitFailure(root)) {
                return unavailableChapterGraph();
            }
            List<RelationRow> relations = new ArrayList<>();
            JsonNode arr = root.has("relations") && root.get("relations").isArray()
                    ? root.get("relations") : null;
            if (arr != null) {
                for (JsonNode r : arr) {
                    relations.add(new RelationRow(
                            textOrNull(r, "source"), textOrNull(r, "relation"), textOrNull(r, "target")));
                }
            }
            return new DeepGraphChapterGraphResult(
                    root.path(ContractPaths.R_SUCCESS).asBoolean(false),
                    root.has("nodes") ? root.get("nodes").asInt() : null,
                    root.has(ContractPaths.R_EDGES) ? root.get(ContractPaths.R_EDGES).asInt() : null,
                    relations,
                    root.has("neo4j_synced") ? root.get("neo4j_synced").asBoolean() : null,
                    ContractPaths.MODE_ENGINE, null, null);
        } catch (Exception e) {
            log.debug("DeepGraph 章节图谱返回解析失败：{}", e.getMessage());
            return unavailableChapterGraph();
        }
    }

    private DeepGraphLearningPathResult parseLearningPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return unavailableLearningPath();
        }
        try {
            JsonNode root = OM.readTree(raw);
            if (ContractPaths.isUnavailable(root) || isExplicitFailure(root)) {
                return unavailableLearningPath();
            }
            return new DeepGraphLearningPathResult(
                    textOrNull(root, "target"),
                    textOrNull(root, ContractPaths.R_PATH_TEXT),
                    textOrNull(root, "source_doc"),
                    ContractPaths.MODE_ENGINE, null, null);
        } catch (Exception e) {
            log.debug("DeepGraph 学习路径返回解析失败：{}", e.getMessage());
            return unavailableLearningPath();
        }
    }

    /** 读取字符串字段，缺失或为 null 时返回 null。 */
    private static String textOrNull(JsonNode root, String field) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return null;
        }
        return root.get(field).asText();
    }

    private List<DeepGraphSource> parseSources(JsonNode node) {
        List<DeepGraphSource> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode s : node) {
                Long docId = null;
                if (s.has(ContractPaths.F_DOCUMENT_ID) && !s.get(ContractPaths.F_DOCUMENT_ID).isNull()) {
                    docId = s.get(ContractPaths.F_DOCUMENT_ID).asLong();
                }
                String docName = textOrNull(s, ContractPaths.F_DOCUMENT_NAME);
                Double relevance = s.has("relevance_score") && !s.get("relevance_score").isNull()
                        ? s.get("relevance_score").asDouble() : null;
                String snippet = textOrNull(s, "snippet");
                String retrieval = textOrNull(s, ContractPaths.R_RETRIEVAL);
                list.add(new DeepGraphSource(docId, docName, relevance, snippet, retrieval));
            }
        }
        return list;
    }

    private DeepGraphCourseGraphData unavailableCourseGraph() {
        return new DeepGraphCourseGraphData(null, null, null, ContractPaths.MODE_UNAVAILABLE,
                ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE);
    }

    private DeepGraphCourseGraphData parseCourseGraph(String raw) {
        if (raw == null || raw.isBlank()) {
            return unavailableCourseGraph();
        }
        try {
            JsonNode root = OM.readTree(raw);
            if (ContractPaths.isUnavailable(root) || isExplicitFailure(root)) {
                return unavailableCourseGraph();
            }
            List<String> chapters = new ArrayList<>();
            JsonNode chaptersNode = root.get("chapters");
            if (chaptersNode != null && chaptersNode.isArray()) {
                for (JsonNode n : chaptersNode) {
                    chapters.add(n.asText());
                }
            }
            List<DeepGraphRagContract.CourseGraphNode> nodes = new ArrayList<>();
            JsonNode nodesNode = root.get("nodes");
            if (nodesNode != null && nodesNode.isArray()) {
                for (JsonNode n : nodesNode) {
                    nodes.add(new DeepGraphRagContract.CourseGraphNode(
                            textOrNull(n, "name"), textOrNull(n, "type"), textOrNull(n, "evidence")));
                }
            }
            List<DeepGraphRagContract.CourseGraphEdge> edges = new ArrayList<>();
            JsonNode edgesNode = root.get("edges");
            if (edgesNode != null && edgesNode.isArray()) {
                for (JsonNode e : edgesNode) {
                    edges.add(new DeepGraphRagContract.CourseGraphEdge(
                            textOrNull(e, "source"), textOrNull(e, "target"),
                            textOrNull(e, "relation"), textOrNull(e, "evidence"),
                            textOrNull(e, "chapter")));
                }
            }
            String mode = textOrNull(root, ContractPaths.R_MODE);
            return new DeepGraphCourseGraphData(chapters, nodes, edges,
                    mode != null ? mode : ContractPaths.MODE_ENGINE, null, null);
        } catch (Exception e) {
            log.debug("DeepGraph 课程图谱聚合返回解析失败：{}", e.getMessage());
            return unavailableCourseGraph();
        }
    }

    /**
     * 删除响应：只有明确成功才返回成功；空响应/503/success=false 一律降级。
     */
    private DeepGraphDeleteResult parseDeleteResult(String documentName, String raw) {
        if (raw == null || raw.isBlank()) {
            return new DeepGraphDeleteResult(null, documentName,
                    ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE);
        }
        try {
            JsonNode root = OM.readTree(raw);
            if (ContractPaths.isUnavailable(root) || isExplicitFailure(root)) {
                return new DeepGraphDeleteResult(false, documentName,
                        ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE);
            }
            return new DeepGraphDeleteResult(true, documentName, null, null);
        } catch (Exception e) {
            log.debug("DeepGraph 删除文档返回解析失败：{}", e.getMessage());
            return new DeepGraphDeleteResult(null, documentName,
                    ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE);
        }
    }

    private static boolean isExplicitFailure(JsonNode root) {
        return root != null
                && root.has(ContractPaths.R_SUCCESS)
                && root.get(ContractPaths.R_SUCCESS).isBoolean()
                && !root.get(ContractPaths.R_SUCCESS).asBoolean();
    }

    private DeepGraphRetrievalResult unavailableRetrieve() {
        return new DeepGraphRetrievalResult(null, null, ContractPaths.MODE_UNAVAILABLE,
                ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE);
    }

    private DeepGraphIngestResult unavailableIngest() {
        return new DeepGraphIngestResult(null, null, null, null, null, null, null,
                ContractPaths.MODE_UNAVAILABLE, ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE,
                ContractPaths.CODE_UNAVAILABLE);
    }

    private DeepGraphChapterGraphResult unavailableChapterGraph() {
        return new DeepGraphChapterGraphResult(null, null, null, null, null,
                ContractPaths.MODE_UNAVAILABLE, ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE,
                ContractPaths.CODE_UNAVAILABLE);
    }

    private DeepGraphLearningPathResult unavailableLearningPath() {
        return new DeepGraphLearningPathResult(null, null, null,
                ContractPaths.MODE_UNAVAILABLE, ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE,
                ContractPaths.CODE_UNAVAILABLE);
    }

    // ===================== 重试执行器（口径与 HttpRagProvider 完全一致） =====================

    @FunctionalInterface
    private interface HttpCall<T> {
        T execute() throws Exception;
    }

    private <T> T executeWithRetry(HttpCall<T> call, String operation, Supplier<T> fallback) {
        try (Entry entry = SphU.entry(DEEPGRAPH_RESOURCE, EntryType.OUT)) {
            Exception lastException = null;
            for (int i = 0; i <= maxRetries; i++) {
                try {
                    return call.execute();
                } catch (Exception e) {
                    lastException = e;
                    if (isReadTimeout(e) || isConnectFailure(e) || isServiceUnavailable(e)) {
                        // 读超时 / 连接拒绝（8501 未启动）/ HTTP 503：不重试，直接降级
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
            log.warn("[Sentinel] DeepGraph 下游(resource={}) 被限流/熔断，操作={}，降级返回 503",
                    DEEPGRAPH_RESOURCE, operation);
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

    private static boolean isReadTimeout(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SocketTimeoutException
                    || t instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static boolean isServiceUnavailable(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof org.springframework.web.client.HttpServerErrorException hse) {
                return hse.getStatusCode().value() == ContractPaths.CODE_UNAVAILABLE;
            }
            t = t.getCause();
        }
        return false;
    }

    private static boolean isConnectFailure(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof java.net.ConnectException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
