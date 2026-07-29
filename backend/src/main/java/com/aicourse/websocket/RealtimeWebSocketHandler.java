package com.aicourse.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public RealtimeWebSocketHandler(WebSocketSessionManager sessionManager, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object userIdObj = session.getAttributes().get("userId");
        if (userIdObj instanceof Long userId) {
            sessionManager.register(userId, session);
            log.debug("WebSocket 连接已建立: userId={}, sessionId={}", userId, session.getId());
        } else {
            log.warn("WebSocket 连接缺少有效 userId，已拒绝: sessionId={}", session.getId());
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception ignored) {
                log.warn("关闭无效WebSocket连接异常", ignored);
            }
        }
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
            // 记录异常日志但不中断连接：单条脏数据不应导致通道断开，但必须可被运维观察到。
            // WARN 级别确保在默认日志配置下可见，同时避免单客户端畸形数据刷满 ERROR 日志。
            Object userIdObj = session.getAttributes().get("userId");
            log.warn("WebSocket 消息处理异常: userId={}, sessionId={}, payload={}",
                    userIdObj, session.getId(),
                    message.getPayloadLength() > 200
                            ? message.getPayload().substring(0, 200) + "..."
                            : message.getPayload(),
                    e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object userIdObj = session.getAttributes().get("userId");
        if (userIdObj instanceof Long userId) {
            sessionManager.unregister(userId, session);
            log.debug("WebSocket 连接已关闭: userId={}, sessionId={}, closeStatus={}", userId, session.getId(), status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Object userIdObj = session.getAttributes().get("userId");
        if (userIdObj instanceof Long userId) {
            sessionManager.unregister(userId, session);
        }
        log.warn("WebSocket 传输异常: userId={}, sessionId={}", userIdObj, session.getId(), exception);
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
