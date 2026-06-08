package com.aicourse.security;

import com.aicourse.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JWT 未认证统一异常处理器
 * 当用户未登录/令牌无效时，统一返回 401 响应
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // 设置响应格式与状态码
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // 构建统一返回结果
        ApiResponse<Object> result = ApiResponse.error(401, "未授权，请先登录");
        // 序列化并写出响应
        OBJECT_MAPPER.writeValue(response.getOutputStream(), result);
    }
}