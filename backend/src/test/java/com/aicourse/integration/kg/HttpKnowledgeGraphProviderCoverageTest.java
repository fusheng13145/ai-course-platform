package com.aicourse.integration.kg;

import com.aicourse.integration.ContractPaths;
import com.aicourse.integration.kg.KnowledgeGraphContract.IngestNode;
import com.aicourse.integration.kg.KnowledgeGraphContract.IngestRelation;
import com.aicourse.integration.kg.KnowledgeGraphContract.IngestResult;
import com.aicourse.integration.kg.KnowledgeGraphContract.PathPlanResult;
import com.aicourse.integration.kg.KnowledgeGraphContract.SubGraphResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HttpKnowledgeGraphProvider 解析层覆盖率补测（I-04 第七批）。
 *
 * <p>不启动 HTTP 服务，直接反射调用私有解析方法，覆盖契约字段的根路径/嵌套路径、
 * 缺字段、类型错误、空响应、抓包异常与载荷转换。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HttpKnowledgeGraphProviderCoverageTest {

    @Mock private KnowledgeGraphContract localFallback;
    private HttpKnowledgeGraphProvider provider;

    @BeforeEach
    void setUp() {
        provider = new HttpKnowledgeGraphProvider(
                "http://localhost:8001", 1, 1, 0, "/health", 3, localFallback);
    }

    private PathPlanResult parsePlan(String raw) {
        return ReflectionTestUtils.invokeMethod(provider, "parseEnginePlan", raw, 1L, 2L);
    }

    private IngestResult parseIngest(String raw) {
        return ReflectionTestUtils.invokeMethod(provider, "parseIngest", raw, 4178L);
    }

    private SubGraphResult parseSubGraph(String raw) {
        return ReflectionTestUtils.invokeMethod(provider, "parseSubGraph", raw, 4178L, 2);
    }

    @Test
    @DisplayName("parseEnginePlan：根路径、data 嵌套路径、缺路径、空数组均按契约处理")
    void parseEnginePlan_pathShapes() {
        assertNull(parsePlan(null));
        assertNull(parsePlan("   "));
        assertNull(parsePlan("{}"));
        assertNull(parsePlan("{\"data\":{}}"));
        assertNull(parsePlan("{\"path\":{}}"));
        assertNull(parsePlan("{\"path\":[]}"));

        PathPlanResult root = parsePlan("{\"path\":[1,2],\"edges\":[]}");
        assertNotNull(root);
        assertTrue(root.found());
        assertEquals(List.of(1L, 2L), root.path());
        assertEquals(1, root.hops());

        PathPlanResult nested = parsePlan("{\"data\":{\"path\":[3,4,5]}}");
        assertNotNull(nested);
        assertEquals(List.of(3L, 4L, 5L), nested.path());
        assertEquals(2, nested.hops());
    }

    @Test
    @DisplayName("parseEnginePlan：边字段缺失时对应值为 null，坏 JSON 返回 null")
    void parseEnginePlan_edges() {
        PathPlanResult result = parsePlan("{\"path\":[1,2],\"edges\":["
                + "{\"source_id\":1,\"target_id\":2},{\"relation_type\":\"PREREQUISITE\"}]}");
        assertNotNull(result);
        assertEquals(2, result.edges().size());
        assertNull(result.edges().get(0).relationType());
        assertNull(result.edges().get(1).sourceId());
        assertNull(result.edges().get(1).targetId());

        assertNull(parsePlan("not-json"));
    }

    @Test
    @DisplayName("parseIngest：两种同步字段命名、缺字段默认 0、空响应 503")
    void parseIngest_fields() {
        IngestResult blank = parseIngest("");
        assertEquals(ContractPaths.CODE_UNAVAILABLE, blank.code());

        IngestResult nodes = parseIngest("{\"status\":\"ok\",\"synced_nodes\":5,\"upserted_relations\":3}");
        assertEquals(200, nodes.code());
        assertEquals("ok", nodes.status());
        assertEquals(5L, nodes.syncedNodes());
        assertEquals(3L, nodes.syncedRelations());

        IngestResult points = parseIngest("{\"syncedPoints\":7}");
        assertEquals(7L, points.syncedNodes());
        assertEquals(0L, points.syncedRelations());
        assertNull(points.status());

        IngestResult broken = parseIngest("not-json");
        assertEquals(ContractPaths.CODE_UNAVAILABLE, broken.code());
    }

    @Test
    @DisplayName("parseSubGraph：节点/边数组解析，缺字段为空数组，坏 JSON 返回 503")
    void parseSubGraph_fields() {
        SubGraphResult blank = parseSubGraph(null);
        assertEquals(ContractPaths.CODE_UNAVAILABLE, blank.code());
        assertTrue(blank.maxHops() == 2);

        SubGraphResult empty = parseSubGraph("{}");
        assertEquals(200, empty.code());
        assertTrue(empty.nodes().isEmpty());
        assertTrue(empty.edges().isEmpty());

        SubGraphResult full = parseSubGraph("{\"status\":\"ok\",\"nodes\":[{\"id\":1}],\"edges\":[{\"source\":2}]}");
        assertEquals(1, full.nodes().size());
        assertEquals(1, full.edges().size());
        assertEquals("ok", full.status());

        SubGraphResult broken = parseSubGraph("not-json");
        assertEquals(ContractPaths.CODE_UNAVAILABLE, broken.code());
    }

    @Test
    @DisplayName("ingest/getSubGraph 载荷：null 列表返回空、节点与关系逐字段转 snake_case")
    @SuppressWarnings("unchecked")
    void payloadConversion() {
        assertTrue(((List<?>) ReflectionTestUtils.invokeMethod(provider, "toNodePayload",
                (List<IngestNode>) null)).isEmpty());
        assertTrue(((List<?>) ReflectionTestUtils.invokeMethod(provider, "toRelationPayload",
                (List<IngestRelation>) null)).isEmpty());

        IngestNode node = new IngestNode(1L, 4178L, "TCP", "内容", "CONCEPT");
        Map<String, Object> nodeMap = ((List<Map<String, Object>>) ReflectionTestUtils
                .invokeMethod(provider, "toNodePayload", List.of(node))).get(0);
        assertEquals(1L, nodeMap.get("id"));
        assertEquals(4178L, nodeMap.get("course_id"));
        assertEquals("TCP", nodeMap.get("name"));
        assertEquals("内容", nodeMap.get("content"));
        assertEquals("CONCEPT", nodeMap.get("type"));

        IngestRelation relation = new IngestRelation(1L, 2L, 3L, "PREREQUISITE");
        Map<String, Object> relationMap = ((List<Map<String, Object>>) ReflectionTestUtils
                .invokeMethod(provider, "toRelationPayload", List.of(relation))).get(0);
        assertEquals(1L, relationMap.get("id"));
        assertEquals(2L, relationMap.get("source_id"));
        assertEquals(3L, relationMap.get("target_id"));
        assertEquals("PREREQUISITE", relationMap.get("relation_type"));
    }

    @Test
    @DisplayName("isReadTimeout：原始 SocketTimeout 与包装 cause 均识别，其他异常不识别")
    void readTimeoutClassification() {
        // 判定逻辑已收敛到 DownstreamRetrySupport（DUP-10~13），直接测共用实现点
        assertTrue(com.aicourse.integration.support.DownstreamRetrySupport.isReadTimeout(
                new SocketTimeoutException("timeout")));
        assertTrue(com.aicourse.integration.support.DownstreamRetrySupport.isReadTimeout(
                new RuntimeException(new SocketTimeoutException("wrapped"))));
        assertFalse(com.aicourse.integration.support.DownstreamRetrySupport.isReadTimeout(
                new RuntimeException("other")));
    }
}
