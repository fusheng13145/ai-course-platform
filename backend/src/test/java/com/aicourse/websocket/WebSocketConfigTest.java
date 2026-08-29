package com.aicourse.websocket;

import com.aicourse.config.CorsProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebSocketConfig 单元测试：直接实例化并调用 registerWebSocketHandlers。
 * 覆盖：origins 为 null 时回退默认来源，以及显式 origins 透传。
 */
@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock
    private RealtimeWebSocketHandler handler;
    @Mock
    private JwtHandshakeInterceptor handshakeInterceptor;
    @Mock
    private WebSocketHandlerRegistry registry;
    @Mock
    private WebSocketHandlerRegistration registration;

    @Test
    void registerWebSocketHandlers_usesDefaultOriginsWhenNull() {
        CorsProperties cors = new CorsProperties();
        // getAllowedOrigins() 默认 null
        WebSocketConfig config = new WebSocketConfig(handler, handshakeInterceptor, cors);

        lenient().when(registry.addHandler(any(), anyString())).thenReturn(registration);
        lenient().when(registration.addInterceptors(any())).thenReturn(registration);
        lenient().when(registration.setAllowedOriginPatterns(any())).thenReturn(registration);

        config.registerWebSocketHandlers(registry);

        verify(registration).setAllowedOriginPatterns(new String[]{"http://localhost:5173"});
    }

    @Test
    void registerWebSocketHandlers_usesConfiguredOrigins() {
        CorsProperties cors = new CorsProperties();
        cors.setAllowedOrigins(List.of("https://a.example.com", "https://b.example.com"));
        WebSocketConfig config = new WebSocketConfig(handler, handshakeInterceptor, cors);

        lenient().when(registry.addHandler(any(), anyString())).thenReturn(registration);
        lenient().when(registration.addInterceptors(any())).thenReturn(registration);
        lenient().when(registration.setAllowedOriginPatterns(any())).thenReturn(registration);

        config.registerWebSocketHandlers(registry);

        verify(registration).setAllowedOriginPatterns(
                new String[]{"https://a.example.com", "https://b.example.com"});
    }
}
