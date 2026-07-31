package com.aicourse.service.impl;

import com.aicourse.entity.AiChatMessage;
import com.aicourse.mapper.AiChatMessageMapper;
import com.aicourse.service.AiChatMessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class AiChatMessageServiceImpl extends ServiceImpl<AiChatMessageMapper, AiChatMessage> implements AiChatMessageService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMessage(String sessionId, String role, String content) {
        AiChatMessage msg = new AiChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setDeleted(0);
        this.save(msg);
    }

    @Override
    public List<AiChatMessage> listBySessionId(String sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AiChatMessage> qw = new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .eq(AiChatMessage::getDeleted, 0)
                .orderByAsc(AiChatMessage::getCreatedAt);
        return this.list(qw);
    }
}
