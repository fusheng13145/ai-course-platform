package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * AI 对话会话实体
 * 表名：ai_chat_sessions
 */
@TableName("ai_chat_sessions")
@Data
public class AiChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务会话ID（UUID），对外暴露，避免暴露自增主键 */
    @TableField("session_id")
    private String sessionId;

    @TableField("user_id")
    private Long userId;

    private String title;

    @TableField("message_count")
    private Integer messageCount;

    private Integer deleted;

    /** 自动填充创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 自动填充更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

}
