package com.aicourse.security;

import com.aicourse.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import com.aicourse.common.ApiCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JWT 未认证统一异常处理器
 * 当用户未登录/令牌无效时，统一返回 401 响应
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        // ApiResponse.timestamp 为 LocalDateTime，需注册 JavaTime 模块方可序列化，
        // 否则未认证响应序列化时会抛出 InvalidDefinitionException，导致 401 体为空/报错。
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("[JwtAuth] 未认证/令牌无效，返回 401：uri={}, msg={}",
                request.getRequestURI(), authException.getMessage());
        // 设置响应格式与状态码
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // 构建统一返回结果
        ApiResponse<Object> result = ApiResponse.error(ApiCode.UNAUTHORIZED, "未授权，请先登录");
        // 序列化并写出响应
        OBJECT_MAPPER.writeValue(response.getOutputStream(), result);
    }
}