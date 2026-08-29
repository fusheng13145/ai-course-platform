package com.aicourse.common;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JsonUtils 单元测试：覆盖 toJson（成功/异常被包装为 RuntimeException）/
 * extractJsonObject（```json 代码块 / 裸 JSON / 无 JSON / null）。
 */
class JsonUtilsTest {

    @Test
    void toJson_success() {
        String json = JsonUtils.toJson(Map.of("a", 1, "b", "x"));
        assertTrue(json.contains("\"a\":1"));
    }

    @Test
    void extractJsonObject_fromCodeBlock() {
        String text = "这里是说明\n```json\n{\"name\":\"ai\"}\n```\n结束";
        assertEquals("{\"name\":\"ai\"}", JsonUtils.extractJsonObject(text));
    }

    @Test
    void extractJsonObject_plainJson() {
        assertEquals("{\"k\":1}", JsonUtils.extractJsonObject("前缀 {\"k\":1} 后缀"));
    }

    @Test
    void extractJsonObject_noJson_returnsNull() {
        assertNull(JsonUtils.extractJsonObject("没有任何 json 对象"));
    }

    @Test
    void extractJsonObject_null_returnsNull() {
        assertNull(JsonUtils.extractJsonObject(null));
    }
}
