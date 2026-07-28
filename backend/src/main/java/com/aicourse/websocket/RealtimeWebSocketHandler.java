package com.aicourse.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;

/**
 * 实时通信 WebSocket 处理器。
 *
 * <p>职责：连接登记、心跳（ping/pong）、以及作为实时推送的出口（推送由
 * {@link WebSocketSessionManager} 驱动，本类在连接事件中提取 userId 完成登记/注销）。</p>
 */
@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public RealtimeWebSocketHandler(WebSocketSessionManager sessionManager, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        sessionManager.register(userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = node.path("type").asText("");
            if ("ping".equals(type)) {
                // 心跳应答，保持连接存活
                Map<String, Object> pong = Map.of("type", "pong", "timestamp", System.currentTimeMillis());
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
                }
            }
            // 其它业务消息类型（如 AI 流式输入）可在此后扩展
        } catch (Exception e) {
            // 忽略格式错误的客户端消息，避免单条脏数据中断连接
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        sessionManager.unregister(userId, session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = (Long) session.getAttributes().get("userId");
        sessionManager.unregister(userId, session);
    }

    /**
     * 协商子协议：回显客户端在握手中声明的 JWT，满足 RFC 6455 要求（否则浏览器会关闭连接）。
     * 读取后清空 ThreadLocal，避免线程池复用导致的值泄漏。
     */
    @Override
    public List<String> getSubProtocols() {
        String protocol = NegotiatedSubProtocol.get();
        NegotiatedSubProtocol.clear();
        return protocol != null ? List.of(protocol) : List.of();
    }
}
