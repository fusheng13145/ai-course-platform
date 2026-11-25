package com.aicourse.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JsonNodeSupport} 契约测试：保留下游"非 JSON 也绝不 500"的既有行为。
 */
class JsonNodeSupportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("null 入参原样返回 null（不构造空节点，避免改变既有响应语义）")
    void nullInputReturnsNull() {
        assertNull(JsonNodeSupport.parseOrText(null, MAPPER, "UT"));
    }

    @Test
    @DisplayName("合法 JSON 文本解析为结构节点")
    void validJsonParsedAsObject() {
        JsonNode node = JsonNodeSupport.parseOrText("{\"mode\":\"OK\",\"hits\":3}", MAPPER, "UT");

        assertTrue(node.isObject());
        assertEquals("OK", node.get("mode").asText());
        assertEquals(3, node.get("hits").asInt());
    }

    @Test
    @DisplayName("非 JSON 文本回退为文本节点，而不是抛异常")
    void plainTextFallsBackToTextNode() {
        JsonNode node = JsonNodeSupport.parseOrText("engine unavailable", MAPPER, "UT");

        assertTrue(node.isTextual());
        assertEquals("engine unavailable", node.asText());
    }

    @Test
    @DisplayName("截断的 JSON 也走文本节点回退（下游异常场景）")
    void brokenJsonFallsBackToTextNode() {
        JsonNode node = JsonNodeSupport.parseOrText("{\"mode\":", MAPPER, "UT");

        assertTrue(node.isTextual());
        assertEquals("{\"mode\":", node.asText());
    }
}
