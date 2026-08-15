package com.aicourse.llm;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 供应商（默认）。兼容 OpenAI 协议，base-url 见 {@code ai.deepseek.base-url}。
 */
@Component
public class DeepSeekProvider extends OpenAiCompatibleProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekProvider.class);

    public DeepSeekProvider(
            @Value("${ai.deepseek.api-key:}") String apiKey,
            @Value("${ai.deepseek.base-url:https://api.deepseek.com/v1}") String baseUrl,
            @Value("${ai.deepseek.model:deepseek-chat}") String model,
            @Value("${ai.connect-timeout:5}") int connectTimeout,
            @Value("${ai.read-timeout:60}") int readTimeout,
            @Value("${ai.max-retries:2}") int maxRetries) {
        super("deepseek", apiKey, baseUrl, model, connectTimeout, readTimeout, maxRetries);
        log.debug("DeepSeekProvider 初始化完成, base-url={}", baseUrl);
    }
}
