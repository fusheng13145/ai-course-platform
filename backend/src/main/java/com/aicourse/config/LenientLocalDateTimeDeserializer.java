package com.aicourse.config;

import com.aicourse.util.LenientDateTimeParser;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 宽松的 {@link LocalDateTime} 反序列化器：兼容 ISO-8601 与 {@code yyyy-MM-dd HH:mm:ss} 等入参。
 *
 * <p><b>修复记录（2026-09-14，P2）</b>：{@code application.yaml} 声明
 * {@code spring.jackson.date-format: yyyy-MM-dd HH:mm:ss}，但该配置<b>只作用于
 * {@code java.util.Date}</b>；{@code java.time.*} 由 {@code JavaTimeModule} 处理，默认只接受 ISO-8601。
 * 结果：按配置文件「看起来正确」的 {@code "2026-09-14 16:00:00"} 提交课程/作业日期字段，
 * 实际返回 400「请求格式错误」（出参又按 ISO 序列化，出入参口径不一致），
 * 属于极易踩中的集成陷阱。此处显式支持多格式，消除该差异。</p>
 *
 * <p>支持：ISO_LOCAL_DATE_TIME（推荐）、{@code yyyy-MM-dd HH:mm:ss}、{@code yyyy-MM-dd HH:mm}、
 * {@code yyyy-MM-dd}（取当日 00:00）、以及毫秒时间戳（数字）。空串/空白 → {@code null}。</p>
 *
 * <p><b>重构记录（2026-10-30，B-13 ⑨）</b>：解析逻辑下沉到 {@link LenientDateTimeParser}，
 * 与 {@code TaskController#extendDeadline} 共用同一实现，避免两套时间口径再次漂移。
 * 本类只保留 Jackson 相关的「取 token / 造错」职责，对外行为与重构前完全一致。</p>
 */
public class LenientLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return LenientDateTimeParser.parseEpochMilli(p.getLongValue());
        }
        String text = p.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LenientDateTimeParser.parse(text);
        } catch (IllegalArgumentException e) {
            throw InvalidFormatException.from(p,
                    "时间格式无法解析: " + text.trim() + "；" + LenientDateTimeParser.FORMAT_HINT,
                    text.trim(), LocalDateTime.class);
        }
    }
}
