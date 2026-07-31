package com.aicourse.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Python 微服务（知识图谱 / RAG / 微调）健康指示器。
 *
 * <p>仅对 {@code *.enabled=true} 的服务执行轻量级 GET 健康检查，超时 2 秒；
 * 未启用或不可达的服务标记为 UNKNOWN/DOWN。用于 readiness 探针聚合与运维告警。</p>
 */
@Component
@Slf4j
public class PythonServicesHealthIndicator implements HealthIndicator {

    private final RestClient restClient;
    private final boolean kgEnabled;
    private final String kgBaseUrl;
    private final String kgHealthPath;
    private final boolean ragEnabled;
    private final String ragBaseUrl;
    private final String ragHealthPath;
    private final boolean ftEnabled;
    private final String ftBaseUrl;
    private final String ftHealthPath;

    public PythonServicesHealthIndicator(
            @Value("${knowledge-graph.enabled:true}") boolean kgEnabled,
            @Value("${knowledge-graph.base-url:http://localhost:8001}") String kgBaseUrl,
            @Value("${knowledge-graph.health-path:/api/v1/graph/health}") String kgHealthPath,
            @Value("${rag.enabled:true}") boolean ragEnabled,
            @Value("${rag.base-url:http://localhost:8002}") String ragBaseUrl,
            @Value("${rag.health-path:/api/v1/rag/health}") String ragHealthPath,
            @Value("${fine-tune.enabled:false}") boolean ftEnabled,
            @Value("${fine-tune.base-url:http://localhost:8003}") String ftBaseUrl,
            @Value("${fine-tune.health-path:/api/v1/fine-tune/health}") String ftHealthPath) {
        this.kgEnabled = kgEnabled;
        this.kgBaseUrl = kgBaseUrl;
        this.kgHealthPath = kgHealthPath;
        this.ragEnabled = ragEnabled;
        this.ragBaseUrl = ragBaseUrl;
        this.ragHealthPath = ragHealthPath;
        this.ftEnabled = ftEnabled;
        this.ftBaseUrl = ftBaseUrl;
        this.ftHealthPath = ftHealthPath;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean allUp = true;
        boolean anyChecked = false;

        HealthStatus kg = checkService("knowledge-graph", kgEnabled, kgBaseUrl, kgHealthPath);
        details.put("knowledge-graph", kg.detail());
        anyChecked |= kg.checked();
        allUp &= !kg.checked() || kg.up();

        HealthStatus rag = checkService("rag", ragEnabled, ragBaseUrl, ragHealthPath);
        details.put("rag", rag.detail());
        anyChecked |= rag.checked();
        allUp &= !rag.checked() || rag.up();

        HealthStatus ft = checkService("fine-tune", ftEnabled, ftBaseUrl, ftHealthPath);
        details.put("fine-tune", ft.detail());
        anyChecked |= ft.checked();
        allUp &= !ft.checked() || ft.up();

        // 全部未启用时视为 UP（无 Python 服务依赖），任一已启用服务 DOWN 才判 DOWN
        Health.Builder builder = (!anyChecked || allUp) ? Health.up() : Health.down();
        details.forEach(builder::withDetail);
        return builder.build();
    }

    private HealthStatus checkService(String name, boolean enabled, String baseUrl, String healthPath) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("enabled", enabled);
        detail.put("baseUrl", baseUrl);
        if (!enabled) {
            detail.put("status", "DISABLED");
            // 未启用的服务不参与健康判定：既不算"已检查"也不算"故障"，
            // 避免所有 Python 服务都关闭时整体健康状态被误判为 DOWN。
            return new HealthStatus(true, false, false, detail);
        }
        try {
            restClient.get()
                    .uri(baseUrl + healthPath)
                    .retrieve()
                    .toBodilessEntity();
            detail.put("status", "UP");
            return new HealthStatus(true, true, true, detail);
        } catch (Exception e) {
            log.warn("{} 健康检查失败: {}", name, e.getMessage());
            detail.put("status", "DOWN");
            detail.put("error", e.getClass().getSimpleName());
            return new HealthStatus(false, true, true, detail);
        }
    }

    private record HealthStatus(boolean up, boolean checked, boolean enabled, Map<String, Object> detail) {
    }
}
