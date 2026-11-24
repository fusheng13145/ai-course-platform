package com.aicourse.domain.knowledge;

import java.util.Locale;
import java.util.Set;

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

    /**
     * 入库归一结果。
     *
     * @param code        写入 relation_type 的值
     * @param rawCode     写入 raw_relation_type 的原始谓语；六值别名归一时为 null（与 V35 留痕口径一致）
     * @param quarantined true 表示错位/未登记谓语，打隔离标、默认图谱查询排除
     */
    public record IngestRelation(String code, String rawCode, boolean quarantined) {
    }

    /**
     * 判断谓语是否属于上游抽取错位（实体名/数值/时间被写进谓语位置）。
     */
    public static boolean isQuarantined(String raw) {
        return raw != null && QUARANTINED_PREDICATES.contains(raw.trim());
    }

    /**
     * 入库边界的谓语归一，与 V35 存量迁移使用同一份名单，保证新旧数据口径一致。
     *
     * <p>规则顺序：错位隔离 → 六值别名 → 显式映射表 → 未登记谓语默认隔离。
     * 未登记谓语不洗白成 RELATED：宁可隔离待人工登记，也不让新脏数据进入默认图谱。</p>
     */
    public static IngestRelation resolveIngest(String raw) {
        if (isQuarantined(raw)) {
            return new IngestRelation(sanitize(raw), raw, true);
        }
        RelationType known = known(raw);
        if (known != null) {
            return new IngestRelation(known.code, null, false);
        }
        String key = raw == null ? "" : raw.trim();
        String mapped = null;
        if (TO_PREREQUISITE.contains(key)) {
            mapped = PREREQUISITE.code;
        } else if (TO_CONTAINS.contains(key)) {
            mapped = CONTAINS.code;
        } else if (TO_BELONGS_TO.contains(key)) {
            mapped = BELONGS_TO.code;
        } else if (TO_RELATED.contains(key) || TO_RELATED.contains(key.toUpperCase(Locale.ROOT))) {
            mapped = RELATED.code;
        }
        if (mapped != null) {
            return new IngestRelation(mapped, raw, false);
        }
        return new IngestRelation(sanitize(raw), raw, true);
    }

    /** 显式映射：依赖/需要 → 前置知识。 */
    private static final Set<String> TO_PREREQUISITE = Set.of("依赖", "需要");

    /** 显式映射：包含语义 → 包含。 */
    private static final Set<String> TO_CONTAINS = Set.of("包含协议", "具有/包含");

    /** 显式映射：归属语义 → 属于。 */
    private static final Set<String> TO_BELONGS_TO = Set.of("具有/属于");

    /**
     * 真关系谓语（67 种）弱化为「相关」：六值词表没有功能/定义/示例/因果等类型，
     * 原始谓语留痕在 raw_relation_type，需要更细粒度时可据此回建。
     */
    private static final Set<String> TO_RELATED = Set.of(
            "功能", "具有", "示例", "定义", "导致", "应用", "对应", "分类", "关注", "概念",
            "特性", "特点", "用途", "目标", "类型", "与", "功能/影响", "劣势", "定义/示例", "执行",
            "提供", "检测", "概念/用途", "等于", "通过", "重要性", "SAME", "不提供", "代表", "优势",
            "优势/定义", "优势/难度很大", "传播", "传输", "位于/分用", "冲击", "减少串扰", "分类/定义", "功能/导致", "地位",
            "大于", "安全性", "必须达到", "意义", "成为", "抗电磁干扰", "换算成", "提供接口机制", "提升", "提高到",
            "支持", "标志", "检测方法", "正式名称", "泛滥", "激励", "目的", "目的/过程", "研究", "简称为",
            "绞合", "被入侵", "被导引", "运算速度", "连接", "通过运输层", "面临");

    /** 错位谓语隔离名单（36 种）：谓语位置是实体名、数值或时间，不构成关系。 */
    private static final Set<String> QUARANTINED_PREDICATES = Set.of(
            "数据链路层", "最大地址", "三个基本问题", "最小值", "路由表", "单位", "数值", "末地址",
            "转发表", "首地址", "IP源地址", "参数", "在系内通信", "宽度", "数据链路层协议", "时延",
            "最小地址", "等待时间", "IP目的地址", "值", "到2020年5月", "吞吐量", "大/短", "密钥长度",
            "对象标识符", "序号字段", "序号字段长度", "总时延", "抽象的终点", "最大时延", "最小长度",
            "网络前缀长度", "设置抽象的门", "连接的主机", "通过路由器上网", "长度");
}
