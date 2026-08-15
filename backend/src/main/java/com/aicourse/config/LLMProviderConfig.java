package com.aicourse.config;

import com.aicourse.llm.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM 供应商选择配置
 * <p>
 * 依据 {@code ai.provider} 注入对应的 {@link LLMProvider} 实现（deepseek / openai / qwen）。
 * 业务层 {@code AIService} 仅依赖 {@link LLMProvider} 接口，不感知具体供应商，从而实现热切换。
 */
@Configuration
public class LLMProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(LLMProviderConfig.class);

    @Value("${ai.provider:deepseek}")
    private String provider;

    /**
     * 注入路由型供应商：运行时按 {@code system_configs.ai.provider} 动态选择底层实现，
     * 实现管理员在配置页热切换模型（无需重启）。
     */
    @Bean
    public LLMProvider llmProvider(RoutingLLMProvider router) {
        log.info("[LLMProviderConfig] 注入 LLM 供应商路由实现，启动默认 provider={}", provider);
        return router;
    }
}
