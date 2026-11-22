package com.aicourse.integration.deepgraph;

import com.aicourse.integration.ContractPaths;
import com.aicourse.integration.deepgraph.DeepGraphRagContract.DeepGraphRetrievalResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeepGraph RAG HTTP 远端实现 {@link HttpDeepGraphRagProvider} 的契约测试。
 * <p>
 * 用 JDK 内置 {@link HttpServer} 桩模拟 Python deepgraph_rag（8501），验证请求路径 /
 * 响应解析契约；同时验证「引擎不可达 → 503 降级占位」与「HTTP 200 + body{code:503,
 * mode:UNAVAILABLE} → 同样按不可用处理」两种降级形态（与 8002 契约口径一致）。
 * 不依赖任何外部进程，可在 CI 离线运行。
 */
class HttpDeepGraphRagProviderContractTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private HttpDeepGraphRagProvider newProvider() {
        return new HttpDeepGraphRagProvider(baseUrl(), 2, 2, 0, ContractPaths.DEEPGRAPH_HEALTH);
    }

    private void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(ContractPaths.DEEPGRAPH_HEALTH, ex -> respond(ex, 200,
                "{\"status\":\"ok\",\"engine\":{\"available\":true,\"provider\":\"deepseek\"}}"));
        server.createContext(ContractPaths.DEEPGRAPH_RETRIEVE, ex -> respond(ex, 200,
                "{\"mode\":\"ENGINE\",\"results\":[{\"document_id\":null,\"document_name\":\"course4178_教材.txt\","
                        + "\"relevance_score\":0.92,\"snippet\":\"TCP 三次握手\",\"retrieval\":\"VECTOR\"}],"
                        + "\"graph_context\":\"- TCP (定义) 三次握手\"}"));
        server.createContext(ContractPaths.DEEPGRAPH_INGEST, ex -> respond(ex, 200,
                "{\"success\":true,\"course_id\":4178,\"stored_as\":\"course4178_教材.txt\",\"chapters\":8,"
                        + "\"chunk_count\":42,\"has_graph\":true,\"graph_edges\":30,\"mode\":\"ENGINE\"}"));
        server.createContext(ContractPaths.DEEPGRAPH_DOCUMENTS, ex -> {
            if ("GET".equals(ex.getRequestMethod())) {
                respond(ex, 200, "{\"documents\":[{\"document_name\":\"course4178_教材.txt\",\"course_id\":4178,"
                        + "\"chapters_count\":8,\"has_global_graph\":true,\"has_faiss_index\":true}]}");
            } else {
                respond(ex, 200, "{\"success\":true,\"document_name\":\"course4178_教材.txt\"}");
            }
        });
        server.createContext(ContractPaths.DEEPGRAPH_CHAPTER_GRAPH, ex -> respond(ex, 200,
                "{\"success\":true,\"nodes\":12,\"edges\":18,\"relations\":[{\"source\":\"TCP\",\"relation\":\"定义\",\"target\":\"三次握手\"}],"
                        + "\"neo4j_synced\":true,\"mode\":\"ENGINE\"}"));
        server.createContext(ContractPaths.DEEPGRAPH_LEARNING_PATH, ex -> respond(ex, 200,
                "{\"target\":\"TCP协议\",\"path_text\":\"## 学习目标\",\"source_doc\":\"course4178_教材.txt\",\"mode\":\"ENGINE\"}"));
        server.start();
    }

    /** 可自定义 retrieve 响应形态的桩（body 降级 / HTTP 503 两种形态）。 */
    private void startStubWith(int retrieveStatus, String retrieveBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(ContractPaths.DEEPGRAPH_HEALTH, ex -> respond(ex, 200, "{}"));
        server.createContext(ContractPaths.DEEPGRAPH_RETRIEVE, ex -> respond(ex, retrieveStatus, retrieveBody));
        server.start();
    }

    private void respond(HttpExchange ex, int code, String body) throws IOException {
        ex.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        // Python 侧真实响应恒为 application/json（UTF-8）；不声明字符集会被
        // StringHttpMessageConverter 按 ISO-8859-1 解码，中文断言必然乱码
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    void healthCheck_engineUp_returnsTrue() throws Exception {
        startStub();
        assertTrue(newProvider().healthCheck().healthy());
    }

    @Test
    void healthCheck_engineDown_returnsFalse() {
        // 不启动任何桩：连接拒绝 → 不健康（读超时/连接失败不重试，立刻返回）
        assertFalse(new HttpDeepGraphRagProvider("http://127.0.0.1:1", 1, 1, 0,
                ContractPaths.DEEPGRAPH_HEALTH).healthCheck().healthy());
    }

    @Test
    void retrieve_parsesResultsAndGraphContext() throws Exception {
        startStub();
        DeepGraphRetrievalResult r = newProvider().retrieve(
                new DeepGraphRagContract.DeepGraphRetrieveRequest("三次握手", 4178L, 3));
        assertTrue(r.engineReady(), "ENGINE + 非503 才能作为真实引擎证据");
        assertEquals(ContractPaths.MODE_ENGINE, r.mode());
        assertEquals(1, r.results().size());
        assertEquals("course4178_教材.txt", r.results().get(0).documentName());
        assertEquals(0.92, r.results().get(0).relevanceScore(), 1e-9);
        assertEquals("VECTOR", r.results().get(0).retrieval());
        assertEquals("- TCP (定义) 三次握手", r.graphContext());
    }

    @Test
    void retrieve_bodyCode503_degradesEvenWithHttp200() throws Exception {
        // D7 同款契约：HTTP 200 + body{code:503, mode:UNAVAILABLE} 必须按不可用处理
        startStubWith(200,
                "{\"code\":503,\"mode\":\"UNAVAILABLE\",\"error\":\"尚未构建索引\",\"results\":[]}");
        DeepGraphRetrievalResult r = newProvider().retrieve(
                new DeepGraphRagContract.DeepGraphRetrieveRequest("q", 1L, 3));
        assertFalse(r.engineReady());
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE, r.error());
    }

    @Test
    void retrieve_http503_degradesWithoutRetry() throws Exception {
        startStubWith(503, "{\"code\":503,\"mode\":\"UNAVAILABLE\",\"error\":\"x\"}");
        DeepGraphRetrievalResult r = newProvider().retrieve(
                new DeepGraphRagContract.DeepGraphRetrieveRequest("q", 1L, 3));
        assertFalse(r.engineReady());
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
    }

    @Test
    void retrieve_engineUnreachable_degradesToPlaceholder() {
        DeepGraphRetrievalResult r = new HttpDeepGraphRagProvider("http://127.0.0.1:1", 1, 1, 0,
                ContractPaths.DEEPGRAPH_HEALTH).retrieve(
                new DeepGraphRagContract.DeepGraphRetrieveRequest("q", 1L, 3));
        assertFalse(r.engineReady());
        assertEquals(ContractPaths.MODE_UNAVAILABLE, r.mode());
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
    }

    @Test
    void ingest_parsesSuccessShape() throws Exception {
        startStub();
        DeepGraphRagContract.DeepGraphIngestResult r = newProvider().ingest(4178L, "教材", "正文");
        assertEquals(Boolean.TRUE, r.success());
        assertEquals("course4178_教材.txt", r.storedAs());
        assertEquals(42, r.chunkCount());
        assertEquals(Boolean.TRUE, r.hasGraph());
        assertEquals(30, r.graphEdges());
    }

    @Test
    void listDocuments_parsesDocuments() throws Exception {
        startStub();
        DeepGraphRagContract.DeepGraphDocumentList r = newProvider().listDocuments(4178L);
        assertEquals(1, r.documents().size());
        assertEquals("course4178_教材.txt", r.documents().get(0).documentName());
        assertEquals(Boolean.TRUE, r.documents().get(0).hasFaissIndex());
    }

    @Test
    void deleteDocument_parsesSuccess() throws Exception {
        startStub();
        DeepGraphRagContract.DeepGraphDeleteResult r = newProvider().deleteDocument(4178L, "course4178_教材.txt");
        assertEquals(Boolean.TRUE, r.success());
    }

    @Test
    void chapterGraph_parsesRelations() throws Exception {
        startStub();
        DeepGraphRagContract.DeepGraphChapterGraphResult r =
                newProvider().chapterGraph(4178L, "教材", "第1章 概述", null);
        assertEquals(Boolean.TRUE, r.success());
        assertEquals(12, r.nodes());
        assertEquals(18, r.edges());
        assertEquals(1, r.relations().size());
        assertEquals("TCP", r.relations().get(0).source());
        assertEquals(Boolean.TRUE, r.neo4jSynced());
    }

    @Test
    void learningPath_parsesPathText() throws Exception {
        startStub();
        DeepGraphRagContract.DeepGraphLearningPathResult r =
                newProvider().learningPath("TCP协议", 4178L, 3, true);
        assertEquals("TCP协议", r.target());
        assertEquals("## 学习目标", r.pathText());
        assertEquals(ContractPaths.MODE_ENGINE, r.mode());
    }

    @Test
    void listDocuments_engineUnreachable_degrades() {
        DeepGraphRagContract.DeepGraphDocumentList r = new HttpDeepGraphRagProvider(
                "http://127.0.0.1:1", 1, 1, 0, ContractPaths.DEEPGRAPH_HEALTH).listDocuments(1L);
        assertNull(r.documents());
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
    }
}
