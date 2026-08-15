package com.aicourse.flowcontrol;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.datasource.FileRefreshableDataSource;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 集成配置：规则初始化 + 注解切面注册。
 * <p>
 * 职责：
 * <ol>
 *   <li>将 {@link SentinelProperties} 中声明的流控 / 熔断规则转换为 Sentinel 原生
 *       {@link FlowRule} / {@link DegradeRule}，加载到内存规则管理器，并<b>持久化到本地文件</b>
 *       （{@code sentinel.rules-dir} 下的 {@code flow-rules.json} / {@code degrade-rules.json}）；
 *       同时注册文件数据源，运行期直接编辑这两个文件即可<b>热刷新</b>规则（无需重启）。
 *       为后续接入 Dashboard / Nacos 预留同一抽象（换数据源实现即可）。</li>
 *   <li>注册 {@link SentinelResourceAspect}，使业务方法可用 {@code @SentinelResource} 声明资源、
 *       流控与熔断（本项目主要以下游服务方法级熔断形式落地，见 {@code RAGServiceImpl}）。</li>
 *   <li>master switch：{@code sentinel.enabled=false} 时整体降级为透传，不加载任何规则。</li>
 * </ol>
 */
@Configuration
@EnableConfigurationProperties(SentinelProperties.class)
public class SentinelConfig {

    /** 全局入口限流共享资源名（由 SentinelWebFilter 对每个请求开启）。 */
    public static final String GLOBAL_RESOURCE = "sentinel:global";

    private static final Logger log = LoggerFactory.getLogger(SentinelConfig.class);

    /** 持久化文件名（位于 {@code sentinel.rules-dir} 目录下）。 */
    private static final String FLOW_RULES_FILE = "flow-rules.json";
    private static final String DEGRADE_RULES_FILE = "degrade-rules.json";

    /** 忽略规则类上 Jackson 不识别的字段（Sentinel 版本演进引入的新字段），保证反序列化稳健。 */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final SentinelProperties properties;

    /** 文件数据源引用，便于容器关闭时释放轮询线程（@PreDestroy）。 */
    private FileRefreshableDataSource<List<FlowRule>> flowDataSource;
    private FileRefreshableDataSource<List<DegradeRule>> degradeDataSource;

    public SentinelConfig(SentinelProperties properties) {
        this.properties = properties;
    }

    /**
     * 注解式资源保护切面：业务方法标注 {@code @SentinelResource} 后由本切面织入流控 / 熔断逻辑。
     */
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    /**
     * 启动即加载规则（master switch 开启时）。
     * <p>放在构造后执行，确保配置已绑定完成；规则以「全量替换」方式加载，
     * 多次刷新配置只需再次调用 {@link FlowRuleManager#loadRules} 即可热更新。</p>
     */
    @PostConstruct
    public void initRules() {
        if (!properties.isEnabled()) {
            log.info("[Sentinel] 已禁用（sentinel.enabled=false），不加载任何流控/熔断规则。");
            return;
        }
        List<FlowRule> flowRules = buildFlowRules();
        List<DegradeRule> degradeRules = buildDegradeRules();

        FlowRuleManager.loadRules(flowRules);
        DegradeRuleManager.loadRules(degradeRules);

        log.info("[Sentinel] 流控规则已加载 {} 条，熔断规则已加载 {} 条。",
                flowRules.size(), degradeRules.size());

        // 持久化 + 热刷新：落盘供运维检视/手动调参，并注册文件数据源支持运行期热刷新。
        persistRulesToFile(flowRules, degradeRules);
        registerFileDataSource();
    }

    /**
     * 将当前规则落盘为 JSON 文件（流控 / 熔断各一个）。
     * <p>落盘后运维可直接编辑文件动态调整规则，由 {@link #registerFileDataSource()} 注册的数据源热刷新生效；
     * 应用重启时本方法会用 YAML 声明的规则覆盖文件，保证「配置即代码」的权威性。</p>
     */
    private void persistRulesToFile(List<FlowRule> flowRules, List<DegradeRule> degradeRules) {
        try {
            Path dir = resolveRulesDir();
            Files.createDirectories(dir);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(dir.resolve(FLOW_RULES_FILE).toFile(), flowRules);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(dir.resolve(DEGRADE_RULES_FILE).toFile(), degradeRules);
            log.info("[Sentinel] 规则已持久化到目录: {}", dir.toAbsolutePath());
        } catch (IOException e) {
            // 落盘失败不阻断应用启动：内存规则已通过 loadRules 生效，仅丢失「文件检视/热刷新」能力。
            log.warn("[Sentinel] 规则持久化到文件失败（内存规则仍生效）：{}", e.getMessage());
        }
    }

