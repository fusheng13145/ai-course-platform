package com.aicourse.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 请求级 traceId 注入（2026-11-14 P2-9/N5）。
 *
 * <p>架构文档 §11.5 一直声称日志含 traceId，但全仓无 MDC 写入——生产排障时一次请求
 * 的多行日志无法串联。本过滤器把 traceId 写入 SLF4J MDC（键 {@code traceId}，与
 * logback-spring.xml 的 {@code %X{traceId:-}} 对应），响应头 {@code X-Trace-Id} 回传，
 * 供前端/网关侧关联；上游已带 {@code X-Trace-Id} 时沿用（透传链路）。</p>
 *
 * <p>置于最高优先级（先于限流/CSRF/Security），保证第一行日志即带 traceId；
 * finally 中清理 MDC，防异步线程池复用造成的 traceId 串号。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(TRACE_ID_HEADER);
        String traceId = (incoming != null && !incoming.isBlank() && incoming.length() <= 64)
                ? incoming
                : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
