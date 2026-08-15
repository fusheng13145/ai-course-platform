package com.aicourse.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAG 服务单元测试（2026-08-13 起：RAG 引擎由团队另一成员负责，本端仅保留接口 + HTTP 代理）。
 * <p>
 * 覆盖两类行为：
 * <ol>
 *   <li>服务未启用（{@code rag.enabled=false}，默认）→ 全部方法返回 {@code {"code":503,...}}，健康检查 false；
 *       {@code AIServiceImpl.ragAsk} 依赖该 503 约定自动降级为纯 LLM 调用；</li>
 *   <li>服务启用且 Python 端可达（本地 JDK HttpServer 桩）→ 请求正确代理到 {@code /api/v1/rag/*}，健康检查 true。</li>
 * </ol>
 */
class RAGServiceImplTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ---------- 未启用（默认）----------

    @Test
    void disabled_ask_returns503() throws Exception {
        RAGServiceImpl svc = newDisabledService();
        assertTrue(svc.ask("什么是梯度下降", 1L, 3).contains("\"code\":503"));
        assertFalse(svc.healthCheck());
    }

    @Test
    void disabled_allMethods_return503() throws Exception {
        RAGServiceImpl svc = newDisabledService();
        assertEquals("{\"error\":\"RAG 引擎由团队另一成员负责，本端仅预留接口；服务未接入或不可用\",\"code\":503}",
                svc.addDocument(1L, "文档", "内容"));
        assertTrue(svc.deleteDocument(1L).contains("\"code\":503"));
        assertTrue(svc.listDocuments(1L).contains("\"code\":503"));
        assertTrue(svc.updateDocument(1L, "新内容").contains("\"code\":503"));
        assertTrue(svc.buildIndex(1L).contains("\"code\":503"));
        assertTrue(svc.clearIndex(1L).contains("\"code\":503"));
        assertTrue(svc.retrieve("梯度", 1L, 3).contains("\"code\":503"));
        assertFalse(svc.healthCheck());
    }

    // ---------- 启用 + Python 端可达 ----------

    @Test
    void enabled_proxiesAskAndHealth() throws Exception {
        startStubServer();
        RAGServiceImpl svc = newEnabledService();
        assertTrue(svc.healthCheck());

        String res = svc.ask("什么是梯度下降", 1L, 3);
        Map<?, ?> map = OBJECT_MAPPER.readValue(res, Map.class);
        assertEquals("proxy-ok", map.get("answer"));
        assertTrue(((java.util.List<?>) map.get("sources")).isEmpty());
    }

    @Test
    void enabled_proxiesRetrieveAndIndex() throws Exception {
        startStubServer();
        RAGServiceImpl svc = newEnabledService();

        Map<?, ?> ret = OBJECT_MAPPER.readValue(svc.retrieve("梯度", null, 2), Map.class);
        assertEquals(1, ((java.util.List<?>) ret.get("results")).size());

        Map<?, ?> built = OBJECT_MAPPER.readValue(svc.buildIndex(7L), Map.class);
        assertEquals(Boolean.TRUE, built.get("success"));
    }

    @Test
    void enabled_butServiceDown_returns503() {
        // 启用但 Python 端不可达（随机空闲端口）→ 重试耗尽后返回 503
        RAGServiceImpl svc = new RAGServiceImpl(true, "http://127.0.0.1:1", 1, 1, 0,
                "/api/v1/rag/health", 5);
        assertTrue(svc.ask("q", 1L, 3).contains("\"code\":503"));
        assertFalse(svc.healthCheck());
    }

    // ---------- helpers ----------

    private RAGServiceImpl newDisabledService() {
        return new RAGServiceImpl(false, "http://localhost:8002", 1, 1, 0,
                "/api/v1/rag/health", 5);
    }

    private RAGServiceImpl newEnabledService() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new RAGServiceImpl(true, baseUrl, 2, 2, 0, "/api/v1/rag/health", 5);
    }

    /** 极简 Python RAG 服务桩：按路径返回契约样式的固定响应。 */
    private void startStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        String path = exchange.getRequestURI().getPath();
        String body;
        switch (path) {
            case "/api/v1/rag/health" -> body = "{}";
            case "/api/v1/rag/ask" -> body = "{\"answer\":\"proxy-ok\",\"sources\":[]}";
            case "/api/v1/rag/retrieve" -> body = "{\"results\":[{\"document_id\":1,\"document_name\":\"a\",\"relevance_score\":0.9}]}";
            case "/api/v1/rag/index/build/7" -> body = "{\"success\":true,\"indexed_chunks\":3}";
            default -> body = "{\"success\":true}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
