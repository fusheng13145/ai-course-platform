package com.aicourse.flowcontrol;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Sentinel 边缘（URL 级）流控 / 熔断过滤器。
 * <p>
 * 与既有 {@code RateLimitFilter}（IP 级固定窗口）分层协作：
 * <ul>
 *   <li>RateLimitFilter（@Order HIGHEST_PRECEDENCE）：按 IP 防爆破 / 防刷量，是「身份/来源」维度的粗粒度闸门；</li>
 *   <li>本过滤器（紧随其后）：以 {@code METHOD:path} 为资源名对各业务端点施加 QPS 流控，
 *       并以共享资源 {@code sentinel:global} 提供整站入口总限流，是「资源/容量」维度的细粒度闸门；
 *       同时对配置的熔断资源在边缘层提供 RT / 异常比例熔断。</li>
 * </ul>
 * <p>
 * 资源定义约定：URL 资源名 = {@code HTTP方法:请求URI}（含 context-path /api），例如 {@code POST:/api/ai/qa/ask}。
 * 与 {@code sentinel.flow-rules} / {@code sentinel.degrade-rules} 中按资源名配置的规则精确匹配。
 * <p>
 * 兼容性说明：本项目未引入 Spring Cloud，故不依赖 sentinel-webmvc-adapter（其 javax.servlet 实现与
 * Spring Boot3 的 jakarta 不兼容）；此处用纯 sentinel-core 的 {@link SphU} 自行织入，零 jakarta 冲突。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SentinelWebFilter extends OncePerRequestFilter {

    /** Sentinel 上下文名（统一，避免按 URL 作为上下文名导致上下文数爆炸）。 */
    private static final String CONTEXT_NAME = "sentinel-web";

    private static final Logger log = LoggerFactory.getLogger(SentinelWebFilter.class);

    private final SentinelProperties properties;
    private final ObjectMapper objectMapper;

    public SentinelWebFilter(SentinelProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 边缘流控核心：对每个匹配的 HTTP 请求按 {@code METHOD:path} 资源施加 Sentinel 流控 / 熔断。
     * <p>设计取舍：本过滤器为高频入口，<b>不记录逐请求 info 日志</b>（避免噪音与性能损耗），
     * 仅对<b>边界事件</b>（请求被限流 429 / 熔断 503）输出日志，作为容量与可用性排查的关键信号。</p>
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // master / web 开关关闭时直接透传，零开销
        if (!properties.isEnabled() || !properties.getWeb().isEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        // 预检请求不进入统计，避免污染 CORS 预检指标
        if ("OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (isExcluded(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 资源名：METHOD:path（与规则配置一致）
        String resource = request.getMethod() + ":" + path;
        ContextUtil.enter(CONTEXT_NAME, "default");
        Entry globalEntry = null;
        Entry urlEntry = null;
        try {
            // 1) 全局入口限流（仅当配置了 sentinel.web.global-qps>0 时存在对应规则，否则永不阻塞）
            globalEntry = SphU.entry(SentinelConfig.GLOBAL_RESOURCE, EntryType.IN);
            // 2) 按 URL 资源的流控 / 熔断
            urlEntry = SphU.entry(resource, EntryType.IN);
            chain.doFilter(request, response);
        } catch (BlockException e) {
            // 限流 / 熔断边界事件：记录资源名与状态码，便于容量与可用性排查。
            // 熔断(503)权重更高用 warn；流控(429)为常态限流用 info。
            if (e instanceof DegradeException) {
                log.warn("[Sentinel] 资源 {} 触发熔断降级，返回 503", resource);
            } else {
                log.info("[Sentinel] 资源 {} 触发流控限流，返回 429", resource);
            }
            writeBlocked(response, e);
        } catch (Throwable t) {
            // 下游异常：计入当前资源异常指标（支撑异常比例熔断），并透传给 Spring 统一异常处理
            Tracer.trace(t);
            if (t instanceof RuntimeException re) {
                throw re;
            } else if (t instanceof ServletException se) {
                throw se;
            } else if (t instanceof IOException io) {
                throw io;
            } else {
                throw new ServletException(t);
            }
        } finally {
            if (urlEntry != null) {
                urlEntry.exit();
            }
            if (globalEntry != null) {
                globalEntry.exit();
            }
            ContextUtil.exit();
        }
    }

    /**
     * 统一返回限流 / 熔断响应：熔断（DegradeException）→ 503，其余（流控等）→ 429。
     * 复用项目统一 {@link ApiResponse} 结构，与 GlobalExceptionHandler 风格一致。
     */
    private void writeBlocked(HttpServletResponse response, BlockException e) throws IOException {
        boolean degraded = e instanceof DegradeException;
        int status = degraded ? ApiCode.SERVICE_UNAVAILABLE : ApiCode.TOO_MANY_REQUESTS;
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String body = objectMapper.writeValueAsString(ApiResponse.error(
                status,
                degraded ? "服务暂时不可用（熔断降级），请稍后重试"
                        : "请求过于频繁，请稍后再试"));
        response.getWriter().write(body);
    }

    /** 是否命中排除前缀（探活 / WebSocket / Swagger 等不应被限流）。 */
    private boolean isExcluded(String path) {
        List<String> excludes = properties.getWeb().getExcludePaths();
        if (excludes == null) {
            return false;
        }
        for (String prefix : excludes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
