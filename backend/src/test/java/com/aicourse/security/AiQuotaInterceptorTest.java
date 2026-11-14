package com.aicourse.security;

import com.aicourse.exception.BusinessException;
import com.aicourse.service.impl.AiQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiQuotaInterceptor 单元测试：覆盖可计费方法计费、GET 不计费、未认证不计费、
 * 预算/次数超限抛 429、记账异常透传的分支。
 */
@ExtendWith(MockitoExtension.class)
class AiQuotaInterceptorTest {

    @Mock
    private AiQuotaService aiQuotaService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private Authentication authentication;

    private AiQuotaInterceptor interceptor;

    @BeforeEach
    void setUp() {
        // 必须在 @Mock 注入之后构建，否则 aiQuotaService 为 null 导致 preHandle 内 NPE
        interceptor = new AiQuotaInterceptor(aiQuotaService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticatedAs(Long userId) {
        lenient().when(authentication.getPrincipal()).thenReturn(userId);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void preHandle_billableMethod_chargesQuota() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentLengthLong()).thenReturn(10L);
        lenient().when(aiQuotaService.estimateTokensFromChars(10L)).thenReturn(10L);
        authenticatedAs(1L);

        assertTrue(interceptor.preHandle(request, response, new Object()));

        verify(aiQuotaService).assertTokenBudget(1L);
        verify(aiQuotaService).assertRequestQuota(1L);
        verify(aiQuotaService).recordTokenUsage(eq(1L), eq(10L + 1500L));
    }

    @Test
    void preHandle_getMethod_notBillable() {
        when(request.getMethod()).thenReturn("GET");
        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(aiQuotaService, never()).assertTokenBudget(any());
    }

    @Test
    void preHandle_getQaStream_chargesQuota() {
        // GET /ai/qa/stream 与写请求同成本（连接建立即真实发起 LLM 流式调用），必须纳入双重熔断
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/ai/qa/stream");
        when(request.getContextPath()).thenReturn("/api");
        when(request.getParameter("question")).thenReturn("进程和线程的区别");
        when(aiQuotaService.estimateTokensFromChars(8L)).thenReturn(10L);
        authenticatedAs(1L);

        assertTrue(interceptor.preHandle(request, response, new Object()));

        verify(aiQuotaService).assertTokenBudget(1L);
        verify(aiQuotaService).assertRequestQuota(1L);
        // token 记账按 question 参数字符数折算，叠加响应侧保守开销
        verify(aiQuotaService).recordTokenUsage(eq(1L), eq(10L + 1500L));
    }

    @Test
    void preHandle_getQaStream_overLimit_throws429() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/ai/qa/stream");
        when(request.getContextPath()).thenReturn("");
        authenticatedAs(1L);
        doThrow(new BusinessException(429, "今日 AI 使用次数已达上限"))
                .when(aiQuotaService).assertRequestQuota(1L);

        assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void preHandle_getLlmGeneratingEndpoints_chargeQuota() {
        // 推荐/薄弱点/建议三类 GET 同样在服务端真实触发 LLM 生成（callLlm），必须纳入配额
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/ai/advice/learning");
        when(request.getContextPath()).thenReturn("/api");
        authenticatedAs(1L);

        assertTrue(interceptor.preHandle(request, response, new Object()));

        verify(aiQuotaService).assertTokenBudget(1L);
        verify(aiQuotaService).assertRequestQuota(1L);
        // 无 question 参数 → prompt 折算为 0，仅叠加响应侧保守开销
        verify(aiQuotaService).recordTokenUsage(eq(1L), eq(1500L));
    }

    @Test
    void preHandle_getOtherAiPath_notBillable() {
        // 其余 GET（如查询历史会话）不触发 LLM 调用，不计入配额
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/ai/qa/sessions");
        when(request.getContextPath()).thenReturn("");
        authenticatedAs(1L);

        assertTrue(interceptor.preHandle(request, response, new Object()));

        verify(aiQuotaService, never()).assertTokenBudget(any());
        verify(aiQuotaService, never()).assertRequestQuota(any());
    }

    @Test
    void preHandle_unauthenticated_notBillable() {
        when(request.getMethod()).thenReturn("POST");
        // 未设置认证 -> currentUserId 为 null
        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(aiQuotaService, never()).assertTokenBudget(any());
    }

    @Test
    void preHandle_tokenBudgetExceeded_rethrows() {
        when(request.getMethod()).thenReturn("POST");
        authenticatedAs(1L);
        doThrow(new BusinessException(429, "quota exceeded")).when(aiQuotaService).assertTokenBudget(1L);
        assertThrows(BusinessException.class, () -> interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void preHandle_recordUsageException_rethrows() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentLengthLong()).thenReturn(10L);
        authenticatedAs(1L);
        doThrow(new BusinessException(429, "budget exceeded")).when(aiQuotaService).recordTokenUsage(any(), anyLong());
        assertThrows(BusinessException.class, () -> interceptor.preHandle(request, response, new Object()));
    }
}
