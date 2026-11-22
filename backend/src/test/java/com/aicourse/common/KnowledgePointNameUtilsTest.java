package com.aicourse.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 知识点名称拆分/归一化边界：JSON 数组、非法 JSON 回退、中英文分隔符与空值。 */
class KnowledgePointNameUtilsTest {

    @Test
    @DisplayName("空值/空白返回空列表")
    void blankReturnsEmpty() {
        assertTrue(KnowledgePointNameUtils.split(null).isEmpty());
        assertTrue(KnowledgePointNameUtils.split("   ").isEmpty());
    }

    @Test
    @DisplayName("JSON 数组过滤 null/空白并 trim")
    void jsonArrayFiltersBlank() {
        assertEquals(List.of("TCP", "三次握手"),
                KnowledgePointNameUtils.split("[\"TCP\", null, \" 三次握手 \", \"\"]"));
    }

    @Test
    @DisplayName("非法 JSON 回退中英文逗号/分号拆分")
    void invalidJsonFallsBackToSeparators() {
        assertEquals(List.of("bad", "三次握手", "拥塞控制", "可靠传输"),
                KnowledgePointNameUtils.split("bad，三次握手;拥塞控制；可靠传输"));
    }

    @Test
    @DisplayName("普通分隔符与空分片过滤")
    void separatorsAndEmptySegments() {
        assertEquals(List.of("A", "B", "C"),
                KnowledgePointNameUtils.split(" A , B； , C ; "));
    }

    @Test
    @DisplayName("比较键去空白并小写")
    void normalizeTrimsAndLowercases() {
        assertEquals("tcp", KnowledgePointNameUtils.normalize("  TCP "));
        assertEquals("", KnowledgePointNameUtils.normalize(null));
        assertEquals("", KnowledgePointNameUtils.normalize("   "));
    }
}
