package com.aicourse.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 业务依赖健康指标化服务。
 *
 * <p>将 {@code /actuator/health} 暴露的 4 个自定义业务探针（database / redis / llmProvider /
 * pythonServices）状态映射为 Prometheus Gauge {@code aicourse_health_status{component}}，
 * 使「业务依赖可用性」进入指标与告警体系，而非仅停留在健康端点。</p>
 *
 * <p>取值语义：UP=1.0，DOWN=0.0，其它状态（UNKNOWN / OUT_OF_SERVICE / DISABLED 等）=0.5。
 * 这样 Grafana 可直观呈现各依赖红/绿，Prometheus 可对任一依赖 DOWN 触发 critical 告警。</p>
 *
 * <p>实现沿用 {@link BusinessMetricsService} 的模式：Gauge 在构造期一次性注册（绑定 AtomicReference&lt;Double&gt;，
 * 因 AtomicLong 无法保留 0.5 半状态，故用 Double 容器），后续仅更新底层值，避免重复注册导致 Meter 泄漏。</p>
 */
@Service
public class HealthMetricsService {

    private static final Logger log = LoggerFactory.getLogger(HealthMetricsService.class);

    /** 纳入监控的白名单业务依赖组件（与 *HealthIndicator bean 名去后缀一致）。 */
    private static final List<String> COMPONENTS = List.of("database", "redis", "llmProvider", "pythonServices");

    /** 刷新周期：60s。依赖健康状态变化频率低，60s 足够且避免探测抖动。 */
    private static final long REFRESH_MS = 60_000L;

    private final MeterRegistry meterRegistry;
    private final Map<String, HealthIndicator> indicators;

    /** component → 当前健康值容器（Gauge 绑定到 AtomicReference<Double>，可保留 0.5 半状态） */
    private final Map<String, AtomicReference<Double>> states = new ConcurrentHashMap<>();

    public HealthMetricsService(MeterRegistry meterRegistry, Map<String, HealthIndicator> indicators) {
        this.meterRegistry = meterRegistry;
        this.indicators = indicators;
        for (String component : COMPONENTS) {
            AtomicReference<Double> value = new AtomicReference<>(1.0);
            states.put(component, value);
            Gauge.builder("aicourse_health_status", value, AtomicReference::get)
                    .tag("component", component)
                    .description("业务依赖健康状态：1=UP, 0=DOWN, 0.5=UNKNOWN/未探测/未启用")
                    .register(meterRegistry);
        }
    }

    /**
     * 首次启动即刷新一次，避免仪表盘在采集周期到来前长期显示初始值。
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 周期性刷新各业务依赖健康状态到 Gauge。
     */
    @Scheduled(fixedDelay = REFRESH_MS)
    public void refresh() {
        for (String component : COMPONENTS) {
            HealthIndicator indicator = indicators.get(component + "HealthIndicator");
            double value = indicator == null ? 0.5 : mapStatus(indicator.health().getStatus().getCode());
            AtomicReference<Double> holder = states.get(component);
            if (holder != null) {
                holder.set(value);
            }
        }
    }

    /**
     * 将 Spring 健康状态码映射为数值指标：UP→1，DOWN→0，其它（UNKNOWN/OUT_OF_SERVICE/DISABLED）→0.5。
     */
    private double mapStatus(String code) {
        if (code == null) {
            return 0.5;
        }
        return switch (code) {
            case "UP" -> 1.0;
            case "DOWN" -> 0.0;
            default -> 0.5;
        };
    }
}
