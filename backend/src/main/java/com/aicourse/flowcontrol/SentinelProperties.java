package com.aicourse.flowcontrol;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 流量控制与熔断降级配置绑定。
 * <p>
 * 所有阈值均可通过 {@code sentinel.*} 命名空间在 YAML / 环境变量中配置，做到「规则即配置」，
 * 便于按环境（开发 / 生产）差异化调优，无需改代码。
 * <p>
 * 设计说明：
 * <ul>
 *   <li>{@code flowRules} / {@code degradeRules} 中的 {@code grade} 取值沿用 Sentinel 原生常量语义：
 *       流控 grade —— 1=QPS，0=并发线程数；熔断 grade —— 0=慢调用比例，1=异常比例，2=异常数。</li>
 *   <li>{@code count} 含义随 grade 变化：流控时为阈值（QPS 或线程数）；
 *       熔断 RT 策略时为慢调用阈值(ms)，异常比例时为 0~1 的比例，异常数时为异常个数。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "sentinel")
public class SentinelProperties {

    /** 总开关：关闭后不加载任何规则、过滤器与切面均降级为透传（零开销）。 */
    private boolean enabled = true;

    /** 边缘（URL 级）流控过滤器配置。 */
    private Web web = new Web();

    /** 流控规则列表（按资源名精确匹配）。 */
    private List<FlowRuleDef> flowRules = new ArrayList<>();

    /** 熔断降级规则列表（按资源名精确匹配）。 */
    private List<DegradeRuleDef> degradeRules = new ArrayList<>();

    /**
     * 规则持久化目录（相对进程工作目录，或绝对路径）。应用启动时将 YAML 声明的规则落盘为
     * {@code flow-rules.json} / {@code degrade-rules.json}，并注册文件数据源热刷新；
     * 运行期直接编辑这两个文件即可动态调整规则（无需重启），为接入 Dashboard/Nacos 预留同一抽象。
     */
    private String rulesDir = "sentinel-rules";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Web getWeb() {
        return web;
    }

    public void setWeb(Web web) {
        this.web = web;
    }

    public List<FlowRuleDef> getFlowRules() {
        return flowRules;
    }

    public void setFlowRules(List<FlowRuleDef> flowRules) {
        this.flowRules = flowRules;
    }

    public List<DegradeRuleDef> getDegradeRules() {
        return degradeRules;
    }

    public void setDegradeRules(List<DegradeRuleDef> degradeRules) {
        this.degradeRules = degradeRules;
    }

    public String getRulesDir() {
        return rulesDir;
    }

    public void setRulesDir(String rulesDir) {
        this.rulesDir = rulesDir;
    }

    /** 边缘过滤器相关配置。 */
    public static class Web {
        /** 是否启用 URL 级流控过滤器（对每个 /api/** 请求定义 METHOD:path 资源并校验规则）。 */
        private boolean enabled = true;

        /**
         * 全局入口限流（QPS）。对单一共享资源 {@code sentinel:global} 限流，遏制整站洪泛；
         * 0 表示不启用全局入口限流（仍保留按 URL 资源的规则）。
         */
        private int globalQps = 0;

        /** 排除路径前缀（不进入 Sentinel 统计与流控），如 actuator 探活、WebSocket、Swagger。 */
        private List<String> excludePaths = new ArrayList<>(List.of(
                "/api/actuator", "/actuator",
                "/api/ws", "/ws",
                "/swagger-ui", "/v3/api-docs", "/api-docs", "/api-docs.html"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getGlobalQps() {
            return globalQps;
        }

        public void setGlobalQps(int globalQps) {
            this.globalQps = globalQps;
        }

        public List<String> getExcludePaths() {
            return excludePaths;
        }

        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths;
        }
    }

    /** 流控规则定义（映射为 {@code com.alibaba.csp.sentinel.slots.block.flow.FlowRule}）。 */
    public static class FlowRuleDef {
        /** 资源名（与 SentinelWebFilter 生成的 METHOD:path 或 @SentinelResource 指定值一致）。 */
        private String resource;
        /** 限流维度：1=QPS，0=并发线程数。默认 QPS。 */
        private int grade = 1;
        /** 阈值（QPS 或线程数）。 */
        private double count = 200;
        /** 流控针对的调用来源，default 表示不区分来源。 */
        private String limitApp = "default";
        /** 调用关系限流策略：0=直接，1=关联，2=链路。默认直接。 */
        private int strategy = 0;
        /** 流控效果：0=快速失败，1=WarmUp，2=排队等待。默认快速失败。 */
        private int controlBehavior = 0;
        /** 排队等待最大时长(ms)，仅 controlBehavior=2 时生效。 */
        private int maxQueueingTimeMs = 500;

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public int getGrade() {
            return grade;
        }

        public void setGrade(int grade) {
            this.grade = grade;
        }

        public double getCount() {
            return count;
        }

        public void setCount(double count) {
            this.count = count;
        }

        public String getLimitApp() {
            return limitApp;
        }

        public void setLimitApp(String limitApp) {
            this.limitApp = limitApp;
        }

        public int getStrategy() {
            return strategy;
        }

        public void setStrategy(int strategy) {
            this.strategy = strategy;
        }

        public int getControlBehavior() {
            return controlBehavior;
        }

        public void setControlBehavior(int controlBehavior) {
            this.controlBehavior = controlBehavior;
        }

        public int getMaxQueueingTimeMs() {
            return maxQueueingTimeMs;
        }

        public void setMaxQueueingTimeMs(int maxQueueingTimeMs) {
            this.maxQueueingTimeMs = maxQueueingTimeMs;
        }
    }

    /** 熔断降级规则定义（映射为 {@code com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule}）。 */
    public static class DegradeRuleDef {
        /** 资源名。 */
        private String resource;
        /** 熔断策略：0=慢调用比例，1=异常比例，2=异常数。默认慢调用比例。 */
        private int grade = 0;
        /**
         * 阈值：grade=0 时为慢调用阈值(ms)；grade=1 时为异常比例(0~1)；grade=2 时为异常数。
         */
        private double count = 3000;
        /** 熔断时长(s)，过后进入探测恢复。 */
        private int timeWindow = 10;
        /** 触发熔断的最小请求数（统计窗口内低于此值不熔断）。 */
        private int minRequestAmount = 5;
        /** 统计窗口时长(ms)。 */
        private int statIntervalMs = 10000;
        /** 慢调用比例阈值(0~1)，仅 grade=0 时生效。 */
        private double slowRatioThreshold = 0.5;

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public int getGrade() {
            return grade;
        }

        public void setGrade(int grade) {
            this.grade = grade;
        }

        public double getCount() {
            return count;
        }

        public void setCount(double count) {
            this.count = count;
        }

        public int getTimeWindow() {
            return timeWindow;
        }

        public void setTimeWindow(int timeWindow) {
            this.timeWindow = timeWindow;
        }

        public int getMinRequestAmount() {
            return minRequestAmount;
        }

        public void setMinRequestAmount(int minRequestAmount) {
            this.minRequestAmount = minRequestAmount;
        }

        public int getStatIntervalMs() {
            return statIntervalMs;
        }

        public void setStatIntervalMs(int statIntervalMs) {
            this.statIntervalMs = statIntervalMs;
        }

        public double getSlowRatioThreshold() {
            return slowRatioThreshold;
        }

        public void setSlowRatioThreshold(double slowRatioThreshold) {
            this.slowRatioThreshold = slowRatioThreshold;
        }
    }
}
