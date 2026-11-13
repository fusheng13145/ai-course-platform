package com.aicourse.util;

import com.aicourse.common.ApiCode;
import com.aicourse.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-13 ⑩：三个 JSON 列（options／correct_answers／knowledge_points）的入参规范化。
 *
 * <p>判据来自实测：实体把 JSON 列声明成 String，MyBatis 直接写字符串字面量，
 * 纯文本 {@code A} 会被 MySQL 判成 `Invalid JSON text` → 400。这里锁住「任何形态的入参
 * 都能变成合法 JSON」这一条，避免客户端各自编码。</p>
 */
class JsonColumnNormalizerTest {

    @Test
    @DisplayName("纯文本答案编码成 JSON 字符串字面量")
    void plainTextBecomesJsonString() {
        assertEquals("\"A\"", JsonColumnNormalizer.normalize("A"));
        assertEquals("\"对\"", JsonColumnNormalizer.normalizeAnswers("对", "TRUE_FALSE"));
    }

    @Test
    @DisplayName("已是合法 JSON 的字符串原样保留（老客户端已编码，不应被二次编码）")
    void alreadyJsonIsKeptVerbatim() {
        assertEquals("[\"A\",\"B\"]", JsonColumnNormalizer.normalize("[\"A\",\"B\"]"));
        assertEquals("\"A\"", JsonColumnNormalizer.normalize("\"A\""));
    }

    @Test
    @DisplayName("结构化入参（数组／Map）序列化为 JSON")
    void structuredInputIsSerialized() {
        assertEquals("[\"A\",\"B\"]", JsonColumnNormalizer.normalize(List.of("A", "B")));
        assertEquals("{\"k\":1}", JsonColumnNormalizer.normalize(Map.of("k", 1)));
    }

    @Test
    @DisplayName("数组列（options／knowledgePoints）：数组与数组文本都收，空白给 []，null 保持 null")
    void arrayColumnAcceptsOnlyArrays() {
        assertEquals("[\"选项1\",\"选项2\"]", JsonColumnNormalizer.normalizeArrayColumn(List.of("选项1", "选项2"), "options"));
        assertEquals("[\"闭包\"]", JsonColumnNormalizer.normalizeArrayColumn("[\"闭包\"]", "knowledgePoints"));
        assertEquals("[]", JsonColumnNormalizer.normalizeArrayColumn("   ", "options"));
        assertNull(JsonColumnNormalizer.normalizeArrayColumn(null, "options"));
    }

    @Test
    @DisplayName("数组列收到纯文本／对象／标量一律 400，不静默存形状不对的值")
    void arrayColumnRejectsNonArray() {
        BusinessException e1 = assertThrows(BusinessException.class,
                () -> JsonColumnNormalizer.normalizeArrayColumn("A. 选项一\nB. 选项二", "options"));
        assertEquals(ApiCode.BAD_REQUEST, e1.getCode(), "纯文本必须被拒，否则读出来是标量而非数组");
        assertThrows(BusinessException.class, () -> JsonColumnNormalizer.normalizeArrayColumn(Map.of("k", 1), "options"));
        assertThrows(BusinessException.class, () -> JsonColumnNormalizer.normalizeArrayColumn(5, "knowledgePoints"));
        assertThrows(BusinessException.class, () -> JsonColumnNormalizer.normalizeArrayColumn("\"A\"", "knowledgePoints"));
    }

    @Test
    @DisplayName("多选的 \"A,B\" 简写拆成数组")
    void multipleChoiceCommaSplit() {
        assertEquals("[\"A\",\"B\"]", JsonColumnNormalizer.normalizeAnswers("A, B", "MULTIPLE_CHOICE"));
    }

    @Test
    @DisplayName("非多选的逗号文本按字符串编码，不擅自拆")
    void commaTextKeptForSingleChoice() {
        assertEquals("\"A,B\"", JsonColumnNormalizer.normalizeAnswers("A,B", "SINGLE_CHOICE"));
    }

    @Test
    @DisplayName("空白字符串规范成空数组（本仓约定 [] 表示「没有」），null 保持 null")
    void blankBecomesEmptyArrayAndNullStaysNull() {
        assertEquals("[]", JsonColumnNormalizer.normalize("   "));
        assertNull(JsonColumnNormalizer.normalize(null));
        assertNull(JsonColumnNormalizer.normalizeAnswers(null, "SINGLE_CHOICE"));
    }

    @Test
    @DisplayName("合法性判定：标量也是合法 JSON，裸词不是")
    void jsonValidity() {
        assertTrue(JsonColumnNormalizer.isValidJson("123"));
        assertTrue(JsonColumnNormalizer.isValidJson("[]"));
        assertFalse(JsonColumnNormalizer.isValidJson("A"));
        assertFalse(JsonColumnNormalizer.isValidJson(""));
    }
}