    /**
     * 注册文件数据源：监听规则文件变更并热刷新到内存管理器。
     * <p>构造前文件已由 {@link #persistRulesToFile} 写入，故首次加载与内存规则一致；
     * 运行期编辑文件后，文件数据源在轮询周期内自动 reload，实现「不重启改规则」。</p>
     */
    private void registerFileDataSource() {
        try {
            Path dir = resolveRulesDir();
            flowDataSource =
                    new FileRefreshableDataSource<>(dir.resolve(FLOW_RULES_FILE).toFile(), flowDecoder());
            degradeDataSource =
                    new FileRefreshableDataSource<>(dir.resolve(DEGRADE_RULES_FILE).toFile(), degradeDecoder());
            FlowRuleManager.register2Property(flowDataSource.getProperty());
            DegradeRuleManager.register2Property(degradeDataSource.getProperty());
            log.info("[Sentinel] 文件数据源已注册，规则文件热刷新已启用（目录: {}）",
                    dir.toAbsolutePath());
        } catch (Exception e) {
            // 文件数据源注册失败不阻断应用：内存规则已通过 loadRules 生效。
            log.warn("[Sentinel] 文件数据源注册失败（内存规则仍生效）：{}", e.getMessage());
        }
    }

    /**
     * 容器关闭时释放文件数据源的轮询线程，避免线程泄漏。
     */
    @PreDestroy
    public void destroy() {
        if (flowDataSource != null) {
            try {
                flowDataSource.close();
            } catch (Exception e) {
                log.debug("[Sentinel] 关闭流控文件数据源异常（可忽略）：{}", e.getMessage());
            }
        }
        if (degradeDataSource != null) {
            try {
                degradeDataSource.close();
            } catch (Exception e) {
                log.debug("[Sentinel] 关闭熔断文件数据源异常（可忽略）：{}", e.getMessage());
            }
        }
    }

    /** 解析规则目录：支持相对路径（相对进程工作目录）或绝对路径。 */
    private Path resolveRulesDir() {
        String dir = properties.getRulesDir();
        Path path = Path.of(dir);
        return path.isAbsolute() ? path : Path.of(System.getProperty("user.dir"), dir);
    }

    /** 流控规则 JSON 反序列化器（文件 → List<FlowRule>）。 */
    private Converter<String, List<FlowRule>> flowDecoder() {
        return s -> {
            try {
                return objectMapper.readValue(s, new TypeReference<List<FlowRule>>() {
                });
            } catch (Exception e) {
                throw new RuntimeException("[Sentinel] 流控规则文件解析失败", e);
            }
        };
    }

    /** 熔断规则 JSON 反序列化器（文件 → List<DegradeRule>）。 */
    private Converter<String, List<DegradeRule>> degradeDecoder() {
        return s -> {
            try {
                return objectMapper.readValue(s, new TypeReference<List<DegradeRule>>() {
                });
            } catch (Exception e) {
                throw new RuntimeException("[Sentinel] 熔断规则文件解析失败", e);
            }
        };
    }

    /**
     * 构建流控规则：
     * 1) 全局入口限流（globalQps>0 时）；
     * 2) YAML 中声明的按资源名精确匹配规则。
     */
    List<FlowRule> buildFlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        SentinelProperties.Web web = properties.getWeb();
        if (web.isEnabled() && web.getGlobalQps() > 0) {
            FlowRule global = new FlowRule();
            global.setResource(GLOBAL_RESOURCE);
            global.setGrade(RuleConstant.FLOW_GRADE_QPS);
            global.setCount(web.getGlobalQps());
            global.setLimitApp("default");
            global.setStrategy(RuleConstant.STRATEGY_DIRECT);
            global.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
            rules.add(global);
        }
        for (SentinelProperties.FlowRuleDef def : properties.getFlowRules()) {
            FlowRule rule = new FlowRule();
            rule.setResource(def.getResource());
            rule.setGrade(def.getGrade());
            rule.setCount(def.getCount());
            rule.setLimitApp(def.getLimitApp());
            rule.setStrategy(def.getStrategy());
            rule.setControlBehavior(def.getControlBehavior());
            rule.setMaxQueueingTimeMs(def.getMaxQueueingTimeMs());
            rules.add(rule);
        }
        return rules;
    }

    /**
     * 构建熔断降级规则（YAML 声明，按资源名精确匹配）。
     * 慢调用比例策略额外设置 slowRatioThreshold。
     */
    List<DegradeRule> buildDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();
        for (SentinelProperties.DegradeRuleDef def : properties.getDegradeRules()) {
            DegradeRule rule = new DegradeRule();
            rule.setResource(def.getResource());
            rule.setGrade(def.getGrade());
            rule.setCount(def.getCount());
            rule.setTimeWindow(def.getTimeWindow());
            rule.setMinRequestAmount(def.getMinRequestAmount());
            rule.setStatIntervalMs(def.getStatIntervalMs());
            if (def.getGrade() == RuleConstant.DEGRADE_GRADE_RT) {
                rule.setSlowRatioThreshold(def.getSlowRatioThreshold());
            }
            rules.add(rule);
        }
        return rules;
    }
}
