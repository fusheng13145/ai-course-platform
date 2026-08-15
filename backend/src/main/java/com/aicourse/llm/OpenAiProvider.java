package com.aicourse.llm;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OpenAI 供应商（GPT 系列）。兼容 OpenAI 协议，可经 {@code ai.provider=openai} 切换。
 */
@Component
public class OpenAiProvider extends OpenAiCompatibleProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);

    public OpenAiProvider(
            @Value("${ai.openai.api-key:}") String apiKey,
            @Value("${ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${ai.openai.model:gpt-4o-mini}") String model,
            @Value("${ai.connect-timeout:5}") int connectTimeout,
            @Value("${ai.read-timeout:60}") int readTimeout,
            @Value("${ai.max-retries:2}") int maxRetries) {
        super("openai", apiKey, baseUrl, model, connectTimeout, readTimeout, maxRetries);
        log.debug("OpenAiProvider 初始化完成, base-url={}", baseUrl);
    }
}
