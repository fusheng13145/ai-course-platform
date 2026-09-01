package com.aicourse.service.impl;

import com.aicourse.entity.AiChatMessage;
import com.aicourse.mapper.AiChatMessageMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiChatMessageServiceImpl 单元测试（纯 Mockito）。
 * <p>覆盖保存消息（字段落库）与按会话查询（null 兜底 / 正常查询）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiChatMessageServiceImplTest {

    @Mock
    private AiChatMessageMapper aiChatMessageMapper;

    private AiChatMessageServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiChatMessage.class);
        service = new AiChatMessageServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", aiChatMessageMapper);
    }

    @Test
    @DisplayName("saveMessage 构造实体并落库（deleted=0）")
    void saveMessage() {
        service.saveMessage("sid-1", "user", "你好");
        verify(aiChatMessageMapper).insert(argThat((AiChatMessage m) ->
                "sid-1".equals(m.getSessionId())
                        && "user".equals(m.getRole())
                        && "你好".equals(m.getContent())
                        && Integer.valueOf(0).equals(m.getDeleted())));
    }

    @Test
    @DisplayName("listBySessionId 空 sessionId 返回空列表且不查库")
    void listBySessionId_null() {
        assertTrue(service.listBySessionId(null).isEmpty());
        verify(aiChatMessageMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("listBySessionId 按会话查询并按时间升序")
    void listBySessionId_normal() {
        AiChatMessage m = new AiChatMessage();
        m.setId(1L);
        m.setSessionId("sid-1");
        m.setContent("回复");
        when(aiChatMessageMapper.selectList(any())).thenReturn(Collections.singletonList(m));

        List<AiChatMessage> list = service.listBySessionId("sid-1");
        assertEquals(1, list.size());
        assertEquals("sid-1", list.get(0).getSessionId());
        assertEquals("回复", list.get(0).getContent());
        verify(aiChatMessageMapper).selectList(any());
    }
}
