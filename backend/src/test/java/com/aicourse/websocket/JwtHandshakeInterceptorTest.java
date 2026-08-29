package com.aicourse.websocket;

import com.aicourse.util.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JwtHandshakeInterceptor 单元测试。
 * 覆盖：Cookie 优先解析、子协议回退（Bearer / 纯 jwt 两种形式）、令牌缺失/无效拒绝、
 * 解析成功写入属性与 NegotiatedSubProtocol、afterHandshake 空实现、Cookie 解析各类边界。
 */
@ExtendWith(MockitoExtension.class)
class JwtHandshakeInterceptorTest {

    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private ServerHttpRequest request;
    @Mock
    private ServerHttpResponse response;
    @Mock
    private WebSocketHandler wsHandler;

    private JwtHandshakeInterceptor interceptor;
    private final HttpHeaders headers = new HttpHeaders();

    @BeforeEach
    void setUp() {
        interceptor = new JwtHandshakeInterceptor(jwtUtils);
        lenient().when(request.getHeaders()).thenReturn(headers);
        lenient().when(request.getRemoteAddress()).thenReturn(null);
    }

    private void stubValidToken(String token) {
        when(jwtUtils.validateToken(token)).thenReturn(true);
        when(jwtUtils.getUserIdFromToken(token)).thenReturn(1L);
        when(jwtUtils.getUsernameFromToken(token)).thenReturn("alice");
        when(jwtUtils.getRoleFromToken(token)).thenReturn("STUDENT");
    }

    @Test
    void beforeHandshake_successViaCookie() {
        headers.add("Cookie", "ai_course_token=valid.jwt.token");
        stubValidToken("valid.jwt.token");

        boolean ok = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

        assertTrue(ok);
        verify(jwtUtils).getUserIdFromToken("valid.jwt.token");
    }

    @Test
    void beforeHandshake_successViaBearerProtocol() {
        headers.add("Sec-WebSocket-Protocol", "Bearer prot.jwt.token");
        stubValidToken("prot.jwt.token");

        Map<String, Object> attributes = new HashMap<>();
        boolean ok = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertTrue(ok);
        // 子协议原样回显（RFC 6455 要求与服务端声明一致，含 Bearer 前缀）
        assertEquals("Bearer prot.jwt.token", NegotiatedSubProtocol.get());
        NegotiatedSubProtocol.clear();
    }

    @Test
    void beforeHandshake_successViaPlainProtocol() {
        headers.add("Sec-WebSocket-Protocol", "plain.jwt.token");
        stubValidToken("plain.jwt.token");

        boolean ok = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

        assertTrue(ok);
    }

    @Test
    void beforeHandshake_failureWhenNoToken() {
        boolean ok = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());
        assertFalse(ok);
    }

    @Test
    void beforeHandshake_failureWhenTokenInvalid() {
        headers.add("Cookie", "ai_course_token=bad.token");
        when(jwtUtils.validateToken("bad.token")).thenReturn(false);

        boolean ok = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());
        assertFalse(ok);
    }

    @Test
    void afterHandshake_isNoop() {
        // 不应抛异常
        interceptor.afterHandshake(request, response, wsHandler, null);
        interceptor.afterHandshake(request, response, wsHandler, new RuntimeException("x"));
    }

    @Test
    void resolveTokenFromCookie_multipleCookies() {
        headers.add("Cookie", "theme=dark; ai_course_token=abc.def; other=1");
        stubValidToken("abc.def");

        boolean ok = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());
        assertTrue(ok);
    }

    @Test
    void resolveTokenFromCookie_emptyHeaderReturnsNull() {
        headers.add("Cookie", "");
        // 无有效令牌 -> 失败
        boolean ok = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());
        assertFalse(ok);
    }

    @Test
    void resolveToken_emptyProtocolListHandled() {
        headers.add("Sec-WebSocket-Protocol", "");
        // 子协议为空字符串，resolveToken 返回 null -> 失败
        boolean ok = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());
        assertFalse(ok);
    }
}
