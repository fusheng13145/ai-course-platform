package com.aicourse.flowcontrol;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sentinel 规则持久化与文件数据源热刷新验证（不依赖 DB / Redis，纯单测）。
 */
class SentinelDataSourceConfigTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private SentinelConfig config;

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        if (config != null) {
            config.destroy();
        }
        // 清理全局规则管理器，避免污染其他用例
        FlowRuleManager.loadRules(new ArrayList<>());
        DegradeRuleManager.loadRules(new ArrayList<>());
    }

    private SentinelProperties buildProperties() {
        SentinelProperties props = new SentinelProperties();
        props.setEnabled(true);
        props.setRulesDir(tempDir.toString());

        SentinelProperties.FlowRuleDef flow = new SentinelProperties.FlowRuleDef();
        flow.setResource("POST:/api/ai/qa/ask");
        flow.setGrade(1);
        flow.setCount(50);
        flow.setLimitApp("default");
        flow.setStrategy(0);
        flow.setControlBehavior(0);
        flow.setMaxQueueingTimeMs(500);
        props.setFlowRules(List.of(flow));

        SentinelProperties.DegradeRuleDef degrade = new SentinelProperties.DegradeRuleDef();
        degrade.setResource("rag:http");
        degrade.setGrade(0);
        degrade.setCount(3000);
        degrade.setTimeWindow(10);
        degrade.setMinRequestAmount(5);
        degrade.setStatIntervalMs(10000);
        degrade.setSlowRatioThreshold(0.5);
        props.setDegradeRules(List.of(degrade));

        return props;
    }

    @Test
    void shouldPersistRulesToFilesOnInit() throws Exception {
        config = new SentinelConfig(buildProperties());
        config.initRules();

        Path flowFile = tempDir.resolve("flow-rules.json");
        Path degradeFile = tempDir.resolve("degrade-rules.json");

        assertTrue(Files.exists(flowFile), "流控规则文件应已落盘");
        assertTrue(Files.exists(degradeFile), "熔断规则文件应已落盘");

        String flowContent = Files.readString(flowFile);
        String degradeContent = Files.readString(degradeFile);

        assertTrue(flowContent.contains("POST:/api/ai/qa/ask"), "流控文件应含声明的资源名");
        assertTrue(degradeContent.contains("rag:http"), "熔断文件应含声明的资源名");

        // 内存规则应与声明的 count 一致
        Optional<FlowRule> aiQa = FlowRuleManager.getRules().stream()
                .filter(r -> "POST:/api/ai/qa/ask".equals(r.getResource()))
                .findFirst();
        assertTrue(aiQa.isPresent());
        assertEquals(50, aiQa.get().getCount(), "流控阈值应等于声明的 50");
    }

    @Test
    void shouldHotReloadRulesFromModifiedFile() throws Exception {
        config = new SentinelConfig(buildProperties());
        config.initRules();

        // 编辑流控文件：将 POST:/api/ai/qa/ask 的阈值改为 123
        Path flowFile = tempDir.resolve("flow-rules.json");
        List<FlowRule> rules = MAPPER.readValue(Files.readString(flowFile),
                new TypeReference<List<FlowRule>>() {
                });
        rules.stream()
                .filter(r -> "POST:/api/ai/qa/ask".equals(r.getResource()))
                .forEach(r -> r.setCount(123));
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(flowFile.toFile(), rules);

        // 轮询等待文件数据源热刷新（默认轮询周期 3s 内）
        boolean reloaded = false;
        for (int i = 0; i < 25; i++) { // 最多 ~5s
            Optional<FlowRule> updated = FlowRuleManager.getRules().stream()
                    .filter(r -> "POST:/api/ai/qa/ask".equals(r.getResource()))
                    .findFirst();
            if (updated.isPresent() && updated.get().getCount() == 123) {
                reloaded = true;
                break;
            }
            Thread.sleep(200);
        }

        assertTrue(reloaded, "运行期编辑规则文件后，内存规则应经热刷新更新为 123");
    }

    @Test
    void shouldSkipWhenDisabled() {
        SentinelProperties props = buildProperties();
        props.setEnabled(false);
        config = new SentinelConfig(props);
        config.initRules();

        Path flowFile = tempDir.resolve("flow-rules.json");
        assertFalse(Files.exists(flowFile), "禁用时不应落盘规则文件");
        assertNotNull(FlowRuleManager.getRules()); // 不抛异常即可
    }
}
