package com.aicourse.service.component.ai;

import com.aicourse.exception.LLMUnavailableException;
import com.aicourse.llm.LLMProvider;
import com.aicourse.service.AiCallLogService;
import com.aicourse.service.impl.PromptGuard;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiCoreComponent 的失败语义单元测试（DR-74-B）。
 *
 * <p>钉的是一条契约：<b>调用失败必须是异常，不是一句被当成正经回答发出去的中文文案</b>。
 * 改动前 {@code callLlm} 在拿到 null 时 {@code return SERVICE_ERROR_MSG}，控制器再
 * {@code ApiResponse.success(...)} 包起来——实测（手册 DR-74）问答与批改因此对外是
 * HTTP 200 / code 200 / message=success，出题则把这句话当成模型 JSON 去解析报 400。</p>
 */
@ExtendWith(MockitoExtension.class)
class AiCoreComponentTest {

    @Mock
    private LLMProvider llmProvider;
    @Mock
    private PromptGuard promptGuard;
    @Mock
    private AiCallLogService aiCallLogService;

    private SimpleMeterRegistry meterRegistry;
    private AiCoreComponent core;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        core = new AiCoreComponent(llmProvider, promptGuard, meterRegistry, aiCallLogService);
    }

    private static List<Map<String, String>> messages() {
        return List.of(Map.of("role", "user", "content", "进程和线程的区别？"));
    }

    @Test
    void callLlm_whenKeyMissing_throwsNotConfiguredInsteadOfReturningHintText() {
        // 未配置是"部署形态问题"，但对调用方而言同样不可用：必须让请求失败，而不是发一句提示当回答
        when(llmProvider.isConfigured()).thenReturn(false);

        LLMUnavailableException ex = assertThrows(LLMUnavailableException.class,
                () -> core.callLlm(messages(), 0.7, null, "chat"));

        assertEquals(LLMUnavailableException.Kind.NOT_CONFIGURED, ex.kind());
        assertEquals(503, ex.getCode());
        verify(llmProvider, never()).complete(any(), any());
    }

    @Test
    void callLlm_whenProviderReturnsNull_throwsEmptyResponseAndCountsFailure() {
        when(llmProvider.isConfigured()).thenReturn(true);
        when(llmProvider.complete(any(), anyDouble())).thenReturn(null);

        LLMUnavailableException ex = assertThrows(LLMUnavailableException.class,
                () -> core.callLlm(messages(), 0.7, null, "chat"));

        assertEquals(LLMUnavailableException.Kind.EMPTY_RESPONSE, ex.kind());
        assertEquals(1.0, meterRegistry.get("aicourse_ai_calls_failed_total")
                .tag("type", "chat").counter().count());
    }

    @Test
    void callLlm_whenProviderThrowsCredentialFailure_keepsKindAndCountsFailure() {
        // 401 这类"凭据被拒"必须一路保留类别，不能被重写成无差别的"暂时不可用"
        when(llmProvider.isConfigured()).thenReturn(true);
        when(llmProvider.complete(any(), anyDouble())).thenThrow(new LLMUnavailableException(
                LLMUnavailableException.Kind.CREDENTIAL_REJECTED, "大模型凭据被拒：上游返回 401"));

        LLMUnavailableException ex = assertThrows(LLMUnavailableException.class,
                () -> core.callLlm(messages(), 0.7, null, "chat"));

        assertEquals(LLMUnavailableException.Kind.CREDENTIAL_REJECTED, ex.kind());
        assertEquals(1.0, meterRegistry.get("aicourse_ai_calls_failed_total")
                .tag("type", "chat").counter().count());
    }

    @Test
    void callLlm_whenProviderSucceeds_returnsSanitizedText() {
        when(llmProvider.isConfigured()).thenReturn(true);
        when(llmProvider.complete(any(), anyDouble())).thenReturn("原始回答");
        when(promptGuard.sanitizeOutput("原始回答")).thenReturn("清洗后回答");

        assertEquals("清洗后回答", core.callLlm(messages(), 0.7, null, "chat"));
        assertEquals(1.0, meterRegistry.get("aicourse_ai_calls_total").tag("type", "chat").counter().count());
        assertEquals(0.0, meterRegistry.find("aicourse_ai_calls_failed_total").counter() == null
                ? 0.0 : meterRegistry.get("aicourse_ai_calls_failed_total").counter().count());
    }

    @Test
    void completeAndSanitize_whenOutputBlank_throwsInsteadOfHandingNullToUpperFacade() {
        // 上层门面的 `raw != null ? raw : SERVICE_ERROR_MSG` 正是伪装成功的落点：
        // 这里把 null 也收成异常，门面就不再需要在"成功"分支里兜文案。
        when(llmProvider.complete(any(), anyDouble())).thenReturn(null);

        assertThrows(LLMUnavailableException.class,
                () -> core.completeAndSanitize(messages(), 0.7, "chat"));
    }
}
