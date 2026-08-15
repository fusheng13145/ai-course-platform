package com.aicourse.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonProperties;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.text.SimpleDateFormat;
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
        log.info("[JacksonConfig] 注册 Jackson 定制器：JavaTimeModule + date-format/time-zone（来自 application.yaml）");
        return builder -> {
            builder.modules(new JavaTimeModule());
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
