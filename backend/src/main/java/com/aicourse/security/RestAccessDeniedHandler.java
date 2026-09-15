package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 已认证但权限不足（AccessDeniedException）统一响应处理器：返回 403 + 标准业务信封。
 *
 * <p>配套 {@link JwtAuthenticationEntryPoint} 使用：Spring Security 的
 * {@code ExceptionTranslationFilter} 在「已认证」分支会调用 AccessDeniedHandler；
 * 此前 {@code SecurityConfig} 只配置了 authenticationEntryPoint，未配置本处理器，
 * 依赖默认 {@code AccessDeniedHandlerImpl}（返回无响应体的 403），
 * 前端拿不到 {@code code/message} 字段，无法区分「无权限」与「服务异常」。
 * 现统一为 {@code {"code":403,"message":"权限不足，禁止访问"}}。</p>
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("[AccessDenied] 已认证但权限不足：uri={}, msg={}",
                request.getRequestURI(), accessDeniedException.getMessage());
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        ApiResponse<Object> result = ApiResponse.error(ApiCode.FORBIDDEN, "权限不足，禁止访问");
        OBJECT_MAPPER.writeValue(response.getOutputStream(), result);
    }
}
