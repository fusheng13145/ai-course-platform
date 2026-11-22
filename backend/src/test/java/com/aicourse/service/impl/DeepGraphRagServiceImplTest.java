package com.aicourse.service.impl;

import com.aicourse.integration.ContractPaths;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.integration.deepgraph.DeepGraphRagContract.CourseGraphEdge;
import com.aicourse.integration.deepgraph.DeepGraphRagContract.CourseGraphNode;
import com.aicourse.integration.deepgraph.DeepGraphRagContract.DeepGraphCourseGraphData;
import com.aicourse.integration.deepgraph.DeepGraphRagContract.DeepGraphRetrievalResult;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeepGraph RAG 门面 {@link DeepGraphRagServiceImpl} 测试（桩服务器，与 RAGServiceImplTest 同口径）：
 * enabled=false → Disabled 占位（503）；enabled=true → HTTP 透传 + JSON 序列化；
 * healthJson 的 enabled/reachable 双读数。
 */
class DeepGraphRagServiceImplTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void startStub(int retrieveStatus, String retrieveBody,
                           int courseGraphStatus, String courseGraphBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(ContractPaths.DEEPGRAPH_HEALTH, ex -> respond(ex, 200, "{}"));
        server.createContext(ContractPaths.DEEPGRAPH_RETRIEVE, ex -> respond(ex, retrieveStatus, retrieveBody));
        server.createContext(ContractPaths.DEEPGRAPH_INGEST, ex -> respond(ex, 200,
                "{\"success\":true,\"stored_as\":\"course1_d.txt\",\"chunk_count\":7}"));
        server.createContext(ContractPaths.DEEPGRAPH_DOCUMENTS, ex -> {
            if ("DELETE".equals(ex.getRequestMethod())) {
                respond(ex, 200, "{\"success\":true}");
            } else {
                respond(ex, 200, "{\"documents\":[]}");
            }
        });
        server.createContext(ContractPaths.DEEPGRAPH_CHAPTER_GRAPH, ex -> respond(ex, 200,
                "{\"success\":true,\"nodes\":3,\"edges\":2,\"relations\":[]}"));
        server.createContext(ContractPaths.DEEPGRAPH_LEARNING_PATH, ex -> respond(ex, 200,
                "{\"target\":\"t\",\"path_text\":\"## p\"}"));
        server.createContext(ContractPaths.DEEPGRAPH_COURSE_GRAPH, ex -> respond(ex, courseGraphStatus,
                courseGraphBody));
        server.start();
    }

    private void startStub(int retrieveStatus, String retrieveBody) throws IOException {
        startStub(retrieveStatus, retrieveBody, 200,
                "{\"chapters\": [\"第1章\"], \"nodes\": [{\"name\": \"TCP\", \"type\": \"CONCEPT\", \"evidence\": \"\"}, {\"name\": \"三次握手\", \"type\": \"CONCEPT\", \"evidence\": \"握手建立连接\"}, {\"name\": \"第1章 概述\", \"type\": \"CHAPTER\", \"evidence\": \"\"}], \"edges\": [{\"source\": \"TCP\", \"target\": \"三次握手\", \"relation\": \"包含\", \"evidence\": \"TCP 三次握手同步序号\", \"chapter\": \"第1章\"}], \"mode\": \"ENGINE\"}");
    }

    private void respond(com.sun.net.httpserver.HttpExchange ex, int code, String body) throws IOException {
        ex.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    @DisplayName("enabled=false：全方法 503 占位；healthJson 双读数 enabled=false reachable=false")
    void disabledProvider_placeholder() {
        DeepGraphRagServiceImpl service = new DeepGraphRagServiceImpl(
                false, "http://localhost:8501", 5, 60, 1, ContractPaths.DEEPGRAPH_HEALTH, "",
                null, null);

        assertFalse(service.healthCheck());
        DeepGraphRetrievalResult r = service.retrieveTyped("q", 1L, 3);
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());

        String json = service.retrieve("q", 1L, 3);
        assertTrue(json.contains("\"code\":503"), "降级 JSON 必须带 code=503: " + json);

        assertTrue(service.ingest(1L, "d", "c").contains("503"));
        assertTrue(service.listDocuments(1L).contains("503"));
        assertTrue(service.deleteDocument(1L, "d").contains("503"));
        assertTrue(service.chapterGraph(1L, "d", "ch", null).contains("503"));
        assertTrue(service.learningPath("t", 1L, 3, true).contains("503"));

        String health = service.healthJson();
        assertTrue(health.contains("\"enabled\":false"));
        assertTrue(health.contains("\"reachable\":false"));
    }

    @Test
    @DisplayName("enabled=true：检索成功/降级透传、各方法 JSON 序列化与参数透传")
    void enabledProvider_passThrough() throws IOException {
        startStub(200, "{\"mode\":\"ENGINE\",\"results\":[],\"graph_context\":\"- a (相关) b\"}");
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        DeepGraphRagServiceImpl service = new DeepGraphRagServiceImpl(
                true, base, 5, 60, 0, ContractPaths.DEEPGRAPH_HEALTH, "",
                null, null);

        assertTrue(service.healthCheck());
        assertTrue(service.healthJson().contains("\"reachable\":true"));

        String retrieveJson = service.retrieve("q", 1L, 3);
        assertTrue(retrieveJson.contains("graphContext"), "契约记录以 camelCase 序列化: " + retrieveJson);

        DeepGraphRetrievalResult typed = service.retrieveTyped("q", 1L, 3);
        assertTrue(typed.engineReady());

        assertTrue(service.ingest(1L, "d", "c").contains("chunkCount\":7"));
        assertTrue(service.listDocuments(1L).contains("documents"));
        assertTrue(service.deleteDocument(1L, "d").contains("success\":true"));
        assertTrue(service.chapterGraph(1L, "d", "ch", null).contains("nodes\":3"));
        assertTrue(service.learningPath("t", 1L, 3, true).contains("pathText"));

        // courseId 为 null 时同样透传（成功路径 code 为 null，用 mode 断言）
        assertTrue(service.retrieve("q", null, 3).contains("\"mode\":\"ENGINE\""));
    }

    @Test
    @DisplayName("enabled=true：HTTP 503 检索 -> 契约降级结果")
    void enabledProvider_http503_degrades() throws IOException {
        startStub(503, "{\"code\":503,\"mode\":\"UNAVAILABLE\",\"error\":\"x\"}");
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        DeepGraphRagServiceImpl service = new DeepGraphRagServiceImpl(
                true, base, 5, 60, 0, ContractPaths.DEEPGRAPH_HEALTH, "",
                null, null);

        DeepGraphRetrievalResult r = service.retrieveTyped("q", 1L, 3);
        assertFalse(r.engineReady());
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), r.code());
    }

    // ===================== 课程图谱落库（syncCourseKnowledgeGraph） =====================

    private DeepGraphCourseGraphData engineGraph() {
        return new DeepGraphCourseGraphData(List.of("第1章"),
                List.of(new CourseGraphNode("TCP", "CONCEPT", ""),
                        new CourseGraphNode("三次握手", "CONCEPT", "握手建立连接"),
                        new CourseGraphNode("第1章 概述", "CHAPTER", "")),
                List.of(new CourseGraphEdge("TCP", "三次握手", "包含", "TCP 三次握手同步序号", "第1章"),
                        new CourseGraphEdge("TCP", "三次握手", "包含", "重复边", "第1章")),
                ContractPaths.MODE_ENGINE, null, null);
    }

    @Test
    @DisplayName("sync：聚合降级(UNAVAILABLE) -> 原样透传 503，不写库")
    void sync_engineUnavailable_passesThrough() throws IOException {
        startStub(200, "{}", 503, "{\"code\":503,\"mode\":\"UNAVAILABLE\",\"error\":\"x\"}");
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        KnowledgePointService pointService = org.mockito.Mockito.mock(KnowledgePointService.class);
        KnowledgePointRelationService relationService = org.mockito.Mockito.mock(KnowledgePointRelationService.class);
        DeepGraphRagServiceImpl service = new DeepGraphRagServiceImpl(
                true, base, 5, 60, 0, ContractPaths.DEEPGRAPH_HEALTH, "", pointService, relationService);

        String json = service.syncCourseKnowledgeGraph(4178L, "教材");
        assertTrue(json.contains("\"mode\":\"UNAVAILABLE\""), json);
        org.mockito.Mockito.verifyNoInteractions(pointService, relationService);
    }

    @Test
    @DisplayName("sync：引擎聚合 ENGINE -> 实体按名幂等建点、三元组判重落库")
    void sync_engineGraph_persistsIdempotently() throws IOException {
        startStub(200, "{}");
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        KnowledgePointService pointService = org.mockito.Mockito.mock(KnowledgePointService.class);
        KnowledgePointRelationService relationService = org.mockito.Mockito.mock(KnowledgePointRelationService.class);

        List<KnowledgePoint> existing = List.of(point(7000083L, "TCP"));
        org.mockito.Mockito.when(pointService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgePoint>>any())).thenReturn(existing);
        org.mockito.Mockito.when(relationService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgePointRelation>>any())).thenReturn(List.of());
        org.mockito.Mockito.doAnswer(inv -> {
            List<KnowledgePoint> savedPoints = inv.getArgument(0);
            for (KnowledgePoint point : savedPoints) {
                point.setId(7000084L + point.getName().length());
            }
            return true;
        }).when(pointService).saveBatch(org.mockito.ArgumentMatchers.anyList());

        DeepGraphRagServiceImpl service = new DeepGraphRagServiceImpl(
                true, base, 5, 60, 0, ContractPaths.DEEPGRAPH_HEALTH, "", pointService, relationService);

        String json = service.syncCourseKnowledgeGraph(4178L, "教材");
        assertTrue(json.contains("addedPoints"), json);

        ArgumentCaptor<List<KnowledgePoint>> pointCaptor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(pointService).saveBatch(pointCaptor.capture());
        List<KnowledgePoint> saved = pointCaptor.getValue();
        assertEquals(2, saved.size(), "TCP 已存在，仅新建 三次握手/第1章概述");
        assertEquals("CHAPTER", saved.get(1).getPointType());

        ArgumentCaptor<List<KnowledgePointRelation>> relCaptor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(relationService).saveBatch(relCaptor.capture());
        // 双端归属（2026-11-22）：1 条语义边 + 2 条归属边（TCP/三次握手 → 第1章 概述）；
        // 重复三元组已在 existingTriples 判重跳过
        assertEquals(3, relCaptor.getValue().size(), "1 语义边 + 2 归属边（双端挂靠）");
        assertEquals("CONTAINS", relCaptor.getValue().get(0).getRelationType());
        assertEquals("TCP 三次握手同步序号", relCaptor.getValue().get(0).getDescription());
        assertEquals("BELONGS_TO", relCaptor.getValue().get(1).getRelationType());
        assertEquals("BELONGS_TO", relCaptor.getValue().get(2).getRelationType());
    }

    @Test
    @DisplayName("sync：引擎原始长名引用可命中已截断节点，不产生孤立点/丢边")
    void sync_longNameEdgeResolvesToTruncatedPoint() throws IOException {
        String longName = "L".repeat(121);
        startStub(200, "{}", 200,
                "{\"chapters\": [], \"nodes\": ["
                        + "{\"name\": \"TCP\", \"type\": \"CONCEPT\", \"evidence\": \"\"},"
                        + "{\"name\": \"" + longName + "\", \"type\": \"CONCEPT\", \"evidence\": \"长\"}], \"edges\": ["
                        + "{\"source\": \"" + longName + "\", \"target\": \"TCP\", \"relation\": \"相关\", \"evidence\": \"e\"}"
                        + "], \"mode\": \"ENGINE\"}");
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        KnowledgePointService pointService = org.mockito.Mockito.mock(KnowledgePointService.class);
        KnowledgePointRelationService relationService = org.mockito.Mockito.mock(KnowledgePointRelationService.class);
        org.mockito.Mockito.when(pointService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgePoint>>any()))
                .thenReturn(List.of(point(1L, "TCP")));
        org.mockito.Mockito.when(relationService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgePointRelation>>any()))
                .thenReturn(List.of());
        org.mockito.Mockito.doAnswer(inv -> {
            for (KnowledgePoint p : inv.<List<KnowledgePoint>>getArgument(0)) {
                p.setId(3L);
            }
            return true;
        }).when(pointService).saveBatch(org.mockito.ArgumentMatchers.anyList());

        DeepGraphRagServiceImpl service = new DeepGraphRagServiceImpl(
                true, base, 5, 60, 0, ContractPaths.DEEPGRAPH_HEALTH, "", pointService, relationService);
        String json = service.syncCourseKnowledgeGraph(4178L, "教材");

        assertTrue(json.contains("\"addedPoints\":1"), json);
        assertTrue(json.contains("\"addedRelations\":1"), "原始长名边不能因截断而丢失: " + json);
        ArgumentCaptor<List<KnowledgePointRelation>> relCaptor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(relationService).saveBatch(relCaptor.capture());
        assertEquals(3L, relCaptor.getValue().get(0).getSourceId());
        assertEquals(1L, relCaptor.getValue().get(0).getTargetId());
        assertEquals("RELATED", relCaptor.getValue().get(0).getRelationType());
    }

    @Test
    @DisplayName("sync：disabled 门面 -> 503 JSON")
    void sync_disabled_facade_returns503() {
        KnowledgePointService pointService = org.mockito.Mockito.mock(KnowledgePointService.class);
        KnowledgePointRelationService relationService = org.mockito.Mockito.mock(KnowledgePointRelationService.class);
        DeepGraphRagServiceImpl service = new DeepGraphRagServiceImpl(
                false, "http://localhost:8501", 5, 60, 1, ContractPaths.DEEPGRAPH_HEALTH, "", pointService, relationService);
        String json = service.syncCourseKnowledgeGraph(1L, "教材");
        assertTrue(json.contains("503"), json);
        org.mockito.Mockito.verifyNoInteractions(pointService, relationService);
    }

    @Test
    @DisplayName("sync：空名/自环/空关系 等非法项全部跳过")
    void sync_edgeCases_skipsInvalid() throws IOException {
        startStub(200, "{}", 200,
                "{\"chapters\": [\"第1章\"], \"nodes\": [{\"name\": \"  \", \"type\": \"CONCEPT\", \"evidence\": \"\"}, "
                        + "{\"name\": \"A\", \"type\": \"CONCEPT\", \"evidence\": \"\"}, "
                        + "{\"name\": \"B\", \"type\": \"CONCEPT\", \"evidence\": \"\"}], "
                        + "\"edges\": [{\"source\": \"A\", \"target\": \"A\", \"relation\": \"\", \"evidence\": \"\", \"chapter\": \"\"}, "
                        + "{\"source\": \"  \", \"target\": \"A\", \"relation\": \"相关\", \"evidence\": \"\", \"chapter\": \"\"}, "
                        + "{\"source\": \"A\", \"target\": \"B\", \"relation\": \"  \", \"evidence\": \"\", \"chapter\": \"\"}], "
                        + "\"mode\": \"ENGINE\"}");
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        KnowledgePointService pointService = org.mockito.Mockito.mock(KnowledgePointService.class);
        KnowledgePointRelationService relationService = org.mockito.Mockito.mock(KnowledgePointRelationService.class);
        org.mockito.Mockito.when(pointService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgePoint>>any())).thenReturn(List.of());
        org.mockito.Mockito.when(relationService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgePointRelation>>any())).thenReturn(List.of());
        org.mockito.Mockito.doAnswer(inv -> {
            List<KnowledgePoint> savedPoints = inv.getArgument(0);
            java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong(1);
            for (KnowledgePoint point : savedPoints) {
                point.setId(seq.getAndIncrement());
            }
            return true;
        }).when(pointService).saveBatch(org.mockito.ArgumentMatchers.anyList());

        DeepGraphRagServiceImpl service = new DeepGraphRagServiceImpl(
                true, base, 5, 60, 0, ContractPaths.DEEPGRAPH_HEALTH, "", pointService, relationService);
        String json = service.syncCourseKnowledgeGraph(4178L, "教材");
        // 空名节点不建点（A/B 落库）；自环与空名边跳过；空关系名兜底为「相关」
        assertTrue(json.contains("\"addedPoints\":2"), json);
        assertTrue(json.contains("\"addedRelations\":1"), json);
        assertTrue(json.contains("\"skippedRelations\":2"), json);
    }

    @Test
    @DisplayName("sync：聚合缺 nodes/edges 或 mode 非 ENGINE -> 原样透传，不写库")
    void sync_incompletePayload_passesThrough() throws IOException {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(ContractPaths.DEEPGRAPH_HEALTH, ex -> respond(ex, 200, "{}"));
        server.createContext(ContractPaths.DEEPGRAPH_COURSE_GRAPH, ex -> {
            int call = calls.incrementAndGet();
            String body = call == 1
                    ? "{\"mode\": \"LEGACY\"}"                                  // mode 非 ENGINE
                    : call == 2
                        ? "{\"chapters\": [], \"mode\": \"ENGINE\"}"            // nodes 缺省
                        : "{\"chapters\": [], \"nodes\": [], \"mode\": \"ENGINE\"}"; // edges 缺省
            respond(ex, 200, body);
        });
        server.start();
        KnowledgePointService pointService = org.mockito.Mockito.mock(KnowledgePointService.class);
        KnowledgePointRelationService relationService = org.mockito.Mockito.mock(KnowledgePointRelationService.class);
        DeepGraphRagServiceImpl service = new DeepGraphRagServiceImpl(
                true, "http://127.0.0.1:" + server.getAddress().getPort(), 5, 60, 0,
                ContractPaths.DEEPGRAPH_HEALTH, "", pointService, relationService);

        assertTrue(service.syncCourseKnowledgeGraph(1L, "教材").contains("LEGACY"));
        // 缺省 nodes/edges 经 provider 解析为空列表（非 null）-> 走空图落库分支，摘要为 0 且不写批
        assertTrue(service.syncCourseKnowledgeGraph(1L, "教材").contains("\"addedPoints\":0"));
        assertTrue(service.syncCourseKnowledgeGraph(1L, "教材").contains("\"addedRelations\":0"));
        org.mockito.Mockito.verify(pointService, org.mockito.Mockito.never())
                .saveBatch(org.mockito.ArgumentMatchers.anyList());
        org.mockito.Mockito.verify(relationService, org.mockito.Mockito.never())
                .saveBatch(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("sync：无可落库关系时跳过 saveBatch（空归属/未知端点全跳过）")
    void sync_noPersistableRelations_skipsSaveBatch() throws IOException {
        startStub(200, "{}", 200,
                "{\"chapters\": [\"附录B\"], \"nodes\": [{\"name\": \"X\", \"type\": \"CONCEPT\", \"evidence\": \"\"}], "
                        + "\"edges\": [{\"source\": \"Q\", \"target\": \"W\", \"relation\": \"r\", \"chapter\": \"附录B\"}], "
                        + "\"mode\": \"ENGINE\"}");
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        KnowledgePointService pointService = org.mockito.Mockito.mock(KnowledgePointService.class);
        KnowledgePointRelationService relationService = org.mockito.Mockito.mock(KnowledgePointRelationService.class);

        DeepGraphRagServiceImpl service = new DeepGraphRagServiceImpl(
                true, base, 5, 60, 0, ContractPaths.DEEPGRAPH_HEALTH, "", pointService, relationService);
        String json = service.syncCourseKnowledgeGraph(4178L, "教材");

        assertTrue(json.contains("\"addedPoints\":1"), json);
        assertTrue(json.contains("\"addedRelations\":0"), json);
        assertTrue(json.contains("\"skippedRelations\":1"), json);
        assertTrue(json.contains("\"addedMembership\":0"), json);
        // 实体 Q/W 均未建点 -> 归属边无处可挂；章节「附录B」缺省自建
        org.mockito.Mockito.verify(relationService, org.mockito.Mockito.never())
                .saveBatch(org.mockito.ArgumentMatchers.anyList());
        org.mockito.Mockito.verify(pointService).save(org.mockito.ArgumentMatchers.argThat(
                p -> p != null && "附录B".equals(p.getName()) && "CHAPTER".equals(p.getPointType())));
    }

    @Test
    @DisplayName("sync：成员归属分支——章号兜底、章节自建、双端去重、字段缺省兜底")
    void sync_membershipBranches() throws IOException {
        String longName = "L".repeat(121);
        String body = "{\"chapters\": null, \"nodes\": ["
                + "{\"name\": \"TCP\", \"type\": \"CONCEPT\"},"
                + "{\"name\": \"第2章 交换\", \"type\": \"CHAPTER\", \"evidence\": \"\"},"
                + "{\"name\": null, \"type\": \"CONCEPT\", \"evidence\": \"\"},"
                + "{\"name\": \"" + longName + "\", \"type\": \"CONCEPT\", \"evidence\": \"长\"}], \"edges\": ["
                + "{\"source\": \"TCP\", \"target\": \"第2章 交换\", \"relation\": \"已知\", \"evidence\": \"e1\", \"chapter\": \"第2章 交换\"},"
                + "{\"source\": \"TCP\", \"target\": \"第9章 其他\", \"relation\": null, \"evidence\": \"ev9\", \"chapter\": null},"
                + "{\"source\": \"TCP\", \"target\": \"第9章 其他\", \"relation\": \"r2\", \"chapter\": null},"
                + "{\"source\": \"第2章 交换\", \"target\": \"TCP\", \"relation\": \"包含章节\", \"evidence\": \"e4\", \"chapter\": \"文档标题\"},"
                + "{\"source\": \"TCP\", \"target\": \"TCP\", \"relation\": \"自环\", \"evidence\": \"\", \"chapter\": \"第2章 交换\"},"
                + "{\"source\": \"  \", \"target\": \"TCP\", \"relation\": \"x\", \"chapter\": \"第9章 其他\"},"
                + "{\"target\": \"TCP\", \"relation\": \"x\", \"chapter\": \"第9章 其他\"},"
                + "{\"source\": \"TCP\", \"target\": \"第2章 交换\", \"relation\": \"x\", \"chapter\": \"第2章\"},"
                + "{\"source\": \"TCP\", \"target\": \"第2章 交换\", \"relation\": \"x2\", \"chapter\": \"附录A\"},"
                + "{\"source\": \"第9章 其他\", \"target\": \"TCP\", \"relation\": \"s\", \"chapter\": \"第9章 其他\"}"
                + "], \"mode\": \"ENGINE\"}";
        startStub(200, "{}", 200, body);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        KnowledgePointService pointService = org.mockito.Mockito.mock(KnowledgePointService.class);
        KnowledgePointRelationService relationService = org.mockito.Mockito.mock(KnowledgePointRelationService.class);

        // 已有实体/章节（第9章在前，验证章号兜底需先命中不匹配候选再命中目标章节）
        org.mockito.Mockito.when(pointService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgePoint>>any()))
                .thenReturn(List.of(point(1L, "TCP"), point(4L, "第9章 其他"), point(2L, "第2章 交换")));
        org.mockito.Mockito.when(relationService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgePointRelation>>any()))
                .thenReturn(List.of(rel(1L, 2L, "已知")));
        org.mockito.Mockito.doAnswer(inv -> {
            for (KnowledgePoint p : inv.<List<KnowledgePoint>>getArgument(0)) {
                p.setId(p.getName().length() > 120 ? 3L : 99L);
            }
            return true;
        }).when(pointService).saveBatch(org.mockito.ArgumentMatchers.anyList());
        org.mockito.Mockito.doAnswer(inv -> {
            inv.<KnowledgePoint>getArgument(0).setId(9L);
            return true;
        }).when(pointService).save(org.mockito.ArgumentMatchers.any(KnowledgePoint.class));

        DeepGraphRagServiceImpl service = new DeepGraphRagServiceImpl(
                true, base, 5, 60, 0, ContractPaths.DEEPGRAPH_HEALTH, "", pointService, relationService);
        String json = service.syncCourseKnowledgeGraph(4178L, "教材");

        // 6 条语义边（含重复三元组/自环/空名跳过）+ 4 条归属边（TCP→第2章、TCP→第9章、TCP/交换→附录A）
        assertTrue(json.contains("\"addedPoints\":1"), json);
        assertTrue(json.contains("\"addedRelations\":10"), json);
        assertTrue(json.contains("\"skippedRelations\":4"), json);
        assertTrue(json.contains("\"addedMembership\":4"), json);
        assertTrue(json.contains("\"chapters\":0"), json);

        ArgumentCaptor<List<KnowledgePoint>> pointCaptor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(pointService).saveBatch(pointCaptor.capture());
        assertEquals(120, pointCaptor.getValue().get(0).getName().length(), "超长名截断到 120");

        org.mockito.Mockito.verify(pointService).save(org.mockito.ArgumentMatchers.argThat(
                p -> p != null && "附录A".equals(p.getName()) && "CHAPTER".equals(p.getPointType())));

        ArgumentCaptor<List<KnowledgePointRelation>> relCaptor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(relationService).saveBatch(relCaptor.capture());
        List<KnowledgePointRelation> rels = relCaptor.getValue();
        assertEquals(10, rels.size());
        assertEquals("RELATED", rels.get(0).getRelationType(), "relation 缺省兜底为 RELATED");
        assertEquals("ev9", rels.get(0).getDescription());
        assertEquals("", rels.get(1).getDescription(), "evidence 缺省落空描述");
        assertEquals("CONTAINS_CHAPTER", rels.get(2).getRelationType());
        assertEquals("S", rels.get(5).getRelationType(), "未知关系按规范编码大写落库");
        assertEquals("BELONGS_TO", rels.get(6).getRelationType());
        assertEquals(2L, rels.get(6).getTargetId());
        assertEquals(4L, rels.get(7).getTargetId());
        assertEquals(9L, rels.get(8).getTargetId(), "兜底落「附录A」自建章节");
        assertEquals(9L, rels.get(9).getTargetId());
    }

    private static KnowledgePointRelation rel(Long sourceId, Long targetId, String type) {
        KnowledgePointRelation relation = new KnowledgePointRelation();
        relation.setSourceId(sourceId);
        relation.setTargetId(targetId);
        relation.setRelationType(type);
        return relation;
    }

    private static KnowledgePoint point(Long id, String name) {
        KnowledgePoint point = new KnowledgePoint();
        point.setId(id);
        point.setName(name);
        point.setCourseId(4178L);
        return point;
    }
}