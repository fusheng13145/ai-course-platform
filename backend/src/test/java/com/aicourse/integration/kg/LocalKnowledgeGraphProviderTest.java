package com.aicourse.integration.kg;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.integration.ContractPaths;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 知识图谱本地占位实现 {@link LocalKnowledgeGraphProvider} 的单元测试。
 * <p>
 * 锁定「当前阶段不依赖 Python」的占位契约：本地 BFS 路径规划、摄取/子图的本地降级、
 * 健康状态恒为 false。与 {@link com.aicourse.service.impl.KnowledgeGraphServiceImplTest}
 * 形成互补——本类直接验证契约实现，后者验证门面委派。
 */
class LocalKnowledgeGraphProviderTest {

    private KnowledgePointService mockPointService() {
        return mock(KnowledgePointService.class);
    }

    private KnowledgePointRelationService mockRelationService() {
        return mock(KnowledgePointRelationService.class);
    }

    @Test
    void healthCheck_returnsFalse_noPythonEngine() {
        var provider = new LocalKnowledgeGraphProvider(mockPointService(), mockRelationService(), 2);
        assertFalse(provider.healthCheck().healthy());
    }

    @Test
    void planLearningPath_findsShortestHops_locally() {
        var kp = mockPointService();
        var rel = mockRelationService();
        KnowledgePointRelation r12 = new KnowledgePointRelation();
        r12.setSourceId(1L);
        r12.setTargetId(2L);
        r12.setRelationType("prerequisite");
        KnowledgePointRelation r23 = new KnowledgePointRelation();
        r23.setSourceId(2L);
        r23.setTargetId(3L);
        r23.setRelationType("prerequisite");
        // 修复后：按起点知识点归属课程过滤关系加载（不再全表 list()）
        KnowledgePoint source = new KnowledgePoint();
        source.setId(1L);
        source.setCourseId(10L);
        when(kp.getById(1L)).thenReturn(source);
        when(rel.listByCourse(10L)).thenReturn(List.of(r12, r23));

        var provider = new LocalKnowledgeGraphProvider(kp, rel, 2);
        KnowledgeGraphContract.PathPlanResult r = provider.planLearningPath(
                new KnowledgeGraphContract.PathPlanRequest(1L, 3L, 5));

        assertTrue(r.found());
        assertEquals(KnowledgeGraphContract.Mode.LOCAL, r.mode());
        assertEquals(List.of(1L, 2L, 3L), r.path());
        assertEquals(2, r.hops());
        assertEquals(2, r.edges().size());
    }

    @Test
    void planLearningPath_isolatesByCourse_noCrossCoursePath() {
        // 跨课程连通风险回归：起点课程（10）关系外的跨课程关系不得参与规划
        var kp = mockPointService();
        var rel = mockRelationService();
        KnowledgePointRelation r12 = new KnowledgePointRelation();
        r12.setSourceId(1L);
        r12.setTargetId(2L);
        r12.setRelationType("prerequisite");
        KnowledgePoint source = new KnowledgePoint();
        source.setId(1L);
        source.setCourseId(10L);
        when(kp.getById(1L)).thenReturn(source);
        // listByCourse 仅返回本课程关系（r12），跨课程关系 r23 不在其中 -> 1→3 不可达
        when(rel.listByCourse(10L)).thenReturn(List.of(r12));

        var provider = new LocalKnowledgeGraphProvider(kp, rel, 5);
        KnowledgeGraphContract.PathPlanResult r = provider.planLearningPath(
                new KnowledgeGraphContract.PathPlanRequest(1L, 3L, 5));

        assertFalse(r.found());
        assertEquals(KnowledgeGraphContract.Mode.LOCAL, r.mode());
    }

    @Test
    void planLearningPath_sourcePointMissing_fallsBackToFullLoad() {
        // 起点知识点不存在（归属课程未知）时回退全量加载，保持占位可用性
        var kp = mockPointService();
        var rel = mockRelationService();
        KnowledgePointRelation r12 = new KnowledgePointRelation();
        r12.setSourceId(1L);
        r12.setTargetId(2L);
        r12.setRelationType("prerequisite");
        when(kp.getById(1L)).thenReturn(null);
        when(rel.list()).thenReturn(List.of(r12));

        var provider = new LocalKnowledgeGraphProvider(kp, rel, 3);
        KnowledgeGraphContract.PathPlanResult r = provider.planLearningPath(
                new KnowledgeGraphContract.PathPlanRequest(1L, 2L, 3));

        assertTrue(r.found());
        assertEquals(List.of(1L, 2L), r.path());
    }

