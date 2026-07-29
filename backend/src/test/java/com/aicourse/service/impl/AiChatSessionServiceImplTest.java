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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 聊天会话服务单元测试。
 * 注意：ServiceImpl.getOne() 在不同 MP 版本中可能调用 selectList 或 selectOne，
 * 因此同时 stub 两者以保证兼容。
 */
@ExtendWith(MockitoExtension.class)
class AiChatSessionServiceImplTest {

    @Mock private AiChatMessageService aiChatMessageService;
    @Mock private AiChatSessionMapper aiChatSessionMapper;
    @InjectMocks private AiChatSessionServiceImpl aiChatSessionService;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(aiChatSessionService, "baseMapper", aiChatSessionMapper);
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
        lenient().doNothing().when(aiChatMessageService).saveMessage("s1", "user", "你好");
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
        // getOne(qw, false) 内部可能走 selectList 或 selectOne，同时 stub 两者
        when(aiChatSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(s));
        when(aiChatSessionMapper.selectOne(any(Wrapper.class))).thenReturn(s);
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
        // deleteSession 中调用 getBySessionId，内部走 selectList
        lenient().when(aiChatSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(s));
        lenient().when(aiChatSessionMapper.selectOne(any(Wrapper.class))).thenReturn(s);
        aiChatSessionService.deleteSession("s1");
        assertEquals(1, s.getDeleted().intValue());
    }
}
