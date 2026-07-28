package com.aicourse.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON 工具类：从 LLM 返回文本中健壮地提取首个 JSON 对象。
 * 同时兼容 ```json ``` 代码块包裹与裸 JSON 文本。
 * 作为纯工具方法，独立于 Service 层，避免控制器直接耦合 Service 实现类。
 */
public final class JsonUtils {

    private static final Pattern JSON_BLOCK =
            Pattern.compile("```(?:json)?\\s*(\\{[\\s\\S]*?})\\s*```", Pattern.CASE_INSENSITIVE);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    /**
     * 将对象序列化为 JSON 字符串（用于 DTO 列表字段落库为 JSON 文本）。
     * @param obj 待序列化对象
     * @return JSON 字符串
     */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    /**
     * 从 LLM 返回中提取首个 JSON 对象（兼容 ```json ``` 代码块或裸 JSON）
     *
     * @param text 原始文本
     * @return 提取出的 JSON 字符串；无法提取时返回 null
     */
    public static String extractJsonObject(String text) {
        if (text == null) return null;
        String t = text.trim();
        Matcher m = JSON_BLOCK.matcher(t);
        if (m.find()) {
            return m.group(1);
        }
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return t.substring(start, end + 1);
        }
        return null;
    }
}
