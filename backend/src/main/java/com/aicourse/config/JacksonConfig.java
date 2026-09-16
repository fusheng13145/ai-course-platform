package com.aicourse.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonProperties;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 显式注册 Jackson JavaTimeModule，确保 java.time.* 类型（如 ApiResponse.timestamp）
 * 可以被正常序列化，避免 "Java 8 date/time type not supported by default" 异常。
 * 同时沿用 application.yaml 中的 date-format / time-zone 配置。
 */
@Configuration
public class JacksonConfig {

    private static final Logger log = LoggerFactory.getLogger(JacksonConfig.class);

    @Autowired
    private JacksonProperties jacksonProperties;

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        log.info("[JacksonConfig] 注册 Jackson 定制器：JavaTimeModule + 宽松 LocalDateTime 反序列化 + date-format/time-zone（来自 application.yaml）");
        return builder -> {
            // JavaTimeModule 负责 java.time.* 的（反）序列化；
            // SimpleModule 额外为 LocalDateTime 注册宽松反序列化器，
            // 使 yyyy-MM-dd HH:mm:ss（配置文件声明的格式）与 ISO-8601 两种入参都被接受，
            // 消除「按配置格式提交却 400」的契约陷阱。详见 LenientLocalDateTimeDeserializer。
            SimpleModule lenientDateTime = new SimpleModule();
            lenientDateTime.addDeserializer(LocalDateTime.class, new LenientLocalDateTimeDeserializer());
            builder.modules(new JavaTimeModule(), lenientDateTime);
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            if (jacksonProperties.getDateFormat() != null) {
                builder.dateFormat(new SimpleDateFormat(jacksonProperties.getDateFormat()));
            }
            if (jacksonProperties.getTimeZone() != null) {
                builder.timeZone(jacksonProperties.getTimeZone());
            }
        };
    }
}
