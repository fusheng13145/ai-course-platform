package com.aicourse.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebSocketSessionManager 单元测试。
 * 覆盖：register / unregister 的空值与前/后状态分支、sendToUser / broadcast 的
 * 序列化成功与失败（serialize 返回 null）分支、sendSafe 的关闭/IO 异常分支、
 * isOnline / onlineUserCount。
 */
@ExtendWith(MockitoExtension.class)
class WebSocketSessionManagerTest {

    private WebSocketSessionManager manager;
    private WebSocketSessionManager managerWithFailingMapper;

    @Mock
    private WebSocketSession session;
    @Mock
    private WebSocketSession session2;
    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper failingMapper;

    @BeforeEach
    void setUp() throws IOException {
        manager = new WebSocketSessionManager(new ObjectMapper());
        managerWithFailingMapper = new WebSocketSessionManager(failingMapper);

        lenient().when(session.getId()).thenReturn("s1");
        lenient().when(session2.getId()).thenReturn("s2");
        lenient().when(session.isOpen()).thenReturn(true);
        lenient().when(session2.isOpen()).thenReturn(true);
        // 用 thenAnswer 抛出，绕过 Mockito 对 checked exception 与方法声明兼容性的校验；
        // 抛出的 JsonMappingException 仍被 serialize() 的 catch(IOException) 捕获 -> 返回 null。
        lenient().when(failingMapper.writeValueAsString(any()))
                .thenAnswer(invocation -> {
                    throw new com.fasterxml.jackson.databind.JsonMappingException("serialize fail");
                });
    }

    @Test
    void register_nullArgsReturnsEarly() {
        manager.register(null, session);
        manager.register(1L, null);
        assertEquals(0, manager.onlineUserCount());
    }

    @Test
    void register_and_isOnline() {
        manager.register(1L, session);
        assertTrue(manager.isOnline(1L));
        assertEquals(1, manager.onlineUserCount());
        assertFalse(manager.isOnline(99L));
    }

    @Test
    void unregister_nullUserIdReturnsEarly() {
        manager.unregister(null, session);
        assertEquals(0, manager.onlineUserCount());
    }

    @Test
    void unregister_removesUserWhenLastSessionGone() {
        manager.register(1L, session);
        manager.register(1L, session2);
        // onlineUserCount 统计不同 userId 数，两个连接同属 userId=1，故为 1
        assertEquals(1, manager.onlineUserCount());
        manager.unregister(1L, session);
        assertTrue(manager.isOnline(1L));
        manager.unregister(1L, session2);
        assertFalse(manager.isOnline(1L));
        assertEquals(0, manager.onlineUserCount());
    }

    @Test
    void unregister_userNotPresentDoesNothing() {
        manager.unregister(55L, session);
        assertFalse(manager.isOnline(55L));
    }

    @Test
    void sendToUser_serializesAndSends() throws IOException {
        manager.register(1L, session);
        manager.sendToUser(1L, Map.of("k", "v"));

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendToUser_noSessionsReturnsEarly() throws IOException {
        manager.sendToUser(1L, "payload");
        verify(session, never()).sendMessage(any());
    }

    @Test
    void sendToUser_serializeFailureReturnsEarly() throws IOException {
        managerWithFailingMapper.register(1L, session);
        managerWithFailingMapper.sendToUser(1L, "payload");
        verify(session, never()).sendMessage(any());
    }

    @Test
    void broadcast_sendsToAll() throws IOException {
        manager.register(1L, session);
        manager.register(2L, session2);
        manager.broadcast(Map.of("msg", "hello"));

        verify(session).sendMessage(any(TextMessage.class));
        verify(session2).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcast_noSessionsReturnsEarly() throws IOException {
        manager.broadcast("payload");
        verify(session, never()).sendMessage(any());
    }

    @Test
    void broadcast_serializeFailureReturnsEarly() throws IOException {
        managerWithFailingMapper.register(1L, session);
        managerWithFailingMapper.broadcast("payload");
        verify(session, never()).sendMessage(any());
    }

    @Test
    void sendSafe_skipsWhenSessionClosed() throws IOException {
        when(session.isOpen()).thenReturn(false);
        manager.register(1L, session);
        manager.sendToUser(1L, "x");
        verify(session, never()).sendMessage(any());
    }

    @Test
    void sendSafe_ioExceptionIsSwallowed() throws IOException {
        doThrow(new IOException("closed")).when(session).sendMessage(any());
        manager.register(1L, session);
        // 不应抛出
        manager.sendToUser(1L, "x");
        verify(session).sendMessage(any());
    }
}
