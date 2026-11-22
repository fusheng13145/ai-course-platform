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
}
