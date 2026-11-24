package com.aicourse.websocket;

import com.aicourse.config.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final RealtimeWebSocketHandler handler;
    private final JwtHandshakeInterceptor handshakeInterceptor;
    private final CorsProperties corsProperties;

    /**
     * 是否允许「未配置 origins 时回退本地开发地址」。与 HTTP CORS 共用同一开关（DEF-07），
     * 字段注入而非构造参数，保持既有单测可直接 new 出实例。
     */
    @org.springframework.beans.factory.annotation.Value("${app.cors.dev-fallback:true}")
    private boolean corsDevFallback = true;

    public WebSocketConfig(RealtimeWebSocketHandler handler,
                           JwtHandshakeInterceptor handshakeInterceptor,
                           CorsProperties corsProperties) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.corsProperties = corsProperties;
    }

    /**
     * 注册 {@code /ws} 端点并绑定 JWT 握手拦截器与跨域来源。
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        List<String> origins = com.aicourse.config.CorsOriginResolver.resolve(
                corsProperties.getAllowedOrigins(), corsDevFallback);
        if (com.aicourse.config.CorsOriginResolver.isFailClosed(origins)) {
            // 与 HTTP CORS 保持一致：dev-fallback 关闭且未配置来源时拒绝一切跨域握手。
            log.warn("[WebSocketConfig] cors.allowed-origins 未配置且 app.cors.dev-fallback=false，"
                    + "WebSocket 将拒绝一切跨域握手");
        }
        log.info("[WebSocketConfig] 注册 WebSocket 端点 /ws，允许跨域来源: {}", origins);
        registry.addHandler(handler, "/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(origins.toArray(new String[0]));
    }
}
