package com.aicourse.service.impl;


import com.aicourse.integration.ContractPaths;
import com.aicourse.service.LLMFineTuneService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 大模型微调（LoRA/PEFT）服务实现类
 * 通过HTTP调用Python微调微服务（端口 8003）
 * <p>
 * Python端需实现的API契约见 docs/Python接口契约-微调服务.md
 */
@Service
@Slf4j
public class LLMFineTuneServiceImpl implements LLMFineTuneService {


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
            @Value("${fine-tune.health-path:" + ContractPaths.FT_HEALTH + "}") String healthPath) {
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


    /** 大模型微调相关操作 */
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


    /** 启动大模型微调 */
    @Override
    public String startFineTune(Long courseId, String baseModel, String config) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.post()
                        .uri(ContractPaths.FT_START)
                        .body(Map.of("course_id", courseId, "base_model", baseModel, "config", config))
                        .retrieve().body(String.class), "启动微调任务");
    }


    /** 查询大模型微调 */
    @Override
    public String getTaskStatus(String taskId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.get()
                        .uri(ContractPaths.FT_TASK_STATUS, taskId)
                        .retrieve().body(String.class), "查询微调状态");
    }


    /** 查询大模型微调 */
    @Override
    public String listTasks(Long courseId) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() -> {
            String uri = courseId != null
                    ? ContractPaths.FT_TASKS + "?course_id=" + courseId
                    : ContractPaths.FT_TASKS;
            return restClient.get().uri(uri).retrieve().body(String.class);
        }, "查询微调任务列表");
    }


    /** 取消大模型微调 */
    @Override
    public String cancelTask(String taskId) {
        log.info("取消大模型微调");

        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.post()
                        .uri(ContractPaths.FT_TASK_CANCEL, taskId)
                        .retrieve().body(String.class), "取消微调任务");
    }


    /** 大模型微调相关操作 */
    @Override
    public String inferWithFineTuned(String taskId, String prompt, String params) {
        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.post()
                        .uri(ContractPaths.FT_INFER, taskId)
                        .body(Map.of("prompt", prompt, "params", params))
                        .retrieve().body(String.class), "微调模型推理");
    }


    /** 导出大模型微调 */
    @Override
    public String exportWeights(String taskId) {
        log.info("导出大模型微调");

        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.get()
                        .uri(ContractPaths.FT_TASK_EXPORT, taskId)
                        .retrieve().body(String.class), "导出LoRA权重");
    }


    /** 删除大模型微调 */
    @Override
    public String deleteTask(String taskId) {
        log.info("删除大模型微调");

        if (!enabled) return SERVICE_UNAVAILABLE;
        return executeWithRetry(() ->
                restClient.delete()
                        .uri(ContractPaths.FT_TASK, taskId)
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
                    try { Thread.sleep(1000L * (i + 1)); } catch (InterruptedException ie) {
                        log.warn("重试等待被中断", ie);
                        Thread.currentThread().interrupt();
                    }
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