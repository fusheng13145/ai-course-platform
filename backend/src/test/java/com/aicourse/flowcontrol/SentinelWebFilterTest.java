package com.aicourse.flowcontrol;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 SentinelWebFilter 在 JDK21 下可正常织入流控：超限返回 429，OPTIONS / 排除路径 / 关闭开关时透传。
 * 使用 Spring Mock Servlet API，不启动完整 Spring 上下文。
 */
class SentinelWebFilterTest {

    private SentinelWebFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        SentinelProperties props = new SentinelProperties();
        props.setEnabled(true);
        props.getWeb().setEnabled(true);
        props.getWeb().setGlobalQps(0); // 关闭全局入口限流，隔离测试 URL 级规则
        filter = new SentinelWebFilter(props, objectMapper);

        // 为测试资源加载 QPS=1 的流控规则：同一秒内第 2 次请求应被拦截
        FlowRule rule = new FlowRule();
        rule.setResource("GET:/api/test/x");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(1);
        rule.setLimitApp("default");
        FlowRuleManager.loadRules(List.of(rule));
    }

    @AfterEach
    void tearDown() {
        // 清空规则，避免影响其他测试
        FlowRuleManager.loadRules(List.of());
    }

    private FilterChain recordingChain(AtomicBoolean invoked) {
        return (jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s) -> invoked.set(true);
    }

    @Test
    void shouldBlockWith429WhenFlowLimitExceeded() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test/x");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        // 第 1 次：放行
        filter.doFilter(req, resp, recordingChain(invoked));
        assertTrue(invoked.get(), "首次请求应放行并进入下游");
        assertEquals(200, resp.getStatus());

        // 第 2 次：被流控拦截
        invoked.set(false);
        filter.doFilter(req, resp, recordingChain(invoked));
        assertFalse(invoked.get(), "超限请求不应进入下游");
        assertEquals(429, resp.getStatus());
    }

    @Test
    void shouldPassOptionsWithoutLimiting() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("OPTIONS", "/api/test/x");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(req, resp, recordingChain(invoked));
        assertTrue(invoked.get());
        assertEquals(200, resp.getStatus());
    }

    @Test
    void shouldPassExcludedPath() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/actuator/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(req, resp, recordingChain(invoked));
        assertTrue(invoked.get(), "排除路径(actuator 探活)应透传");
        assertEquals(200, resp.getStatus());
    }

    @Test
    void shouldPassThroughWhenDisabled() throws ServletException, IOException {
        SentinelProperties disabled = new SentinelProperties();
        disabled.setEnabled(false);
        SentinelWebFilter disabledFilter = new SentinelWebFilter(disabled, objectMapper);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test/x");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        disabledFilter.doFilter(req, resp, recordingChain(invoked));
        assertTrue(invoked.get(), "关闭开关时应透传，不受流控规则影响");
        assertEquals(200, resp.getStatus());
    }
}
