package com.aicourse.service;

import com.aicourse.entity.AiChatMessage;
import com.aicourse.entity.AiChatSession;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


/**
 * AI聊天会话接口：相关业务逻辑入口，提供增删改查与编排能力。
 */
public interface AiChatSessionService extends IService<AiChatSession> {

    /**
     * 创建新的 AI 对话会话
     *
     * @param userId   用户ID
     * @param title    会话标题（可为 null，首条用户消息将自动成为标题）
     * @param courseId 关联课程ID（可为 null = 不绑定课程的通识问答）
     * @return 新建的会话实体（含业务 sessionId）
     */
    AiChatSession createSession(Long userId, String title, Long courseId);

    /**
     * 向会话追加一条消息，并维护会话的消息数与更新时间
     *
     * @param sessionId 业务会话ID
     * @param role     角色 user / assistant
     * @param content  消息内容
     */
    void appendMessage(String sessionId, String role, String content);

    /**
     * 查询指定用户的会话列表（按更新时间倒序）
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    List<AiChatSession> listSessions(Long userId);

    /**
     * 根据业务会话ID查询会话
     *
     * @param sessionId 业务会话ID
     * @return 会话实体（未找到返回 null）
     */
    AiChatSession getBySessionId(String sessionId);

    /**
     * 查询会话下的全部消息（按创建时间正序）
     *
     * @param sessionId 业务会话ID
     * @return 消息列表
     */
    List<AiChatMessage> getMessages(String sessionId);

    /**
     * 逻辑删除会话
     *
     * @param sessionId 业务会话ID
     */
    void deleteSession(String sessionId);
}
