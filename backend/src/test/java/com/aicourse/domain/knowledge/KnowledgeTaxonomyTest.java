package com.aicourse.domain.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 知识分类收敛规则测试：持久化编码唯一，展示标签兼容历史前端。
 */
class KnowledgeTaxonomyTest {

    @Test
    @DisplayName("关系别名统一收敛为领域编码")
    void relationAliasesConverge() {
        assertEquals("PREREQUISITE", RelationType.canonicalCode("prerequisite"));
        assertEquals("PREREQUISITE", RelationType.canonicalCode(" 前置知识 "));
        assertEquals("RELATED", RelationType.canonicalCode("related"));
        assertEquals("RELATED", RelationType.canonicalCode(null));
        assertEquals("FOLLOW_UP", RelationType.canonicalCode("follow-up"));
        assertEquals("CONTAINS", RelationType.canonicalCode("包含"));
        assertEquals("CONTAINS_CHAPTER", RelationType.canonicalCode("包含章节"));
        assertEquals("BELONGS_TO", RelationType.canonicalCode("属于"));
    }

    @Test
    @DisplayName("未知关系只做编码清洗，不被静默改成语义不同的值")
    void unknownRelationKeepsStableCode() {
        assertEquals("CUSTOM_REL", RelationType.canonicalCode(" custom rel "));
        assertEquals("UNKNOWN", RelationType.canonicalCode("unknown"));
    }

    @Test
    @DisplayName("图谱接口保留历史中文标签，避免前端渲染契约突变")
    void graphLabelsStayBackwardCompatible() {
        assertEquals("包含章节", RelationType.graphLabel("CONTAINS_CHAPTER"));
        assertEquals("包含", RelationType.graphLabel("CONTAINS"));
        assertEquals("属于", RelationType.graphLabel("belongs_to"));
        assertEquals("相关", RelationType.graphLabel(null));
        assertEquals("前置知识", RelationType.graphLabel("prereq"));
        assertEquals("自定义", RelationType.graphLabel("自定义"));
    }

    @Test
    @DisplayName("点类型大小写/中文别名统一为领域编码")
    void pointTypesConverge() {
        assertEquals("CHAPTER", PointType.canonicalCode("chapter"));
        assertEquals("CONCEPT", PointType.canonicalCode("概念"));
        assertEquals("CONCEPT", PointType.canonicalCode(null));
        assertEquals("SKILL", PointType.canonicalCode("skill"));
        assertEquals("PRINCIPLE", PointType.canonicalCode(" principle "));
        assertEquals("METHOD", PointType.canonicalCode("method"));
        assertEquals("FACT", PointType.canonicalCode("事实"));
        assertEquals("SUMMARY", PointType.canonicalCode("小结"));
    }

    @Test
    @DisplayName("未知点类型保留清洗编码，空白回退 CONCEPT，超长截断 30")
    void unknownPointTypeKeepsCleanedCodeAndClampsLength() {
        assertEquals("UNKNOWN_TYPE", PointType.canonicalCode("unknown type"));
        assertEquals("CONCEPT", PointType.canonicalCode("   "));
        assertEquals("A".repeat(30), PointType.canonicalCode("a".repeat(40)));
    }

    @Test
    @DisplayName("关系扩展别名、空白回退与超长截断均稳定")
    void relationExtendedAliasesAndSanitize() {
        assertEquals("PREREQUISITE", RelationType.canonicalCode("PRE"));
        assertEquals("PREREQUISITE", RelationType.canonicalCode("前置"));
        assertEquals("RELATED", RelationType.canonicalCode("RELATED_TO"));
        assertEquals("FOLLOW_UP", RelationType.canonicalCode("SUCCESSOR"));
        assertEquals("CONTAINS_CHAPTER", RelationType.canonicalCode("CHAPTER"));
        assertEquals("BELONGS_TO", RelationType.canonicalCode("归属"));
        assertEquals("RELATED", RelationType.canonicalCode("   "));
        assertEquals("A".repeat(30), RelationType.canonicalCode("a".repeat(40)));
        assertEquals("相关", RelationType.graphLabel("   "));
        assertEquals("自定义", RelationType.graphLabel(" 自定义 "));
    }

