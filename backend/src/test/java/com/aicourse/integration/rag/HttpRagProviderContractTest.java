package com.aicourse.integration.rag;

import com.aicourse.integration.ContractPaths;
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
 * RAG HTTP 远端实现 {@link HttpRagProvider} 的契约测试。
 * <p>
 * 用 JDK 内置 {@link HttpServer} 桩模拟 Python 8002，验证本端与 RAG 引擎之间的
 * 请求路径 / 响应解析契约；同时验证「引擎不可达时返回 503 降级占位」的行为（与
 * {@code AIServiceImpl.ragAsk}「检测到 code:503 即回退纯 LLM」约定一致）。
 * 不依赖任何外部进程，可在 CI 离线运行。是重构前反射注入式 {@code RagServiceContractTest}
 * 的等价替代（门面重构后 {@code restClient} 字段已下沉至 Provider 层，反射注入不可行）。
 */
class HttpRagProviderContractTest {

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

    private HttpRagProvider newProvider() {
        return new HttpRagProvider(baseUrl(), 2, 2, 0, ContractPaths.RAG_HEALTH, 5);
    }

    private void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(ContractPaths.RAG_HEALTH, ex -> respond(ex, 200, "{}"));
        server.createContext(ContractPaths.RAG_ASK, ex -> respond(ex, 200,
                "{\"answer\":\"recursion is self-calling\",\"sources\":[{\"document_id\":1,\"document_name\":\"d1\",\"relevance_score\":0.9,\"snippet\":\"s\"}]}"));
        server.createContext(ContractPaths.RAG_DOCUMENTS, ex -> {
            if ("GET".equals(ex.getRequestMethod())) {
                respond(ex, 200, "{\"documents\":[{\"id\":1,\"document_name\":\"d1\"}]}");
            } else {
                respond(ex, 200, "{\"id\":1,\"document_name\":\"d1\",\"course_id\":1,\"content\":\"c\"}");
            }
        });
        server.createContext(ContractPaths.RAG_DOCUMENTS + "/5", ex -> respond(ex, 200,
                "{\"id\":5,\"document_name\":\"d5\"}"));
        server.createContext(ContractPaths.RAG_RETRIEVE, ex -> respond(ex, 200,
                "{\"results\":[{\"document_id\":1,\"document_name\":\"d1\",\"relevance_score\":0.8,\"snippet\":\"s\"}]}"));
        server.createContext("/api/v1/rag/index/build/1", ex -> respond(ex, 200,
                "{\"success\":true,\"indexed_chunks\":10}"));
        server.createContext("/api/v1/rag/index/1", ex -> respond(ex, 200,
                "{\"success\":true,\"indexed_chunks\":0}"));
        server.start();
    }

    private void respond(HttpExchange ex, int code, String body) throws IOException {
        ex.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
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
    void ask_callsEngineEndpoint_andParsesAnswer() throws Exception {
        startStub();
        RagContract.RagQueryResult r = newProvider().ask(new RagContract.RagAskRequest("什么是递归？", 1L, 3));
        assertEquals(ContractPaths.MODE_ENGINE, r.mode());
        assertEquals("recursion is self-calling", r.answer());
        assertEquals(1, r.sources().size());
        assertEquals("d1", r.sources().get(0).documentName());
    }

    @Test
    void retrieve_callsEngineEndpoint_andParsesResults() throws Exception {
        startStub();
        RagContract.RagRetrievalResult r = newProvider().retrieve(new RagContract.RagRetrieveRequest("二叉树", 1L, 5));
        assertEquals(1, r.results().size());
        assertEquals(0.8, r.results().get(0).relevanceScore(), 1e-9);
    }

    @Test
    void addDocument_callsEngineEndpoint_andParses() throws Exception {
        startStub();
        RagContract.RagDocument r = newProvider().addDocument(1L, "doc", "content");
        assertEquals(ContractPaths.MODE_ENGINE, r.mode());
        assertEquals(1L, r.id());
        assertEquals("d1", r.documentName());
    }

    @Test
    void listDocuments_callsEngineEndpoint_andParses() throws Exception {
        startStub();
        RagContract.RagDocumentList r = newProvider().listDocuments(1L);
        assertNull(r.code());
        assertEquals(1, r.documents().size());
    }

    @Test
    void deleteDocument_callsEngineEndpoint() throws Exception {
        startStub();
        RagContract.RagDocument r = newProvider().deleteDocument(5L);
        assertEquals(5L, r.id());
        assertEquals(ContractPaths.MODE_ENGINE, r.mode());
    }

    @Test
    void updateDocument_callsEngineEndpoint() throws Exception {
        startStub();
        RagContract.RagDocument r = newProvider().updateDocument(5L, "new content");
        assertEquals(ContractPaths.MODE_ENGINE, r.mode());
    }

    @Test
    void buildIndex_callsEngineEndpoint_andParses() throws Exception {
        startStub();
        RagContract.IndexResult r = newProvider().buildIndex(1L);
        assertEquals(Boolean.TRUE, r.success());
        assertEquals(10, r.indexedChunks());
    }

    @Test
    void clearIndex_callsEngineEndpoint_andParses() throws Exception {
        startStub();
        RagContract.IndexResult r = newProvider().clearIndex(1L);
        assertEquals(Boolean.TRUE, r.success());
    }

    @Test
    void ask_engineUnreachable_returns503() {
        HttpRagProvider provider = new HttpRagProvider("http://127.0.0.1:1", 1, 1, 0,
                ContractPaths.RAG_HEALTH, 5);
        RagContract.RagQueryResult r = provider.ask(new RagContract.RagAskRequest("q", 1L, 3));
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(ContractPaths.MSG_RAG_UNAVAILABLE, r.error());
    }

    @Test
    void healthCheck_engineDown_returnsFalse() {
        HttpRagProvider provider = new HttpRagProvider("http://127.0.0.1:1", 1, 1, 0,
                ContractPaths.RAG_HEALTH, 5);
        assertFalse(provider.healthCheck().healthy());
    }
}
