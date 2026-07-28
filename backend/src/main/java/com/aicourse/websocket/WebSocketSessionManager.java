package com.aicourse.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话注册中心。
 *
 * <p>维护 {@code userId -> 该用户全部在线连接} 的映射，提供按用户精准推送与全量广播能力。
 * 所有推送均为尽力而为（Best-Effort）：连接已断开、序列化失败仅记录日志，绝不影响业务主流程。</p>
 */
@Service
public class WebSocketSessionManager {

    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public WebSocketSessionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 连接建立时登记 */
    public void register(Long userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    /** 连接关闭时注销 */
    public void unregister(Long userId, WebSocketSession session) {
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
        }
    }

    /** 向指定用户的所有在线连接推送消息对象（自动序列化为 JSON） */
    public void sendToUser(Long userId, Object payload) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String text = serialize(payload);
        if (text == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            sendSafe(session, text);
        }
    }

    /** 全量广播（如系统公告） */
    public void broadcast(Object payload) {
        String text = serialize(payload);
        if (text == null) {
            return;
        }
        for (Set<WebSocketSession> sessions : userSessions.values()) {
            for (WebSocketSession session : sessions) {
                sendSafe(session, text);
            }
        }
    }

    /** 用户当前是否在线（存在任一打开的连接） */
    public boolean isOnline(Long userId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /** 当前在线用户数 */
    public int onlineUserCount() {
        return userSessions.size();
    }

    private void sendSafe(WebSocketSession session, String text) {
        if (session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(text));
            } catch (IOException e) {
                // 连接可能已断开，交由 close 事件统一清理，此处不抛异常
            }
        }
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            return null;
        }
    }
}
