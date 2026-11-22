package com.aicourse.domain.knowledge;

import java.util.Locale;

/**
 * 知识点关系的领域枚举。
 *
 * <p>数据库、HTTP API 与 Python 引擎曾各自使用不同写法：V1 注释为
 * {@code prerequisite/related/follow-up}，历史服务层写入大写，DeepGraph
 * 适配器写入中文“包含章节/属于/相关”。本枚举把“语义”收敛为唯一的领域值，
 * 其余写法只在输入边界做别名解析，避免继续把展示文案写进持久化列。</p>
 *
 * <p>持久化编码使用 {@link #code()}；图谱可视化出参为了兼容当前前端，
 * 由 {@link #graphLabel(String)} 映射回既有中文标签。等前端完成快照/枚举改造后，
 * 可直接改为输出 {@code code()}，不需要再次迁移数据。</p>
 */
public enum RelationType {

    PREREQUISITE("PREREQUISITE", "前置知识"),
    RELATED("RELATED", "相关"),
    FOLLOW_UP("FOLLOW_UP", "后续知识"),
    CONTAINS("CONTAINS", "包含"),
    CONTAINS_CHAPTER("CONTAINS_CHAPTER", "包含章节"),
    BELONGS_TO("BELONGS_TO", "属于");

    /** 持久化编码，最多 30 个字符（与 knowledge_point_relations.relation_type 对齐）。 */
    private final String code;

    /** 图谱接口的历史展示标签，仅用于输出兼容。 */
    private final String graphLabel;

    RelationType(String code, String graphLabel) {
        this.code = code;
        this.graphLabel = graphLabel;
    }

    public String code() {
        return code;
    }

    public String graphLabel() {
        return graphLabel;
    }

    /**
     * 解析已知别名；未知值返回 {@code null}，由调用方决定是保留自定义编码还是拒绝。
     */
    public static RelationType known(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "PREREQUISITE", "PREREQ", "PRE", "前置", "前置知识" -> PREREQUISITE;
            case "RELATED", "RELATION", "RELATED_TO", "相关" -> RELATED;
            case "FOLLOW_UP", "FOLLOWUP", "SUCCESSOR", "后续", "后续知识" -> FOLLOW_UP;
            case "CONTAINS", "包含" -> CONTAINS;
            case "CONTAINS_CHAPTER", "CHAPTER", "包含章节" -> CONTAINS_CHAPTER;
            case "BELONGS_TO", "BELONG", "BELONGS", "属于", "归属" -> BELONGS_TO;
            default -> null;
        };
    }

    /**
     * 写入持久化列前的规范编码：已知别名归一到领域编码，未知值只做去空白/大写和长度保护。
     */
    public static String canonicalCode(String raw) {
        RelationType known = known(raw);
        if (known != null) {
            return known.code;
        }
        return sanitize(raw);
    }

    /**
     * 图谱接口出参标签：已知关系映射回历史中文标签，未知关系原样返回。
     */
    public static String graphLabel(String raw) {
        RelationType known = known(raw);
        if (known != null) {
            return known.graphLabel;
        }
        return raw == null || raw.isBlank() ? RELATED.graphLabel : raw.trim();
    }

    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return RELATED.code;
        }
        String normalized = raw.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", "_");
        return normalized.length() <= 30 ? normalized : normalized.substring(0, 30);
    }
}
