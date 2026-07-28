package com.aicourse.mapper;

import com.aicourse.entity.AiChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 对话会话 Mapper
 */
@Mapper
public interface AiChatSessionMapper extends BaseMapper<AiChatSession> {
}
