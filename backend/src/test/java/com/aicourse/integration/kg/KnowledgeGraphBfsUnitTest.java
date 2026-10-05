package com.aicourse.integration.kg;

import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 本地 BFS 兜底路径规划（{@link LocalKnowledgeGraphProvider}）单元测试。
 *
 * <p>该实现是知识图谱引擎（Python 8001）未接入时的<strong>消费侧兜底</strong>：直接读取
 * {@code knowledge_point_relations} 全部关系，构建无向邻接后做 BFS 最短跳数回溯。
 * 本测试脱离 Spring 上下文，仅用 Mockito 桩 {@link KnowledgePointRelationService}，
 * 验证 BFS 行为的正确性与边界，并固化「全量关系未过滤课程维度」的性能/跨课程连通隐患认知。</p>
 *
 * <p>边界结论（详见 docs/项目手册.md 第三部分 §14 标准化集成层）：
 * <ol>
 *   <li>数据完整性依赖 {@code knowledge_point_relations} 行的存在；关系稀疏则路径 found=false（优雅降级）；</li>
 *   <li>性能上限：{@code relationService.list()} 每次全表加载所有课程关系，规模增长时为 O(全表关系) 内存+遍历，未做课程过滤；</li>
 *   <li>跨课程连通风险：邻接按全局关系构建，BFS 可能跨课程连通（见 testCrossCourseEdgesAreNotFiltered）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeGraphBfsUnitTest {

    @Mock
    private KnowledgePointService knowledgePointService;

    @Mock
    private KnowledgePointRelationService relationService;

    private LocalKnowledgeGraphProvider provider(int defaultMaxHops) {
        return new LocalKnowledgeGraphProvider(knowledgePointService, relationService, defaultMaxHops);
    }

    private static KnowledgePointRelation rel(long source, long target, String type) {
        KnowledgePointRelation r = new KnowledgePointRelation();
        r.setSourceId(source);
        r.setTargetId(target);
        r.setRelationType(type);
        return r;
    }

    @Test
    void pathFound_linearGraph_shortestHops() {
        when(relationService.list()).thenReturn(List.of(
                rel(1L, 2L, "PREREQUISITE"),
                rel(2L, 3L, "PREREQUISITE")));

        KnowledgeGraphContract.PathPlanResult res =
                provider(5).planLearningPath(new KnowledgeGraphContract.PathPlanRequest(1L, 3L, 5));

        assertTrue(res.found());
        assertEquals(List.of(1L, 2L, 3L), res.path());
        assertEquals(2, res.hops());
        assertEquals(KnowledgeGraphContract.Mode.LOCAL, res.mode());
        assertNotNull(res.edges());
        assertEquals(2, res.edges().size());
        assertEquals("PREREQUISITE", res.edges().get(0).relationType());
    }

    @Test
    void pathNotFound_whenExceedsMaxHops() {
        when(relationService.list()).thenReturn(List.of(
                rel(1L, 2L, "X"),
                rel(2L, 3L, "X")));

        KnowledgeGraphContract.PathPlanResult res =
                provider(5).planLearningPath(new KnowledgeGraphContract.PathPlanRequest(1L, 3L, 1));

        assertFalse(res.found());
        assertTrue(res.path().isEmpty());
    }

    @Test
    void pathNotFound_whenSourceHasNoRelations() {
        when(relationService.list()).thenReturn(List.of(rel(2L, 3L, "X")));

        KnowledgeGraphContract.PathPlanResult res =
                provider(5).planLearningPath(new KnowledgeGraphContract.PathPlanRequest(1L, 3L, 5));

        assertFalse(res.found());
        assertTrue(res.note().contains("不存在任何关联关系"));
    }

    @Test
    void pathFound_whenSourceEqualsTarget() {
        when(relationService.list()).thenReturn(List.of(rel(1L, 2L, "X")));

        KnowledgeGraphContract.PathPlanResult res =
                provider(5).planLearningPath(new KnowledgeGraphContract.PathPlanRequest(1L, 1L, 5));

        assertTrue(res.found());
        assertEquals(List.of(1L), res.path());
        assertEquals(0, res.hops());
    }

    @Test
    void pathNotFound_whenSourceOrTargetIdNull() {
        // 注意：sourceId 为 null 时方法在调用 relationService.list() 之前即提前返回，
        // 故此处不桩 list()（避免 Mockito 严格模式报多余桩）。
        KnowledgeGraphContract.PathPlanResult res =
                provider(5).planLearningPath(new KnowledgeGraphContract.PathPlanRequest(null, 3L, 5));

        assertFalse(res.found());
        assertTrue(res.note().contains("为空"));
    }

    @Test
    void crossCourseEdgesAreNotFiltered() {
        // planLearningPath 仅读取关系表的 source/target/type，不按课程过滤：
        // 即便关系语义上分属不同课程（此处以不同 ID 段模拟），邻接仍按全局关系构建，
        // BFS 可跨课程连通——这是规模增长时的性能上限与跨课程连通风险来源。
        when(relationService.list()).thenReturn(List.of(
                rel(10L, 20L, "A"),
                rel(20L, 30L, "A")));

        KnowledgeGraphContract.PathPlanResult res =
                provider(5).planLearningPath(new KnowledgeGraphContract.PathPlanRequest(10L, 30L, 5));

        assertTrue(res.found());
        assertEquals(List.of(10L, 20L, 30L), res.path());
    }

    /** 1—2—3—4—5 线性链：端点 1→5 的真实最短距离为 4 跳。 */
    private void stubFourHopChain() {
        when(relationService.list()).thenReturn(List.of(
                rel(1L, 2L, "PREREQUISITE"),
                rel(2L, 3L, "PREREQUISITE"),
                rel(3L, 4L, "PREREQUISITE"),
                rel(4L, 5L, "PREREQUISITE")));
    }

    @Test
    void unsetMaxHops_returnsTrueShortestPath_beyondConstructorDefault() {
        // 未指定跳数（<=0）时必须给出真实最短路径，而不是套用 GraphRAG 子图扩图上限。
        // 回归缺陷：原先借用 rag.max-hops=2，65 节点课程图上约八成合法查询假性「未找到」。
        stubFourHopChain();

        KnowledgeGraphContract.PathPlanResult res =
                provider(2).planLearningPath(new KnowledgeGraphContract.PathPlanRequest(1L, 5L, 0));

        assertTrue(res.found(), "未指定跳数时应能给出 4 跳真实路径");
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), res.path());
        assertEquals(4, res.hops());
    }

    @Test
    void capExceeded_noteReportsTrueShortestDistance() {
        // 显式上限不足时，note 需回传真实最短跳数，避免用户面对「未找到」却无从判断该调到几。
        stubFourHopChain();

        KnowledgeGraphContract.PathPlanResult res =
                provider(2).planLearningPath(new KnowledgeGraphContract.PathPlanRequest(1L, 5L, 2));

        assertFalse(res.found());
        assertTrue(res.note().contains("4"), "note 应含真实最短跳数 4，实际：" + res.note());
        assertTrue(res.note().contains("2"), "note 应含本次上限 2，实际：" + res.note());
    }
}
