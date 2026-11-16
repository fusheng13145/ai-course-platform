package com.aicourse.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 题库 {@code questions.knowledge_points} 的知识点名称解析口径。
 *
 * <p>历史数据同时存在 JSON 数组（{@code ["TCP","三次握手"]}）与中英文逗号/分号分隔
 * （{@code TCP，三次握手}）两种形态。掌握度、错题分组与题库写入必须使用同一套拆分
 * 和归一化规则，否则会出现“同名知识点匹配不上”或“子串误匹配”。</p>
 */
public final class KnowledgePointNameUtils {

    private static final ObjectMapper JSON = new ObjectMapper();

    private KnowledgePointNameUtils() {
    }

    /**
     * 拆分知识点名称，兼容 JSON 数组与中英文逗号/分号。
     */
    public static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            try {
                List<String> names = JSON.readValue(trimmed, new TypeReference<List<String>>() {});
                return names.stream()
                        .filter(name -> name != null && !name.isBlank())
                        .map(String::trim)
                        .collect(Collectors.toList());
            } catch (Exception ignored) {
                // 非法 JSON 退回分隔符拆分，保持与历史数据兼容
            }
        }
        return Arrays.stream(trimmed.split("[,，;；]"))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 知识点名称比较键：去首尾空白 + 小写，避免大小写和空白造成同名节点匹配失败。
     */
    public static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
