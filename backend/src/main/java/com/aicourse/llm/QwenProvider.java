package com.aicourse.llm;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 通义千问(Qwen) 供应商。阿里云 DashScope 提供 OpenAI 兼容模式（{@code /compatible-mode/v1}），
 * 可经 {@code ai.provider=qwen} 切换。
 */
@Component
public class QwenProvider extends OpenAiCompatibleProvider {

    private static final Logger log = LoggerFactory.getLogger(QwenProvider.class);

    public QwenProvider(
            @Value("${ai.qwen.api-key:}") String apiKey,
            @Value("${ai.qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
            @Value("${ai.qwen.model:qwen-plus}") String model,
            @Value("${ai.connect-timeout:5}") int connectTimeout,
            @Value("${ai.read-timeout:60}") int readTimeout,
            @Value("${ai.max-retries:2}") int maxRetries) {
        super("qwen", apiKey, baseUrl, model, connectTimeout, readTimeout, maxRetries);
        log.debug("QwenProvider 初始化完成, base-url={}", baseUrl);
    }
}
