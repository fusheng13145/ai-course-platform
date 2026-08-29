package com.aicourse.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLMFineTune 微调服务测试。
 * 该服务通过 HTTP 调用 Python 微调微服务；生产默认 fine-tune.enabled=false，
 * 关闭时所有操作直接返回 503。本测试覆盖：
 *  - 关闭态（enabled=false）：各方法早返回分支；
 *  - 开启态但服务不可达（指向无监听的 localhost:8003 + maxRetries=0）：
 *    覆盖 enabled=true 进入分支、HTTP 调用抛出、executeWithRetry 的 catch/重试/回落逻辑。
 * 成功路径（真实 RestClient 返回值）不在纯单测范围，留待集成测试。
 */
@ExtendWith(MockitoExtension.class)
class LLMFineTuneServiceImplTest {

    private static final String UNAVAIL = "{\"error\":\"微调服务暂不可用\",\"code\":503}";

    // 关闭态：与生产线默认配置一致
    private final LLMFineTuneServiceImpl disabled =
            new LLMFineTuneServiceImpl(false, "http://localhost:8003", 5, 300, 1, "/api/v1/fine-tune/health");

    // 开启态但目标端口无服务：调用必抛 -> 触发重试/回落分支（maxRetries=0 避免 sleep）
    private final LLMFineTuneServiceImpl enabledBroken =
            new LLMFineTuneServiceImpl(true, "http://localhost:8003", 5, 300, 0, "/api/v1/fine-tune/health");

    @Test
    @DisplayName("disabled: healthCheck 返回 false")
    void disabled_health() {
        assertFalse(disabled.healthCheck());
    }

    @Test
    @DisplayName("disabled: 所有操作返回 503")
    void disabled_ops() {
        assertEquals(UNAVAIL, disabled.startFineTune(1L, "m", "c"));
        assertEquals(UNAVAIL, disabled.getTaskStatus("t1"));
        assertEquals(UNAVAIL, disabled.listTasks(1L));
        assertEquals(UNAVAIL, disabled.cancelTask("t1"));
        assertEquals(UNAVAIL, disabled.inferWithFineTuned("t1", "p", "par"));
        assertEquals(UNAVAIL, disabled.exportWeights("t1"));
        assertEquals(UNAVAIL, disabled.deleteTask("t1"));
    }

    @Test
    @DisplayName("enabled 但服务不可达: healthCheck 返回 false")
    void enabled_health_fail() {
        assertFalse(enabledBroken.healthCheck());
    }

    @Test
    @DisplayName("enabled 但服务不可达: 操作回落 503")
    void enabled_ops_fail() {
        assertEquals(UNAVAIL, enabledBroken.startFineTune(1L, "m", "c"));
        assertEquals(UNAVAIL, enabledBroken.getTaskStatus("t1"));
        assertEquals(UNAVAIL, enabledBroken.listTasks(1L));
        assertEquals(UNAVAIL, enabledBroken.cancelTask("t1"));
        assertEquals(UNAVAIL, enabledBroken.inferWithFineTuned("t1", "p", "par"));
        assertEquals(UNAVAIL, enabledBroken.exportWeights("t1"));
        assertEquals(UNAVAIL, enabledBroken.deleteTask("t1"));
    }
}
