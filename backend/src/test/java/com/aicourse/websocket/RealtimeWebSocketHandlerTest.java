package com.aicourse.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RealtimeWebSocketHandler 单元测试（纯 Mockito，无 Spring 上下文）。
 * 覆盖：连接登记 / 拒绝（缺 userId）/ 心跳 pong / 业务消息透传 / 脏数据异常 /
 * 连接关闭注销 / 传输异常注销 / 子协议回显（含 null 分支）。
 */
@ExtendWith(MockitoExtension.class)
class RealtimeWebSocketHandlerTest {

    @Mock
    private WebSocketSessionManager sessionManager;
    @Mock
    private WebSocketSession session;

    private RealtimeWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RealtimeWebSocketHandler(sessionManager, new ObjectMapper());
        lenient().when(session.getId()).thenReturn("sid-1");
        lenient().when(session.getAttributes()).thenReturn(new HashMap<>());
    }

    private Map<String, Object> attrsWithUser(Long userId) {
        Map<String, Object> m = new HashMap<>();
        if (userId != null) m.put("userId", userId);
        return m;
    }

    @Test
    void afterConnectionEstablished_registersWhenUserIdPresent() {
        when(session.getAttributes()).thenReturn(attrsWithUser(42L));

        handler.afterConnectionEstablished(session);

        verify(sessionManager).register(42L, session);
    }

    @Test
    void afterConnectionEstablished_rejectsWhenNoUserId() throws IOException {
        when(session.getAttributes()).thenReturn(attrsWithUser(null));

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(sessionManager, never()).register(any(), any());
    }

    @Test
    void afterConnectionEstablished_closeThrowsIsSwallowed() throws IOException {
        when(session.getAttributes()).thenReturn(attrsWithUser(null));
        // 关闭无效连接本身再抛异常，必须被 catch 吞掉，不能上抛
        org.mockito.Mockito.doThrow(new IOException("boom")).when(session).close(any());

        handler.afterConnectionEstablished(session);

        verify(session).close(any());
    }

    @Test
    void handleTextMessage_pongOnPingWhenOpen() throws IOException {
        TextMessage ping = new TextMessage("{\"type\":\"ping\"}");
        when(session.isOpen()).thenReturn(true);

        handler.handleTextMessage(session, ping);

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void handleTextMessage_noSendForNonPing() throws IOException {
        TextMessage msg = new TextMessage("{\"type\":\"chat\",\"content\":\"hi\"}");
        // 非 ping 消息不读取 isOpen()，也不推送，故无需 stub isOpen（避免 UnnecessaryStubbing）

        handler.handleTextMessage(session, msg);

        verify(session, never()).sendMessage(any());
    }

    @Test
    void handleTextMessage_closedSessionDoesNotSend() throws IOException {
        TextMessage ping = new TextMessage("{\"type\":\"ping\"}");
        when(session.isOpen()).thenReturn(false);

        handler.handleTextMessage(session, ping);

        verify(session, never()).sendMessage(any());
    }

    @Test
    void handleTextMessage_malformedPayloadIsCaught() throws IOException {
        TextMessage bad = new TextMessage("this-is-not-json");
        // 异常分支会读 attributes.get("userId")，返回空 map 即可
        when(session.getAttributes()).thenReturn(new HashMap<>());

        handler.handleTextMessage(session, bad);

        verify(session, never()).sendMessage(any());
    }

    @Test
    void afterConnectionClosed_unregistersWhenUserIdPresent() {
        when(session.getAttributes()).thenReturn(attrsWithUser(7L));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(sessionManager).unregister(7L, session);
    }

    @Test
    void afterConnectionClosed_noUserDoesNothing() {
        when(session.getAttributes()).thenReturn(attrsWithUser(null));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(sessionManager, never()).unregister(any(), any());
    }

    @Test
    void handleTransportError_unregistersWhenUserIdPresent() {
        when(session.getAttributes()).thenReturn(attrsWithUser(9L));

        handler.handleTransportError(session, new RuntimeException("x"));

        verify(sessionManager).unregister(9L, session);
    }

    @Test
    void handleTransportError_noUserStillLogs() {
        when(session.getAttributes()).thenReturn(attrsWithUser(null));

        handler.handleTransportError(session, new RuntimeException("x"));

        verify(sessionManager, never()).unregister(any(), any());
    }

    @Test
    void getSubProtocols_returnsEmptyWhenNotSet() {
        // 确保 ThreadLocal 为空
        NegotiatedSubProtocol.clear();
        assertEquals(0, handler.getSubProtocols().size());
    }

    @Test
    void getSubProtocols_echoesNegotiatedProtocolAndClears() {
        NegotiatedSubProtocol.set("my-protocol");
        assertEquals(java.util.List.of("my-protocol"), handler.getSubProtocols());
        // 读取后应已清除
        assertEquals(0, handler.getSubProtocols().size());
    }
}
