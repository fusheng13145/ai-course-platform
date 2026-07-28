package com.aicourse.websocket;

import com.aicourse.config.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

/**
 * WebSocket 端点配置。
 *
 * <p>注册 {@code /ws} 端点（受 context-path 影响，实际对外路径为 {@code /api/ws}）。
 * 鉴权由 {@link JwtHandshakeInterceptor} 在握手阶段完成，故 SecurityConfig 对 {@code /ws} 放行。
 * 跨域允许域名复用 CorsProperties 配置（与 REST 接口保持一致）。</p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RealtimeWebSocketHandler handler;
    private final JwtHandshakeInterceptor handshakeInterceptor;
    private final CorsProperties corsProperties;

    public WebSocketConfig(RealtimeWebSocketHandler handler,
                           JwtHandshakeInterceptor handshakeInterceptor,
                           CorsProperties corsProperties) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        List<String> origins = corsProperties.getAllowedOrigins();
        if (origins == null || origins.isEmpty()) {
            origins = List.of("http://localhost:5173");
        }
        registry.addHandler(handler, "/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(origins.toArray(new String[0]));
    }
}
