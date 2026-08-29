package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.vo.KnowledgePathPlanVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 知识图谱服务契约测试（2026-08-13 起：图谱引擎由团队另一成员负责，本端仅保留接口 + HTTP 代理）。
 * <p>
 * 测试策略与 {@link RAGServiceImplTest} 对齐，规避「final 字段 + JDK 21 反射替换」不可行的约束：
 * <ol>
 *   <li><b>禁用（默认 {@code knowledge-graph.enabled=false}）</b>：不触碰 {@code RestClient}，
 *       验证消费侧本地降级契约——{@code planLearningPath} 本地 BFS 兜底、{@code ingest}/{@code getSubGraph}
 *       返回 {@code mode=LOCAL}、健康检查 false；</li>
 *   <li><b>启用 + Python 端可达</b>：用 JDK 内置 {@code HttpServer} 桩模拟 8001，验证请求正确代理到
 *       {@code /api/v1/graph/*} 且返回被解析为 {@code mode=ENGINE}；</li>
 *   <li><b>启用但 Python 端不可达</b>：验证 {@code planLearningPath} 回退本地 BFS，{@code ingest}/{@code getSubGraph}
 *       返回 503 占位（与 §11.7 降级策略一致）。</li>
 * </ol>
 */
class KnowledgeGraphServiceImplTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final String HEALTH = "/api/v1/graph/health";

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ============ 禁用（默认）：消费侧本地降级 ============

    @Test
    void disabled_healthCheck_false() {
        var svc = newDisabledService();
        assertFalse(svc.healthCheck());
    }

    @Test
    void disabled_planLearningPath_localBfs() {
        var kp = mock(KnowledgePointService.class);
        var rel = mock(KnowledgePointRelationService.class);
        var r12 = new KnowledgePointRelation();
        r12.setSourceId(1L);
        r12.setTargetId(2L);
        var r23 = new KnowledgePointRelation();
        r23.setSourceId(2L);
        r23.setTargetId(3L);
        when(rel.list()).thenReturn(List.of(r12, r23));
        stubListByIds(kp);

        var svc = new KnowledgeGraphServiceImpl(false, "http://localhost:8001", 1, 1, 0,
                HEALTH, 2, kp, rel);
        KnowledgePathPlanVO vo = svc.planLearningPath(1L, 3L, 5);

        assertTrue(vo.isFound());
        assertEquals("LOCAL", vo.getMode());
        assertEquals(List.of(1L, 2L, 3L),
                vo.getPath().stream().map(KnowledgePoint::getId).toList());
        assertEquals(2, vo.getHops());
    }

    @Test
    void disabled_planLearningPath_unreachableTarget_notFound() {
        var kp = mock(KnowledgePointService.class);
        var rel = mock(KnowledgePointRelationService.class);
        // 关系网络里没有通向 99 的路径
        var r12 = new KnowledgePointRelation();
        r12.setSourceId(1L);
        r12.setTargetId(2L);
        when(rel.list()).thenReturn(List.of(r12));
        stubListByIds(kp);

        var svc = new KnowledgeGraphServiceImpl(false, "http://localhost:8001", 1, 1, 0,
                HEALTH, 2, kp, rel);
        KnowledgePathPlanVO vo = svc.planLearningPath(1L, 99L, 3);

        assertFalse(vo.isFound());
        assertEquals("LOCAL", vo.getMode());
    }

    @Test
    void disabled_ingest_localMode() throws Exception {
        var kp = mock(KnowledgePointService.class);
        var rel = mock(KnowledgePointRelationService.class);
        when(kp.countByCourse(1L)).thenReturn(7L);

        var svc = new KnowledgeGraphServiceImpl(false, "http://localhost:8001", 1, 1, 0,
                HEALTH, 2, kp, rel);
        Map<?, ?> m = OM.readValue(svc.ingest(1L), Map.class);

        assertEquals(200, m.get("code"));
        assertEquals("LOCAL", m.get("mode"));
        assertEquals(7, m.get("syncedPoints"));
    }

    @Test
    void disabled_subGraph_localNodes() throws Exception {
        var kp = mock(KnowledgePointService.class);
        var rel = mock(KnowledgePointRelationService.class);
        when(kp.listByCourse(1L)).thenReturn(List.of(new KnowledgePoint()));
        when(rel.listByCourse(1L)).thenReturn(List.of(new KnowledgePointRelation()));

        var svc = new KnowledgeGraphServiceImpl(false, "http://localhost:8001", 1, 1, 0,
                HEALTH, 2, kp, rel);
        Map<?, ?> m = OM.readValue(svc.getSubGraph(1L, 2), Map.class);

        assertEquals("LOCAL", m.get("mode"));
        assertNotNull(m.get("nodes"));
        assertNotNull(m.get("edges"));
    }

    // ============ 启用 + Python 端可达（JDK HttpServer 桩） ============

    @Test
    void enabled_proxiesPathPlan() throws Exception {
        startStub();
        var kp = mock(KnowledgePointService.class);
        var rel = mock(KnowledgePointRelationService.class);
        stubListByIds(kp);

        var svc = newEnabledService(kp, rel);
        assertTrue(svc.healthCheck());

        KnowledgePathPlanVO vo = svc.planLearningPath(1L, 4L, 3);
        assertTrue(vo.isFound());
        assertEquals("ENGINE", vo.getMode());
        assertEquals(List.of(1L, 2L, 3L, 4L),
                vo.getPath().stream().map(KnowledgePoint::getId).toList());
    }

    @Test
    void enabled_proxiesIngestAndSubgraph() throws Exception {
        startStub();
        var svc = newEnabledService(mock(KnowledgePointService.class), mock(KnowledgePointRelationService.class));

        Map<?, ?> ing = OM.readValue(svc.ingest(1L), Map.class);
        assertEquals("ok", ing.get("status"));

        Map<?, ?> sub = OM.readValue(svc.getSubGraph(1L, 2), Map.class);
        assertEquals("ok", sub.get("status"));
    }

    // ============ 启用但 Python 端不可达：降级 ============

    @Test
    void enabled_butServiceDown_planFallsBackToLocal() {
        var kp = mock(KnowledgePointService.class);
        var rel = mock(KnowledgePointRelationService.class);
        var r12 = new KnowledgePointRelation();
        r12.setSourceId(1L);
        r12.setTargetId(2L);
        when(rel.list()).thenReturn(List.of(r12));
        stubListByIds(kp);

        // 指向必然不可达的端口（:1），maxRetries=0 加速失败
        var svc = new KnowledgeGraphServiceImpl(true, "http://127.0.0.1:1", 1, 1, 0,
                HEALTH, 2, kp, rel);
        KnowledgePathPlanVO vo = svc.planLearningPath(1L, 2L, 2);

        assertTrue(vo.isFound());
        assertEquals("LOCAL", vo.getMode());
    }

    @Test
    void enabled_butServiceDown_ingestAndSubgraphReturn503() {
        var svc = new KnowledgeGraphServiceImpl(true, "http://127.0.0.1:1", 1, 1, 0,
                HEALTH, 2, mock(KnowledgePointService.class), mock(KnowledgePointRelationService.class));

        assertTrue(svc.ingest(1L).contains("\"code\":503"));
        assertTrue(svc.getSubGraph(1L, 2).contains("\"code\":503"));
        assertFalse(svc.healthCheck());
    }

    // ============ helpers ============

    private void stubListByIds(KnowledgePointService kp) {
        when(kp.listByIds(anyList())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            java.util.Collection<Long> ids = inv.getArgument(0);
            return ids.stream().map(id -> {
                KnowledgePoint p = new KnowledgePoint();
                p.setId(id);
                p.setName("KP-" + id);
                return p;
            }).toList();
        });
    }

    private KnowledgeGraphServiceImpl newDisabledService() {
        return new KnowledgeGraphServiceImpl(false, "http://localhost:8001", 1, 1, 0,
                HEALTH, 2, mock(KnowledgePointService.class), mock(KnowledgePointRelationService.class));
    }

    private KnowledgeGraphServiceImpl newEnabledService(KnowledgePointService kp,
                                                        KnowledgePointRelationService rel) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new KnowledgeGraphServiceImpl(true, baseUrl, 2, 2, 0, HEALTH, 2, kp, rel);
    }

    private void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        String path = exchange.getRequestURI().getPath();
        String body;
        switch (path) {
            case HEALTH -> body = "{}";
            case "/api/v1/graph/path/plan" ->
                    body = "{\"path\":[1,2,3,4],\"edges\":[],\"hops\":3}";
            case "/api/v1/graph/ingest" -> body = "{\"status\":\"ok\"}";
            case "/api/v1/graph/subgraph" -> body = "{\"status\":\"ok\"}";
            default -> body = "{}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
