package com.aicourse.service.impl;

import com.aicourse.service.LLMFineTuneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;

/**
 * 大模型微调（LoRA/PEFT）服务实现类
 * 通过HTTP调用Python微调微服务（端口 8003）
 * <p>
 * Python端需实现的API契约见 docs/Python接口契约-微调服务.md
 */
@Service
public class LLMFineTuneServiceImpl implements LLMFineTuneService {

    private static final Logger log = LoggerFactory.getLogger(LLMFineTuneServiceImpl.class);
    private static final String SERVICE_UNAVAILABLE = "{\"error\":\"微调服务暂不可用\",\"code\":503}";

    private final RestClient restClient;
    private final boolean enabled;
    private final int maxRetries;
    private final String healthPath;

    public LLMFineTuneServiceImpl(
            @Value("${fine-tune.enabled:false}") boolean enabled,
            @Value("${fine-tune.base-url:http://localhost:8003}") String baseUrl,
            @Value("${fine-tune.connect-timeout:5}") int connectTimeout,
            @Value("${fine-tune.read-timeout:300}") int readTimeout,
            @Value("${fine-tune.max-retries:1}") int maxRetries,
            @Value("${fine-tune.health-path:/api/v1/fine-tune/health}") String healthPath) {
        this.enabled = enabled;
        this.maxRetries = maxRetries;
        this.healthPath = healthPath;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public boolean healthCheck() {
        if (!enabled) return false;
        try {
            restClient.get().uri(healthPath).retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.debug("微调服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String startFineTune(Long courseId, String baseModel, String config) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.post()
                        .uri("/api/v1/fine-tune/start")
                        .body(Map.of("course_id", courseId, "base_model", baseModel, "config", config))
                        .retrieve().body(String.class), "启动微调任务");
    }

    @Override
    public String getTaskStatus(String taskId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.get()
                        .uri("/api/v1/fine-tune/tasks/{taskId}/status", taskId)
                        .retrieve().body(String.class), "查询微调状态");
    }

    @Override
    public String listTasks(Long courseId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() -> {
            String uri = courseId != null
                    ? "/api/v1/fine-tune/tasks?course_id=" + courseId
                    : "/api/v1/fine-tune/tasks";
            return restClient.get().uri(uri).retrieve().body(String.class);
        }, "查询微调任务列表");
    }

    @Override
    public String cancelTask(String taskId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.post()
                        .uri("/api/v1/fine-tune/tasks/{taskId}/cancel", taskId)
                        .retrieve().body(String.class), "取消微调任务");
    }

    @Override
    public String inferWithFineTuned(String taskId, String prompt, String params) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.post()
                        .uri("/api/v1/fine-tune/infer/{taskId}", taskId)
                        .body(Map.of("prompt", prompt, "params", params))
                        .retrieve().body(String.class), "微调模型推理");
    }

    @Override
    public String exportWeights(String taskId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.get()
                        .uri("/api/v1/fine-tune/tasks/{taskId}/export", taskId)
                        .retrieve().body(String.class), "导出LoRA权重");
    }

    @Override
    public String deleteTask(String taskId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.delete()
                        .uri("/api/v1/fine-tune/tasks/{taskId}", taskId)
                        .retrieve().body(String.class), "删除微调任务");
    }

    /**
     * 带重试的HTTP调用执行器
     */
    private String executeWithRetry(HttpCall call, String operation) {
        Exception lastException = null;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                return call.execute();
            } catch (Exception e) {
                lastException = e;
                if (i < maxRetries && !(e instanceof SocketTimeoutException)) {
                    log.warn("{}失败，第{}次重试: {}", operation, i + 1, e.getMessage());
                    try { Thread.sleep(1000L * (i + 1)); } catch (InterruptedException ignored) {}
                }
            }
        }
        log.error("{}多次失败，已达最大重试次数", operation, lastException);
        return SERVICE_UNAVAILABLE;
    }

    @FunctionalInterface
    private interface HttpCall {
        String execute() throws Exception;
    }
}