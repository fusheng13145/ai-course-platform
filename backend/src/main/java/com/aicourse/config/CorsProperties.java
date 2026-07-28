package com.aicourse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 跨域允许域名白名单配置（绑定 cors.allowed-origins）
 * <p>
 * 将原先硬编码在 {@link SecurityConfig} 中的 localhost 列表改为配置驱动，
 * 便于测试/生产等多环境部署时通过配置文件或环境变量注入，而无需改代码。
 */
@Component
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

    private List<String> allowedOrigins;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
