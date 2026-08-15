package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.AiChatMessage;
import com.aicourse.mapper.AiChatMessageMapper;
import com.aicourse.service.AiChatMessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;


/**
 * AI聊天消息服务实现：相关业务逻辑入口，提供增删改查与编排能力。
 */
@Service
public class AiChatMessageServiceImpl extends ServiceImpl<AiChatMessageMapper, AiChatMessage> implements AiChatMessageService {

    private static final Logger log = LoggerFactory.getLogger(AiChatMessageServiceImpl.class);


    /** 保存AI聊天消息 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMessage(String sessionId, String role, String content) {
        log.info("保存AI聊天消息");

        AiChatMessage msg = new AiChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setDeleted(0);
        this.save(msg);
    }


    /** 查询AI聊天消息 */
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
