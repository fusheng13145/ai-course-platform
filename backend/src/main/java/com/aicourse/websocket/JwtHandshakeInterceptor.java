package com.aicourse.websocket;

import com.aicourse.service.UserSessionService;
import com.aicourse.util.JwtUtils;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket 握手拦截器：从 {@code Sec-WebSocket-Protocol} 子协议头中解析并校验 JWT。
 *
 * <p>相比把令牌放在 URL 查询参数（会被服务器访问日志、浏览器历史、反向代理记录），
 * 子协议头随 TLS 加密且在握手完成后即丢弃，显著降低了令牌泄露面。</p>
 *
 * <p>【修复 #10a】验签之外还必须校验会话活跃态：此前仅 {@code jwtUtils.validateToken(token)}，
 * 与 {@code JwtRequestFilter#doFilterInternal}（验签 + {@code isSessionActive}）不对称 ——
 * 用户登出 / 被强制下线 / 改密后会话已吊销，REST 侧已被 401 拒绝，WS 侧却仍可握手成功，
 * 使「登出即失效」在长连接通道上被绕过。现统一调用
 * {@link UserSessionService#isSessionActive(String)}（Redis 缓存优先、DB 兜底，开销可忽略）。</p>
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final JwtUtils jwtUtils;
    private final UserSessionService userSessionService;

    /**
     * 构造器注入（本类为 {@code @Component}，由 {@code WebSocketConfig} 经 Spring 装配，
     * 因此可以直接使用构造器注入，无需 {@code new} 手工构造）。
     *
     * @param jwtUtils           JWT 解析/验签工具
     * @param userSessionService 会话活跃态校验服务（修复 #10a：补会话吊销检查）
     */
    public JwtHandshakeInterceptor(JwtUtils jwtUtils, UserSessionService userSessionService) {
        this.jwtUtils = jwtUtils;
        this.userSessionService = userSessionService;
    }

    /**
     * 握手前校验 JWT（优先 HttpOnly Cookie，回退子协议头），校验通过则写入用户身份属性。
     *
     * <p>校验链：令牌存在 → 验签通过 → {@link UserSessionService#isSessionActive(String)} 为 true。
     * 任一环节不过即拒绝握手（HTTP 403，不升级为 WebSocket）。</p>
     *
     * @return true 允许升级；令牌缺失/无效/会话已吊销时返回 false 拒绝握手
     */
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
            log.warn("[ws-handshake] 握手失败：令牌缺失或无效，拒绝连接: {}", request.getRemoteAddress());
            return false; // 校验失败，拒绝握手
        }

        // 修复 #10a：验签通过 ≠ 会话仍然有效。登出 / 强制下线 / 改密后会话已被吊销，
        // 此时签名仍未过期（JWT 无状态），必须查会话活跃态才能与 REST 侧口径一致。
        if (userSessionService == null || !userSessionService.isSessionActive(token)) {
            log.warn("[ws-handshake] 握手失败：会话已失效（用户已登出或会话已吊销），拒绝连接: userId={}",
                    jwtUtils.getUserIdFromToken(token));
            return false;
        }

        // 提取身份并存入握手属性，供 WebSocketHandler 使用
        attributes.put("userId", jwtUtils.getUserIdFromToken(token));
        attributes.put("username", jwtUtils.getUsernameFromToken(token));
        attributes.put("role", jwtUtils.getRoleFromToken(token));

        // 记录协商出的子协议（必须与客户端声明完全一致），供 getSubProtocols() 回显
        if (protocols != null && !protocols.isEmpty()) {
            NegotiatedSubProtocol.set(protocols.get(0).trim());
        }
        log.info("[ws-handshake] 握手成功: userId={}", attributes.get("userId"));
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
