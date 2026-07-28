package com.aicourse.websocket;

import com.aicourse.util.JwtUtils;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手拦截器：从 {@code Sec-WebSocket-Protocol} 子协议头中解析并校验 JWT。
 *
 * <p>相比把令牌放在 URL 查询参数（会被服务器访问日志、浏览器历史、反向代理记录），
 * 子协议头随 TLS 加密且在握手完成后即丢弃，显著降低了令牌泄露面。</p>
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtils jwtUtils;

    public JwtHandshakeInterceptor(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 旧版通过 Sec-WebSocket-Protocol 子协议传递令牌的客户端仍兼容（作为回退）
        List<String> protocols = request.getHeaders().get("Sec-WebSocket-Protocol");
        // 令牌优先从 HttpOnly Cookie（ai_course_token）读取：前端不再需要将令牌作为参数/子协议传递，
        // 浏览器在同源/同站握手时自动携带 Cookie，JS 无法读取，进一步收敛令牌暴露面。
        String token = resolveTokenFromCookie(request);
        if (token == null) {
            token = resolveToken(protocols);
        }
        if (token == null || !jwtUtils.validateToken(token)) {
            return false; // 校验失败，拒绝握手
        }

        // 提取身份并存入握手属性，供 WebSocketHandler 使用
        attributes.put("userId", jwtUtils.getUserIdFromToken(token));
        attributes.put("username", jwtUtils.getUsernameFromToken(token));
        attributes.put("role", jwtUtils.getRoleFromToken(token));

        // 记录协商出的子协议（必须与客户端声明完全一致），供 getSubProtocols() 回显
        if (protocols != null && !protocols.isEmpty()) {
            NegotiatedSubProtocol.set(protocols.get(0).trim());
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }

    /**
     * 从子协议列表中解析 JWT：支持 {@code "Bearer <jwt>"} 或纯 jwt 两种形式。
     */
    private String resolveToken(List<String> protocols) {
        if (protocols == null || protocols.isEmpty()) {
            return null;
        }
        for (String p : protocols) {
            String trimmed = p.trim();
            if (trimmed.length() > 7 && trimmed.substring(0, 7).equalsIgnoreCase("Bearer ")) {
                return trimmed.substring(7).trim();
            }
        }
        return protocols.get(0).trim();
    }

    /**
     * 从 Cookie 请求头解析 JWT（ai_course_token）。
     */
    private String resolveTokenFromCookie(ServerHttpRequest request) {
        String cookieHeader = request.getHeaders().getFirst("Cookie");
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.trim();
            int eqIndex = trimmed.indexOf('=');
            if (eqIndex > 0) {
                String name = trimmed.substring(0, eqIndex).trim();
                String value = trimmed.substring(eqIndex + 1).trim();
                if (JwtUtils.ACCESS_TOKEN_COOKIE.equals(name) && !value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }
}
