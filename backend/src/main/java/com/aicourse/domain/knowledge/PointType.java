package com.aicourse.domain.knowledge;

import java.util.Locale;

/**
 * 知识点类型的领域枚举。
 *
 * <p>历史数据库注释使用小写 concept/skill/principle，而 DeepGraph 同步和前端
 * CATEGORY_TEXT 使用大写 CHAPTER/CONCEPT。该枚举把持久化编码固定为大写，
 * 输入边界兼容大小写与中文标签。</p>
 */
public enum PointType {

    CHAPTER("CHAPTER"),
    CONCEPT("CONCEPT"),
    SKILL("SKILL"),
    PRINCIPLE("PRINCIPLE"),
    METHOD("METHOD"),
    FACT("FACT"),
    SUMMARY("SUMMARY");

    /** 持久化编码，最多 30 个字符（与 knowledge_points.point_type 对齐）。 */
    private final String code;

    PointType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static PointType known(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "CHAPTER", "章节" -> CHAPTER;
            case "CONCEPT", "概念" -> CONCEPT;
            case "SKILL", "技能" -> SKILL;
            case "PRINCIPLE", "原理" -> PRINCIPLE;
            case "METHOD", "方法" -> METHOD;
            case "FACT", "事实" -> FACT;
            case "SUMMARY", "小结" -> SUMMARY;
            default -> null;
        };
    }

    /**
     * 持久化编码：已知值归一为大写领域编码；未知值保留清洗后的编码，兼容历史扩展类型。
     */
    public static String canonicalCode(String raw) {
        PointType known = known(raw);
        if (known != null) {
            return known.code;
        }
        if (raw == null || raw.isBlank()) {
            return CONCEPT.code;
        }
        String normalized = raw.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", "_");
        return normalized.length() <= 30 ? normalized : normalized.substring(0, 30);
    }
}
