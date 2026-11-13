package com.aicourse.integration.kg;

import com.aicourse.integration.ContractPaths;
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
import static org.mockito.Mockito.*;

/**
 * 知识图谱 HTTP 远端实现 {@link HttpKnowledgeGraphProvider} 的契约测试。
 * <p>
 * 用 JDK 内置 {@link HttpServer} 桩模拟 Python 8001，验证本端与图谱引擎之间的
 * 请求路径 / 响应解析契约；同时验证「引擎不可达或未找到路径时回退本地兜底」的降级行为。
 * 不依赖任何外部进程，可在 CI 离线运行。是重构前反射注入式 {@code KnowledgeGraphServiceContractTest}
 * 的等价替代（门面重构后 {@code restClient} 字段已下沉至 Provider 层，反射注入不可行）。
 */
class HttpKnowledgeGraphProviderContractTest {

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

    private HttpKnowledgeGraphProvider newProvider(KnowledgeGraphContract fallback) {
        return new HttpKnowledgeGraphProvider(baseUrl(), 2, 2, 0,
                ContractPaths.KG_HEALTH, 2, fallback);
    }

    private void startStub(String pathPlanBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(ContractPaths.KG_HEALTH, ex -> respond(ex, 200, "{}"));
        server.createContext(ContractPaths.KG_PATH_PLAN, ex -> respond(ex, 200, pathPlanBody));
        server.createContext(ContractPaths.KG_INGEST, ex -> respond(ex, 200,
                "{\"status\":\"ok\",\"synced_nodes\":5,\"upserted_nodes\":5,"
                        + "\"upserted_relations\":3,\"pruned_nodes\":0}"));
        server.createContext(ContractPaths.KG_SUBGRAPH, ex -> respond(ex, 200,
                "{\"status\":\"ok\",\"nodes\":[],\"edges\":[]}"));
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
        startStub("{}");
        KnowledgeGraphContract fallback = mock(KnowledgeGraphContract.class);
        assertTrue(newProvider(fallback).healthCheck().healthy());
    }

    @Test
    void planLearningPath_callsEngineEndpoint_andParsesEngineResult() throws Exception {
        startStub("{\"path\":[1,2,3],\"edges\":[],\"hops\":2}");
        KnowledgeGraphContract fallback = mock(KnowledgeGraphContract.class);
        HttpKnowledgeGraphProvider provider = newProvider(fallback);

        KnowledgeGraphContract.PathPlanResult r = provider.planLearningPath(
                new KnowledgeGraphContract.PathPlanRequest(1L, 3L, 5));

        assertTrue(r.found());
        assertEquals(KnowledgeGraphContract.Mode.ENGINE, r.mode());
        assertEquals(java.util.List.of(1L, 2L, 3L), r.path());
        assertEquals(2, r.hops());
        verifyNoInteractions(fallback); // 引擎已找到路径，不应回退
    }

    @Test
    void planLearningPath_engineReturnsEmptyPath_fallsBackToLocal() throws Exception {
        startStub("{\"path\":[],\"edges\":[]}"); // 引擎可达但未找到路径
        KnowledgeGraphContract fallback = mock(KnowledgeGraphContract.class);
        when(fallback.planLearningPath(any())).thenReturn(
                new KnowledgeGraphContract.PathPlanResult(1L, 3L, java.util.List.of(1L, 3L),
                        java.util.List.of(), 1, true, KnowledgeGraphContract.Mode.LOCAL, "本地兜底"));
        HttpKnowledgeGraphProvider provider = newProvider(fallback);

        KnowledgeGraphContract.PathPlanResult r = provider.planLearningPath(
                new KnowledgeGraphContract.PathPlanRequest(1L, 3L, 5));

        assertEquals(KnowledgeGraphContract.Mode.LOCAL, r.mode());
        verify(fallback).planLearningPath(any());
    }

    @Test
    void ingest_callsEngineEndpoint_andParsesEngineResult() throws Exception {
        startStub("{}");
        HttpKnowledgeGraphProvider provider = newProvider(mock(KnowledgeGraphContract.class));

        KnowledgeGraphContract.IngestResult r = provider.ingest(snapshot(1L));

        assertEquals(200, r.code());
        assertEquals(ContractPaths.MODE_ENGINE, r.mode());
        assertEquals(1L, r.courseId());
        assertEquals(5L, r.syncedNodes());
        assertEquals(3L, r.syncedRelations());
    }

