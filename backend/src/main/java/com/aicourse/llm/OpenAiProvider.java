package com.aicourse.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OpenAI 供应商（GPT 系列）。兼容 OpenAI 协议，可经 {@code ai.provider=openai} 切换。
 */
@Component
public class OpenAiProvider extends OpenAiCompatibleProvider {

    public OpenAiProvider(
            @Value("${ai.openai.api-key:}") String apiKey,
            @Value("${ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${ai.openai.model:gpt-4o-mini}") String model,
            @Value("${ai.connect-timeout:5}") int connectTimeout,
            @Value("${ai.read-timeout:60}") int readTimeout,
            @Value("${ai.max-retries:2}") int maxRetries) {
        super("openai", apiKey, baseUrl, model, connectTimeout, readTimeout, maxRetries);
    }
}
