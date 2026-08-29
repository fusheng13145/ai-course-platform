package com.aicourse.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAG 服务单元测试（补充版，自包含）。使用 JDK 内置 HttpServer 桩模拟 Python RAG 微服务（8002）。
 * 覆盖：
 * <ol>
 *   <li>服务未启用（rag.enabled=false，默认）→ 所有方法返回 503，健康检查 false；</li>
 *   <li>服务启用且可达 → 全部 CRUD 方法正确代理到 /api/v1/rag/*（含 courseId 为 null 与 非null 两支），
 *       topK<=0 时回落 defaultTopK，健康检查 true；</li>
 *   <li>重试机制：首次 500 后重试成功（覆盖重试 + 退避 sleep）；
 *       全部失败（覆盖 Tracer.trace + 错误日志 + 最终 503）；</li>
 *   <li>启用但不可达（坏端口 / 连接拒绝）→ 重试耗尽后返回 503。</li>
 * </ol>
 * 注：Sentinel BlockException 限流/熔断分支与 Thread.sleep 被中断分支需专用 Sentinel 规则/线程干预，
 * 单测环境难以稳定构造，已在汇报中说明未覆盖。
 */
class RAGServiceImplCoverageTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;
    /** 剩余需要返回 500 的次数（用尽后返回 200 成功体） */
    private volatile int failRemaining = 0;
    /** 处理前人为阻塞毫秒数（用于触发读取超时分支，本环境 RestClient 会包装为 ResourceAccessException） */
    private volatile long slowMillis = 0;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        failRemaining = 0;
        slowMillis = 0;
        lastPath.set(null);
        lastBody.set(null);
    }

    // ---------- 未启用（默认）----------

    @Test
    @DisplayName("disabled: ask 返回 503 且健康检查 false")
    void disabled_ask() {
        RAGServiceImpl svc = newDisabledService();
        assertTrue(svc.ask("什么是梯度下降", 1L, 3).contains("\"code\":503"));
        assertFalse(svc.healthCheck());
    }

    @Test
    @DisplayName("disabled: 所有方法均返回 503")
    void disabled_allMethods() {
        RAGServiceImpl svc = newDisabledService();
        assertTrue(svc.addDocument(1L, "文档", "内容").contains("\"code\":503"));
        assertTrue(svc.deleteDocument(1L).contains("\"code\":503"));
        assertTrue(svc.listDocuments(1L).contains("\"code\":503"));
        assertTrue(svc.listDocuments(null).contains("\"code\":503"));
        assertTrue(svc.updateDocument(1L, "新内容").contains("\"code\":503"));
        assertTrue(svc.buildIndex(1L).contains("\"code\":503"));
        assertTrue(svc.clearIndex(1L).contains("\"code\":503"));
        assertTrue(svc.retrieve("梯度", 1L, 3).contains("\"code\":503"));
        assertFalse(svc.healthCheck());
    }

    // ---------- 启用 + Python 端可达 ----------

    @Test
    @DisplayName("enabled: 健康检查 true 且 ask 正确代理（courseId 非空）")
    void enabled_ask() throws Exception {
        startStub();
        RAGServiceImpl svc = newEnabledService(2, 2, 0);
        assertTrue(svc.healthCheck());

        String res = svc.ask("什么是梯度下降", 7L, 3);
        JsonNode map = OBJECT_MAPPER.readTree(res);
        assertEquals("ok", map.get("answer").asText());
        // 代理路径与请求体校验
        assertEquals("/api/v1/rag/ask", lastPath.get());
        JsonNode body = OBJECT_MAPPER.readTree(lastBody.get());
        assertEquals(7, body.get("course_id").asInt());
        assertEquals(3, body.get("top_k").asInt());
    }

    @Test
    @DisplayName("enabled: ask topK<=0 -> 回落 defaultTopK(5)")
    void enabled_ask_topKZero() throws Exception {
        startStub();
        RAGServiceImpl svc = newEnabledService(2, 2, 0);
        String res = svc.ask("问题", 1L, 0);
        JsonNode map = OBJECT_MAPPER.readTree(res);
        assertEquals("ok", map.get("answer").asText());
        JsonNode body = OBJECT_MAPPER.readTree(lastBody.get());
        assertEquals(5, body.get("top_k").asInt());
    }

    @Test
    @DisplayName("enabled: ask courseId 为 null -> 请求体不含 course_id")
    void enabled_ask_nullCourseId() throws Exception {
        startStub();
        RAGServiceImpl svc = newEnabledService(2, 2, 0);
        svc.ask("问题", null, 4);
        JsonNode body = OBJECT_MAPPER.readTree(lastBody.get());
        assertFalse(body.has("course_id"));
        assertEquals(4, body.get("top_k").asInt());
    }

    @Test
    @DisplayName("enabled: retrieve（courseId 非空/空）正确代理")
    void enabled_retrieve() throws Exception {
        startStub();
        RAGServiceImpl svc = newEnabledService(2, 2, 0);
        Map<?, ?> r1 = OBJECT_MAPPER.readValue(svc.retrieve("梯度", 5L, 3), Map.class);
        assertEquals(1, ((java.util.List<?>) r1.get("results")).size());
        JsonNode b1 = OBJECT_MAPPER.readTree(lastBody.get());
        assertEquals(5, b1.get("course_id").asInt());

        svc.retrieve("梯度", null, 3);
        JsonNode b2 = OBJECT_MAPPER.readTree(lastBody.get());
        assertFalse(b2.has("course_id"));
    }

    @Test
    @DisplayName("enabled: listDocuments（courseId 非空 -> 查询参数；null -> 根路径）")
    void enabled_listDocuments() throws Exception {
        startStub();
        RAGServiceImpl svc = newEnabledService(2, 2, 0);
        svc.listDocuments(9L);
        assertEquals("/api/v1/rag/documents?course_id=9", lastPath.get());
        svc.listDocuments(null);
        assertEquals("/api/v1/rag/documents", lastPath.get());
    }

    @Test
    @DisplayName("enabled: addDocument / updateDocument / deleteDocument / buildIndex / clearIndex 代理成功")
    void enabled_crud() throws Exception {
        startStub();
        RAGServiceImpl svc = newEnabledService(2, 2, 0);
        // 新契约：门面序列化为强类型 RagDocument/IndexResult（含 mode=ENGINE / success），
        // 不再透传 Python 原始 success 字段；成功即非 503 降级，以此校验委派成功。
        String addRes = svc.addDocument(1L, "d", "c");
        assertFalse(addRes.contains("\"code\":503"), "addDocument 成功应非 503: " + addRes);
        assertTrue(addRes.contains("\"mode\":\"ENGINE\""), "addDocument 应含 mode=ENGINE: " + addRes);

        String updRes = svc.updateDocument(2L, "new");
        assertFalse(updRes.contains("\"code\":503"), "updateDocument 成功应非 503: " + updRes);
        assertTrue(updRes.contains("\"mode\":\"ENGINE\""), "updateDocument 应含 mode=ENGINE: " + updRes);

        String delRes = svc.deleteDocument(3L);
        assertFalse(delRes.contains("\"code\":503"), "deleteDocument 成功应非 503: " + delRes);
        assertTrue(delRes.contains("\"mode\":\"ENGINE\""), "deleteDocument 应含 mode=ENGINE: " + delRes);

        String bldRes = svc.buildIndex(7L);
        assertFalse(bldRes.contains("\"code\":503"), "buildIndex 成功应非 503: " + bldRes);
        assertTrue(bldRes.contains("\"success\":true"), "buildIndex 应含 success=true: " + bldRes);

        String clrRes = svc.clearIndex(7L);
        assertFalse(clrRes.contains("\"code\":503"), "clearIndex 成功应非 503: " + clrRes);
        assertTrue(clrRes.contains("\"success\":true"), "clearIndex 应含 success=true: " + clrRes);
    }

    // ---------- 重试 / 失败降级 ----------

    @Test
    @DisplayName("enabled: 首次 500 后重试成功（覆盖重试 + 退避 sleep）")
    void enabled_retryThenSuccess() throws Exception {
        failRemaining = 1; // 第一次返回 500，之后成功
        startStub();
        RAGServiceImpl svc = newEnabledService(2, 2, 1); // maxRetries=1
        String res = svc.ask("q", 1L, 3);
        JsonNode map = OBJECT_MAPPER.readTree(res);
        assertEquals("ok", map.get("answer").asText());
    }

    @Test
    @DisplayName("enabled: 全部失败（maxRetries=2）-> Tracer.trace + 最终 503")
    void enabled_allFail_503() throws Exception {
        failRemaining = 100; // 始终 500
        startStub();
        RAGServiceImpl svc = newEnabledService(2, 2, 2);
        String res = svc.ask("q", 1L, 3);
        assertTrue(res.contains("\"code\":503"));
        assertFalse(svc.healthCheck());
    }

    @Test
    @DisplayName("enabled: 读取超时（slow > readTimeout）-> 非 SocketTimeout 包装异常亦重试并最终 503")
    void enabled_readTimeout_503() throws Exception {
        slowMillis = 2000; // 处理阻塞 2 秒
        startStub();
        // readTimeout=1s，maxRetries=0（单程超时即可落入最终 503 分支）
        RAGServiceImpl svc = new RAGServiceImpl(true, baseUrl(), 1, 1, 0,
                "/api/v1/rag/health", 5);
        assertTrue(svc.ask("q", 1L, 3).contains("\"code\":503"));
        assertFalse(svc.healthCheck());
    }

    @Test
    @DisplayName("enabled 但 Python 不可达（坏端口）-> 重试耗尽返回 503")
    void enabled_down_503() {
        RAGServiceImpl svc = new RAGServiceImpl(true, "http://127.0.0.1:1", 1, 1, 0,
                "/api/v1/rag/health", 5);
        assertTrue(svc.ask("q", 1L, 3).contains("\"code\":503"));
        assertFalse(svc.healthCheck());
    }

    // ---------- helpers ----------

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private RAGServiceImpl newDisabledService() {
        return new RAGServiceImpl(false, "http://localhost:8002", 1, 1, 0,
                "/api/v1/rag/health", 5);
    }

    private RAGServiceImpl newEnabledService(int connectTimeout, int readTimeout, int maxRetries) {
        return new RAGServiceImpl(true, baseUrl(), connectTimeout, readTimeout, maxRetries,
                "/api/v1/rag/health", 5);
    }

    private void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] reqBytes = exchange.getRequestBody().readAllBytes();
        String reqBody = new String(reqBytes, StandardCharsets.UTF_8);
        // 完整捕获请求路径（含查询字符串）：getPath() 不含 query，而 listDocuments 用 GET + ?course_id= 查询参数
        java.net.URI reqUri = exchange.getRequestURI();
        String reqQuery = reqUri.getQuery();
        lastPath.set(reqUri.getPath() + (reqQuery != null ? "?" + reqQuery : ""));
        lastBody.set(reqBody);

        if (slowMillis > 0) {
            try {
                Thread.sleep(slowMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        int status = 200;
        String body;
        if (failRemaining > 0) {
            failRemaining--;
            status = 500;
            body = "{\"error\":\"internal\"}";
        } else {
            body = route(exchange.getRequestURI().getPath());
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String route(String path) {
        switch (path) {
            case "/api/v1/rag/health" -> { return "{}"; }
            case "/api/v1/rag/ask" -> { return "{\"answer\":\"ok\",\"sources\":[]}"; }
            case "/api/v1/rag/retrieve" -> {
                return "{\"results\":[{\"document_id\":1,\"document_name\":\"a\",\"relevance_score\":0.9}]}";
            }
            case "/api/v1/rag/index/build/7" -> { return "{\"success\":true,\"indexed_chunks\":3}"; }
            default -> { return "{\"success\":true}"; }
        }
    }
}
