package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.AiChatMessage;
import com.aicourse.entity.AiChatSession;
import com.aicourse.mapper.AiChatSessionMapper;
import com.aicourse.service.AiChatMessageService;
import com.aicourse.service.AiChatSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;


/**
 * AI聊天会话服务实现：相关业务逻辑入口，提供增删改查与编排能力。
 */
@Service
public class AiChatSessionServiceImpl extends ServiceImpl<AiChatSessionMapper, AiChatSession> implements AiChatSessionService {

    private static final Logger log = LoggerFactory.getLogger(AiChatSessionServiceImpl.class);

    private final AiChatMessageService aiChatMessageService;

    public AiChatSessionServiceImpl(AiChatMessageService aiChatMessageService) {
        this.aiChatMessageService = aiChatMessageService;
    }


    /** 创建AI聊天会话 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiChatSession createSession(Long userId, String title) {
        log.info("创建AI聊天会话");

        AiChatSession session = new AiChatSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setTitle(title != null && !title.isBlank() ? title : "新对话");
        session.setMessageCount(0);
        session.setDeleted(0);
        this.save(session);
        return session;
    }


    /** AI聊天会话相关操作 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appendMessage(String sessionId, String role, String content) {
        // 1. 持久化消息
        aiChatMessageService.saveMessage(sessionId, role, content);

        // 2. 维护会话的消息数与更新时间，首条用户消息自动作为标题
        AiChatSession session = this.getBySessionId(sessionId);
        if (session != null) {
            int count = (session.getMessageCount() == null ? 0 : session.getMessageCount()) + 1;
            session.setMessageCount(count);
            if (count == 1 && "user".equals(role)
                    && ("新对话".equals(session.getTitle()) || session.getTitle() == null)) {
                String t = content != null && content.length() > 20 ? content.substring(0, 20) + "…" : content;
                session.setTitle(t);
            }
            this.updateById(session);
        }
    }


    /** 查询AI聊天会话 */
    @Override
    public List<AiChatSession> listSessions(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AiChatSession> qw = new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getUserId, userId)
                .eq(AiChatSession::getDeleted, 0)
                .orderByDesc(AiChatSession::getUpdatedAt);
        return this.list(qw);
    }


    /** 查询AI聊天会话 */
    @Override
    public AiChatSession getBySessionId(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        LambdaQueryWrapper<AiChatSession> qw = new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getSessionId, sessionId)
                .eq(AiChatSession::getDeleted, 0);
        return this.getOne(qw, false);
    }


    /** 查询AI聊天会话 */
    @Override
    public List<AiChatMessage> getMessages(String sessionId) {
        return aiChatMessageService.listBySessionId(sessionId);
    }


    /** 删除AI聊天会话 */
    @Override
    public void deleteSession(String sessionId) {
        log.info("删除AI聊天会话");

        AiChatSession session = this.getBySessionId(sessionId);
        if (session != null) {
            session.setDeleted(1);
            this.updateById(session);
        }
    }
}
