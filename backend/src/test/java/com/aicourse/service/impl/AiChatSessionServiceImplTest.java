package com.aicourse.service.impl;

import com.aicourse.entity.AiChatMessage;
import com.aicourse.entity.AiChatSession;
import com.aicourse.mapper.AiChatSessionMapper;
import com.aicourse.service.AiChatMessageService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiChatSessionServiceImplTest {

    @Mock private AiChatMessageService aiChatMessageService;
    @Mock private AiChatSessionMapper aiChatSessionMapper;
    @InjectMocks private AiChatSessionServiceImpl aiChatSessionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiChatSessionService, "baseMapper", aiChatSessionMapper);
    }

    @Test
    @DisplayName("创建会话 - UUID+持久化")
    void testCreateSession() {
        AiChatSession r = aiChatSessionService.createSession(100L, "新对话");
        assertNotNull(r.getSessionId());
        assertEquals(100L, r.getUserId().longValue());
    }

    @Test
    @DisplayName("追加消息 - 调用 saveMessage + 更新会话")
    void testAppendMessage() {
        doNothing().when(aiChatMessageService).saveMessage("s1", "user", "你好");
        // getBySessionId → getOne(qw, false) → selectList
        AiChatSession session = new AiChatSession(); session.setId(1L); session.setSessionId("s1"); session.setMessageCount(0);
        when(aiChatSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(session));

        aiChatSessionService.appendMessage("s1", "user", "你好");

        verify(aiChatMessageService).saveMessage("s1", "user", "你好");
    }

    @Test
    @DisplayName("会话列表")
    void testListSessions() {
        AiChatSession s = new AiChatSession(); s.setSessionId("s1"); s.setUserId(100L);
        when(aiChatSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(s));

        assertEquals(1, aiChatSessionService.listSessions(100L).size());
    }

    @Test
    @DisplayName("查会话")
    void testGetBySessionId() {
        AiChatSession s = new AiChatSession(); s.setSessionId("s1");
        // getOne(false) → selectList
        when(aiChatSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(s));

        assertNotNull(aiChatSessionService.getBySessionId("s1"));
    }

    @Test
    @DisplayName("消息列表 - 委托 aiChatMessageService")
    void testGetMessages() {
        AiChatMessage m = new AiChatMessage(); m.setRole("user"); m.setContent("Hi");
        when(aiChatMessageService.listBySessionId("s1")).thenReturn(List.of(m));

        List<AiChatMessage> msgs = aiChatSessionService.getMessages("s1");

        assertEquals(1, msgs.size());
        assertEquals("user", msgs.get(0).getRole());
    }

    @Test
    @DisplayName("删除会话 - 逻辑删除")
    void testDeleteSession() {
        AiChatSession s = new AiChatSession(); s.setId(1L); s.setSessionId("s1"); s.setDeleted(0);
        lenient().when(aiChatSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(s));

        aiChatSessionService.deleteSession("s1");

        assertEquals(1, s.getDeleted().intValue());
    }
}