    @Test
    @DisplayName("快照版本随课程/节点数/边数/更新时间变化，且格式稳定")
    void snapshotVersionIsStableAndChanges() {
        LocalDateTime t1 = LocalDateTime.of(2026, 11, 22, 12, 0);
        GraphSnapshotVersion.Snapshot a = GraphSnapshotVersion.of(1L, 10, 20, t1, t1);
        GraphSnapshotVersion.Snapshot aAgain = GraphSnapshotVersion.of(1L, 10, 20, t1, t1);
        GraphSnapshotVersion.Snapshot b = GraphSnapshotVersion.of(1L, 11, 20, t1, t1);
        GraphSnapshotVersion.Snapshot c = GraphSnapshotVersion.of(2L, 10, 20, t1, t1);

        assertEquals(a.version(), aAgain.version(), "同一数据集必须稳定同版本");
        assertNotEquals(a.version(), b.version(), "节点数变化必须换版本");
        assertNotEquals(a.version(), c.version(), "课程变化必须换版本");
        assertTrue(a.version().startsWith("v1:"));
        assertNotNull(a.generatedAt());
    }

    @Test
    @DisplayName("入库归一：六值别名不打隔离标，也不重复留痕")
    void ingestCanonicalAliases() {
        RelationType.IngestRelation r = RelationType.resolveIngest("前置知识");
        assertEquals("PREREQUISITE", r.code());
        assertNull(r.rawCode());
        assertFalse(r.quarantined());
    }

    @Test
    @DisplayName("入库归一：真关系谓语按显式映射表归一并留痕")
    void ingestMappedPredicates() {
        RelationType.IngestRelation dep = RelationType.resolveIngest("依赖");
        assertEquals("PREREQUISITE", dep.code());
        assertEquals("依赖", dep.rawCode());
        assertFalse(dep.quarantined());

        assertEquals("PREREQUISITE", RelationType.resolveIngest("需要").code());
        assertEquals("CONTAINS", RelationType.resolveIngest("包含协议").code());
        assertEquals("CONTAINS", RelationType.resolveIngest("具有/包含").code());
        assertEquals("BELONGS_TO", RelationType.resolveIngest("具有/属于").code());

        RelationType.IngestRelation fn = RelationType.resolveIngest("功能");
        assertEquals("RELATED", fn.code());
        assertEquals("功能", fn.rawCode());
        assertFalse(fn.quarantined());
        assertEquals("RELATED", RelationType.resolveIngest("same").code(), "英文映射需大小写不敏感");
        assertEquals("RELATED", RelationType.resolveIngest("面临").code());
    }

    @Test
    @DisplayName("入库归一：错位谓语保留原值并打隔离标，不洗白为 RELATED")
    void ingestQuarantinedPredicates() {
        for (String dirty : new String[]{"数据链路层", "最大地址", "路由表", "序号字段长度", "到2020年5月"}) {
            RelationType.IngestRelation r = RelationType.resolveIngest(dirty);
            assertEquals(dirty, r.code(), "隔离边必须保留原谓语，不做语义洗白");
            assertEquals(dirty, r.rawCode());
            assertTrue(r.quarantined());
            assertTrue(RelationType.isQuarantined(dirty));
        }
    }

    @Test
    @DisplayName("入库归一：未登记谓语默认隔离，等待人工登记而非进入默认图谱")
    void ingestUnknownPredicateDefaultsToQuarantine() {
        RelationType.IngestRelation r = RelationType.resolveIngest("某种全新谓语");
        assertTrue(r.quarantined());
        assertEquals("某种全新谓语", r.rawCode());
        assertNotEquals("RELATED", r.code());
    }
}
