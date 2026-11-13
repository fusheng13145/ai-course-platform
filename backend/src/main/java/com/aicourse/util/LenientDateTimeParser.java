package com.aicourse.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 宽松的日期时间文本解析：**全局唯一的入参时间口径**。
 *
 * <p><b>为什么要有这个类（2026-10-30，B-13 ⑨）</b>：项目里原本有两套时间入参口径——
 * Jackson 反序列化走多格式宽松，而 {@code TaskController#extendDeadline} 走手写
 * {@code LocalDateTime.parse}（只认 ISO-8601）。结果是同一个控制器内 {@code dueDate}
 * 接受 {@code "2026-10-30 23:59:59"}，而 {@code extend-deadline} 的 {@code newDueDate}
 * 对同样的字符串必返回 400。把解析抽到这里，两处共用同一实现，从结构上消除再次漂移。</p>
 *
 * <p><b>口径裁定（2026-11-01，项目主）</b>：方向为「放宽」——ISO-8601 是**契约推荐格式**，
 * 其余三种是**兼容格式**（历史客户端按 {@code spring.jackson.date-format} 提交）。
 * 不把全局收紧成 ISO-only，是因为那会让 2026-09-14 修过的
 * 「按 yaml 配置格式提交却 400」的陷阱复活。</p>
 *
 * <p>支持格式（按优先级）：</p>
 * <ol>
 *   <li>ISO-8601：{@code 2026-10-30T23:59:59}、{@code 2026-10-30T23:59:59.123}（推荐）</li>
 *   <li>{@code yyyy-MM-dd HH:mm:ss}（兼容）</li>
 *   <li>{@code yyyy-MM-dd HH:mm}（兼容）</li>
 *   <li>{@code yyyy-MM-dd}（兼容，取当日 00:00）</li>
 * </ol>
 *
 * <p>空串/空白返回 {@code null}；无法解析时抛 {@link IllegalArgumentException}，
 * 调用方负责翻译成 400。</p>
 */
public final class LenientDateTimeParser {

    /** 契约推荐格式的示例，写进错误提示，避免各处自拟措辞。 */
    public static final String FORMAT_HINT =
            "推荐 ISO-8601（如 2026-10-30T23:59:59）；兼容 yyyy-MM-dd HH:mm:ss、yyyy-MM-dd HH:mm、yyyy-MM-dd";

    private static final DateTimeFormatter SPACE_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SPACE_MINUTES =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_ONLY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private LenientDateTimeParser() {
    }

    /**
     * 解析时间文本。
     *
     * @param text 入参文本，可为 null
     * @return 解析结果；{@code text} 为 null/空白时返回 {@code null}
     * @throws IllegalArgumentException 所有格式均不匹配
     */
    public static LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();

        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // 继续尝试兼容格式
        }
        try {
            return LocalDateTime.parse(value, SPACE_SECONDS);
        } catch (DateTimeParseException ignored) {
            // 继续尝试兼容格式
        }
        try {
            return LocalDateTime.parse(value, SPACE_MINUTES);
        } catch (DateTimeParseException ignored) {
            // 继续尝试兼容格式
        }
        try {
            return LocalDate.parse(value, DATE_ONLY).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            // 落入统一报错
        }
        throw new IllegalArgumentException("时间格式无法解析: " + value + "；" + FORMAT_HINT);
    }

    /**
     * 解析毫秒时间戳（JSON 数字入参场景，如 {@code 1793807999000}）。
     */
    public static LocalDateTime parseEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }
}
