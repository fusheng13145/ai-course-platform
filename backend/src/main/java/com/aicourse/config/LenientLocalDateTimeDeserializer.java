package com.aicourse.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 宽松的 {@link LocalDateTime} 反序列化器：兼容 ISO-8601 与 {@code yyyy-MM-dd HH:mm:ss} 两种入参。
 *
 * <p><b>修复记录（2026-09-14，P2）</b>：{@code application.yaml} 声明
 * {@code spring.jackson.date-format: yyyy-MM-dd HH:mm:ss}，但该配置<b>只作用于
 * {@code java.util.Date}</b>；{@code java.time.*} 由 {@code JavaTimeModule} 处理，默认只接受 ISO-8601。
 * 结果：按配置文件「看起来正确」的 {@code "2026-09-14 16:00:00"} 提交课程/作业日期字段，
 * 实际返回 400「请求格式错误」（出参又按 ISO 序列化，出入参口径不一致），
 * 属于极易踩中的集成陷阱。此处显式支持多格式，消除该差异。</p>
 *
 * <p>支持：ISO_LOCAL_DATE_TIME（{@code 2026-09-14T16:00:00[.SSS]}）、
 * {@code yyyy-MM-dd HH:mm:ss}、{@code yyyy-MM-dd HH:mm}、{@code yyyy-MM-dd}（取当日 00:00）、
 * 以及毫秒时间戳（数字）。空串/空白 → {@code null}。</p>
 */
public class LenientLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    private static final DateTimeFormatter SPACE_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SPACE_MINUTES =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_ONLY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(p.getLongValue()), ZoneId.systemDefault());
        }
        String text = p.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();

        // 1) ISO-8601（含毫秒）
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // 继续尝试其它格式
        }
        // 2) yyyy-MM-dd HH:mm:ss
        try {
            return LocalDateTime.parse(value, SPACE_SECONDS);
        } catch (DateTimeParseException ignored) {
            // 继续尝试其它格式
        }
        // 3) yyyy-MM-dd HH:mm
        try {
            return LocalDateTime.parse(value, SPACE_MINUTES);
        } catch (DateTimeParseException ignored) {
            // 继续尝试其它格式
        }
        // 4) yyyy-MM-dd（当日零点）
        try {
            return LocalDate.parse(value, DATE_ONLY).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            // 落入统一报错
        }
        throw InvalidFormatException.from(p,
                "时间格式无法解析，支持 ISO-8601(2026-09-14T16:00:00) 或 yyyy-MM-dd HH:mm:ss: " + value,
                value, LocalDateTime.class);
    }
}