    @Test
    void planLearningPath_nullIds_returnsNotFound() {
        var provider = new LocalKnowledgeGraphProvider(mockPointService(), mockRelationService(), 2);
        KnowledgeGraphContract.PathPlanResult r = provider.planLearningPath(
                new KnowledgeGraphContract.PathPlanRequest(null, 3L, 5));

        assertFalse(r.found());
        assertEquals(KnowledgeGraphContract.Mode.LOCAL, r.mode());
        assertTrue(r.path().isEmpty());
    }

    @Test
    void planLearningPath_unreachableTarget_returnsNotFound() {
        var kp = mockPointService();
        var rel = mockRelationService();
        KnowledgePointRelation r12 = new KnowledgePointRelation();
        r12.setSourceId(1L);
        r12.setTargetId(2L);
        KnowledgePoint source = new KnowledgePoint();
        source.setId(1L);
        source.setCourseId(10L);
        when(kp.getById(1L)).thenReturn(source);
        when(rel.listByCourse(10L)).thenReturn(List.of(r12));

        var provider = new LocalKnowledgeGraphProvider(kp, rel, 3);
        KnowledgeGraphContract.PathPlanResult r = provider.planLearningPath(
                new KnowledgeGraphContract.PathPlanRequest(1L, 99L, 3));

        assertFalse(r.found());
        assertEquals(KnowledgeGraphContract.Mode.LOCAL, r.mode());
    }

    @Test
    void ingest_returnsLocalMode_withSnapshotCounts() {
        var kp = mockPointService();
        var rel = mockRelationService();

        var provider = new LocalKnowledgeGraphProvider(kp, rel, 2);
        KnowledgeGraphContract.IngestResult r = provider.ingest(new KnowledgeGraphContract.IngestRequest(
                1L,
                java.util.List.of(
                        new KnowledgeGraphContract.IngestNode(11L, 1L, "A", "", "CONCEPT"),
                        new KnowledgeGraphContract.IngestNode(12L, 1L, "B", "", "CONCEPT")),
                java.util.List.of(
                        new KnowledgeGraphContract.IngestRelation(21L, 11L, 12L, "PREREQUISITE"))));

        assertEquals(200, r.code());
        assertEquals(ContractPaths.MODE_LOCAL, r.mode());
        assertEquals(1L, r.courseId());
        assertEquals(2L, r.syncedNodes());
        assertEquals(1L, r.syncedRelations());
    }

    @Test
    void getSubGraph_returnsLocalNodesAndEdges() {
        var kp = mockPointService();
        var rel = mockRelationService();
        KnowledgePoint p = new KnowledgePoint();
        p.setId(10L);
        KnowledgePointRelation edge = new KnowledgePointRelation();
        edge.setSourceId(10L);
        edge.setTargetId(11L);
        when(kp.listByCourse(1L)).thenReturn(List.of(p));
        when(rel.listByCourse(1L)).thenReturn(List.of(edge));

        var provider = new LocalKnowledgeGraphProvider(kp, rel, 2);
        KnowledgeGraphContract.SubGraphResult r = provider.getSubGraph(1L, 2);

        assertEquals(200, r.code());
        assertEquals(ContractPaths.MODE_LOCAL, r.mode());
        assertEquals(1L, r.courseId());
        assertEquals(2, r.maxHops());
        assertEquals(1, r.nodes().size());
        assertEquals(1, r.edges().size());
    }

    @Test
    void getSubGraph_nullCourseId_returnsEmptyCollections() {
        var provider = new LocalKnowledgeGraphProvider(mockPointService(), mockRelationService(), 2);
        KnowledgeGraphContract.SubGraphResult r = provider.getSubGraph(null, 0);

        assertEquals(200, r.code());
        assertEquals(ContractPaths.MODE_LOCAL, r.mode());
        assertNotNull(r.nodes());
        assertNotNull(r.edges());
        assertTrue(r.nodes().isEmpty());
        assertTrue(r.edges().isEmpty());
    }
}