    @Test
    void getSubGraph_callsEngineEndpoint_andParsesEngineResult() throws Exception {
        startStub("{}");
        HttpKnowledgeGraphProvider provider = newProvider(mock(KnowledgeGraphContract.class));

        KnowledgeGraphContract.SubGraphResult r = provider.getSubGraph(1L, 2);

        assertEquals(200, r.code());
        assertEquals(ContractPaths.MODE_ENGINE, r.mode());
        assertEquals(2, r.maxHops());
    }

    @Test
    void ingest_engineUnreachable_returns503Fallback() {
        // 指向必然不可达的端口，maxRetries=0 加速失败
        KnowledgeGraphContract fallback = mock(KnowledgeGraphContract.class);
        HttpKnowledgeGraphProvider provider = new HttpKnowledgeGraphProvider(
                "http://127.0.0.1:1", 1, 1, 0, ContractPaths.KG_HEALTH, 2, fallback);

        KnowledgeGraphContract.IngestResult r = provider.ingest(snapshot(1L));

        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(ContractPaths.MODE_UNAVAILABLE, r.mode());
    }

    @Test
    void healthCheck_engineDown_returnsFalse() {
        KnowledgeGraphContract fallback = mock(KnowledgeGraphContract.class);
        HttpKnowledgeGraphProvider provider = new HttpKnowledgeGraphProvider(
                "http://127.0.0.1:1", 1, 1, 0, ContractPaths.KG_HEALTH, 2, fallback);
        assertFalse(provider.healthCheck().healthy());
    }

    @Test
    void requestBodyCarriesContractFields() throws Exception {
        startStub("{\"path\":[1,2],\"edges\":[],\"hops\":1}");
        final boolean[] sawBody = {false};
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(ContractPaths.KG_HEALTH, ex -> respond(ex, 200, "{}"));
        server.createContext(ContractPaths.KG_PATH_PLAN, ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            sawBody[0] = body.contains(ContractPaths.F_SOURCE_ID)
                    && body.contains(ContractPaths.F_TARGET_ID)
                    && body.contains(ContractPaths.F_MAX_HOPS);
            respond(ex, 200, "{\"path\":[1,2],\"edges\":[],\"hops\":1}");
        });
        server.createContext(ContractPaths.KG_INGEST, ex -> respond(ex, 200, "{\"status\":\"ok\",\"synced_nodes\":0}"));
        server.createContext(ContractPaths.KG_SUBGRAPH, ex -> respond(ex, 200, "{\"status\":\"ok\",\"nodes\":[],\"edges\":[]}"));
        server.start();

        newProvider(mock(KnowledgeGraphContract.class))
                .planLearningPath(new KnowledgeGraphContract.PathPlanRequest(1L, 2L, 4));
        assertTrue(sawBody[0], "请求体应包含 source_id/target_id/max_hops 契约字段");
    }

    @Test
    void ingestRequestBodyCarriesSnapshotContractFields() throws Exception {
        final String[] seen = {null};
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(ContractPaths.KG_HEALTH, ex -> respond(ex, 200, "{}"));
        server.createContext(ContractPaths.KG_INGEST, ex -> {
            seen[0] = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            respond(ex, 200, "{\"status\":\"ok\",\"synced_nodes\":2,\"upserted_relations\":1}");
        });
        server.start();

        newProvider(mock(KnowledgeGraphContract.class)).ingest(snapshot(9L));

        String body = seen[0];
        assertNotNull(body, "摄取必须真的把快照发出去，而不是只带 course_id");
        for (String field : new String[]{"course_id", "nodes", "relations",
                "source_id", "target_id", "relation_type", "name", "content", "type"}) {
            assertTrue(body.contains("\"" + field + "\""), "请求体缺少契约字段 " + field + "：" + body);
        }
    }

    /** 与 Java 侧 buildSnapshot 同形的两节点一关系快照。 */
    private static KnowledgeGraphContract.IngestRequest snapshot(Long courseId) {
        return new KnowledgeGraphContract.IngestRequest(courseId,
                java.util.List.of(
                        new KnowledgeGraphContract.IngestNode(101L, courseId, "变量", "命名单元", "CONCEPT"),
                        new KnowledgeGraphContract.IngestNode(102L, courseId, "数据类型", "值分类", "CONCEPT")),
                java.util.List.of(
                        new KnowledgeGraphContract.IngestRelation(201L, 101L, 102L, "PREREQUISITE")));
    }
}
