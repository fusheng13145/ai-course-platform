package com.aicourse.security;

import com.aicourse.exception.BusinessException;
import com.aicourse.service.impl.AiQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 端点配额拦截器：把「没人会恶意刷 AI」这一乐观假设变成代码级保证。
 *
 * <p>拦截路径 {@code /ai/**} 的写请求（POST/PUT/PATCH，即真正触发 LLM 调用的接口），在进入
 * Controller <b>之前</b>完成两道熔断：</p>
 * <ol>
 *   <li>{@link AiQuotaService#assertTokenBudget(Long)} —— 当日 token 预算是否已耗尽；</li>
 *   <li>{@link AiQuotaService#assertRequestQuota(Long)} —— 当日请求次数是否超限（原子自增并占用一次）。</li>
 * </ol>
 *
 * <p>随后按请求体大小（{@code Content-Length}，不消费请求流）估算本次 prompt 的 token 成本，
 * 叠加一份保守的响应侧固定开销后记账，形成单用户单日 LLM 成本的<b>数学上界</b>。
 * 与通用 {@code RateLimitFilter}（按 IP）互补：IP 限流拦「同源洪泛」，本拦截器按认证 {@code userId}
 * 拦「代理池轮换 IP 的刷量」——代理池可换 IP，但换不掉登录身份。</p>
 *
 * <p>超额时 {@link AiQuotaService} 抛出 {@code BusinessException(429)}，由全局异常处理器统一返回
 * HTTP 429，因此本拦截器 {@code preHandle} 不需要自行写响应体。</p>
 */
@Component
public class AiQuotaInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AiQuotaInterceptor.class);

    /**
     * 响应侧保守固定开销（token）。LLM 响应长度在请求前不可知，为使成本上界覆盖响应，
     * 对每次调用叠加一份保守估计，宁可高估也不低估。
     */
    private static final long RESPONSE_TOKEN_OVERHEAD = 1500L;

    private final AiQuotaService aiQuotaService;

    public AiQuotaInterceptor(AiQuotaService aiQuotaService) {
        this.aiQuotaService = aiQuotaService;
    }

    /**
     * 在 Controller 之前对可计费的 AI 写请求执行 token 预算与请求次数双重熔断，并记账本次成本。
     *
     * @return true 放行；配额超限时由 {@link AiQuotaService} 抛出 429 并由全局异常处理器统一返回
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 仅对会触发 LLM 调用的写操作计费；GET（如查询历史会话）不计入 AI 成本配额
        if (!isBillableMethod(request.getMethod())) {
            return true;
        }

        Long userId = currentUserId();
        if (userId == null) {
            // 未认证请求交由 Spring Security 处理（AI 端点均需登录），此处不计费
            return true;
        }

        // 1) 先判 token 预算是否已耗尽；2) 再占用一次请求次数配额。二者任一超限即抛 429 熔断。
        try {
            aiQuotaService.assertTokenBudget(userId);
            aiQuotaService.assertRequestQuota(userId);
        } catch (BusinessException e) {
            log.warn("[AiQuotaInterceptor] 用户 {} AI 配额/预算超限被拦截: {}", userId, e.getMessage());
            throw e;
        }

        // 3) 记账：以请求体字节数近似 prompt 字符数折算 token，叠加响应侧保守开销
        try {
            long promptTokens = estimatePromptTokens(request);
            aiQuotaService.recordTokenUsage(userId, promptTokens + RESPONSE_TOKEN_OVERHEAD);
        } catch (BusinessException e) {
            log.warn("[AiQuotaInterceptor] 用户 {} AI 额度记账异常: {}", userId, e.getMessage());
            throw e;
        }
        return true;
    }

    private boolean isBillableMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return (principal instanceof Long) ? (Long) principal : null;
    }

    /** 用 Content-Length（请求体字节数）近似 prompt 字符数折算 token，避免消费请求流；缺失时按 0 计。 */
    private long estimatePromptTokens(HttpServletRequest request) {
        long contentLength = Math.max(request.getContentLengthLong(), 0L);
        return aiQuotaService.estimateTokensFromChars(contentLength);
    }
}
