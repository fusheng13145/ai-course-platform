package com.aicourse.service.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 提交领域 JSON 辅助工具（P0-4：统一用注入的 ObjectMapper，杜绝字符串拼接导致的非法 JSON）。
 * <p>拆分自原 SubmissionServiceImpl 私有方法（2026-08-29），被写/评分/读组件共享。</p>
 */
@Slf4j
@Component
public class SubmissionJsonSupport {

    private final ObjectMapper objectMapper;

    public SubmissionJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将 submissionData JSON 解析为可变 Map（容错：解析失败返回空 Map）
     */
    public Map<String, Object> parseData(String json) {
        if (json == null || json.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return map != null ? map : new LinkedHashMap<>();
        } catch (IOException e) {
            log.error("查询提交失败: {}", e.getMessage(), e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * 将 Map 序列化回 JSON 字符串（容错）
     */
    public String serializeData(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (IOException e) {
            log.error("查询提交失败: {}", e.getMessage(), e);
            return "{}";
        }
    }

    /**
     * 更新 JSON 中的字段值（值为字符串形式存储，与历史数据兼容）
     */
    public String updateJsonField(String json, String fieldKey, String fieldValue) {
        Map<String, Object> map = parseData(json);
        map.put(fieldKey, fieldValue);
        return serializeData(map);
    }

    /**
     * 从 JSON 中提取整数字段（兼容字符串/数字两种存储形态）
     */
    public int extractIntFromJson(String json, String fieldKey) {
        Map<String, Object> map = parseData(json);
        Object value = map.get(fieldKey);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            log.error("查询提交失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 从 JSON 中提取字符串字段
     */
    public String extractStringFromJson(String json, String fieldKey) {
        Map<String, Object> map = parseData(json);
        Object value = map.get(fieldKey);
        return value != null ? value.toString() : null;
    }

    /**
     * 解析 JSON 树（供 AI 评分等场景读取结构化响应，异常由调用方处理）
     */
    public JsonNode readTree(String json) throws IOException {
        return objectMapper.readTree(json);
    }
}
