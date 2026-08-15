package com.aicourse.flowcontrol;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 SentinelProperties -> Sentinel 原生规则（FlowRule / DegradeRule）的转换正确性。
 * 不依赖 Spring 上下文，纯单元测试。
 */
class SentinelConfigTest {

    private SentinelProperties buildProperties() {
        SentinelProperties props = new SentinelProperties();
        props.setEnabled(true);
        props.getWeb().setEnabled(true);
        props.getWeb().setGlobalQps(1000);

        SentinelProperties.FlowRuleDef aiQa = new SentinelProperties.FlowRuleDef();
        aiQa.setResource("POST:/api/ai/qa/ask");
        aiQa.setGrade(1);
        aiQa.setCount(50);
        aiQa.setLimitApp("default");
        aiQa.setStrategy(0);
        aiQa.setControlBehavior(0);
        aiQa.setMaxQueueingTimeMs(500);
        props.getFlowRules().add(aiQa);

        SentinelProperties.DegradeRuleDef rag = new SentinelProperties.DegradeRuleDef();
        rag.setResource("rag:http");
        rag.setGrade(0);
        rag.setCount(3000);
        rag.setTimeWindow(10);
        rag.setMinRequestAmount(5);
        rag.setStatIntervalMs(10000);
        rag.setSlowRatioThreshold(0.5);
        props.getDegradeRules().add(rag);
        return props;
    }

    @Test
    void buildFlowRules_shouldIncludeGlobalAndConfiguredRules() {
        SentinelConfig config = new SentinelConfig(buildProperties());
        List<FlowRule> rules = config.buildFlowRules();

        // 全局入口限流(1) + 配置的流控规则(1)
        assertEquals(2, rules.size());

        FlowRule global = rules.get(0);
        assertEquals(SentinelConfig.GLOBAL_RESOURCE, global.getResource());
        assertEquals(RuleConstant.FLOW_GRADE_QPS, global.getGrade());
        assertEquals(1000, global.getCount(), 0.0001);

        FlowRule aiQa = rules.get(1);
        assertEquals("POST:/api/ai/qa/ask", aiQa.getResource());
        assertEquals(RuleConstant.FLOW_GRADE_QPS, aiQa.getGrade());
        assertEquals(50, aiQa.getCount(), 0.0001);
        assertEquals(500, aiQa.getMaxQueueingTimeMs());
    }

    @Test
    void buildDegradeRules_shouldMapRtStrategyWithSlowRatio() {
        SentinelConfig config = new SentinelConfig(buildProperties());
        List<DegradeRule> rules = config.buildDegradeRules();

        assertEquals(1, rules.size());
        DegradeRule rag = rules.get(0);
        assertEquals("rag:http", rag.getResource());
        assertEquals(RuleConstant.DEGRADE_GRADE_RT, rag.getGrade());
        assertEquals(3000, rag.getCount(), 0.0001);
        assertEquals(10, rag.getTimeWindow());
        assertEquals(5, rag.getMinRequestAmount());
        assertEquals(10000, rag.getStatIntervalMs());
        assertEquals(0.5, rag.getSlowRatioThreshold(), 0.0001);
    }

    @Test
    void buildFlowRules_globalRuleSkippedWhenGlobalQpsZero() {
        SentinelProperties props = buildProperties();
        props.getWeb().setGlobalQps(0);
        SentinelConfig config = new SentinelConfig(props);
        List<FlowRule> rules = config.buildFlowRules();
        // 仅配置的 1 条，不含全局
        assertEquals(1, rules.size());
        assertEquals("POST:/api/ai/qa/ask", rules.get(0).getResource());
    }
}
