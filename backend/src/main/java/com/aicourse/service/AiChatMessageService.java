package com.aicourse.service;

import com.aicourse.entity.AiChatMessage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AiChatMessageService extends IService<AiChatMessage> {

    /**
     * 保存一条对话消息
     *
     * @param sessionId 业务会话ID
     * @param role     角色 user / assistant
     * @param content  消息内容
     */
    void saveMessage(String sessionId, String role, String content);

    /**
     * 查询会话下的消息（按创建时间正序）
     *
     * @param sessionId 业务会话ID
     * @return 消息列表
     */
    List<AiChatMessage> listBySessionId(String sessionId);
}
