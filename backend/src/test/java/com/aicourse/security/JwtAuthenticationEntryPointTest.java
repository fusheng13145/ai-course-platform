package com.aicourse.security;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JwtAuthenticationEntryPoint 单元测试：验证 401 状态码、Content-Type 与响应体写入。
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationEntryPointTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private AuthenticationException authException;
    @Mock
    private ServletOutputStream outputStream;

    @Test
    void commence_writesUnauthorizedJson() throws IOException {
        when(request.getRequestURI()).thenReturn("/api/secure");
        when(authException.getMessage()).thenReturn("bad token");
        when(response.getOutputStream()).thenReturn(outputStream);

        new JwtAuthenticationEntryPoint().commence(request, response, authException);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json;charset=UTF-8");
        verify(outputStream, org.mockito.Mockito.atLeastOnce())
                .write(org.mockito.ArgumentMatchers.any(byte[].class),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt());
    }
}
