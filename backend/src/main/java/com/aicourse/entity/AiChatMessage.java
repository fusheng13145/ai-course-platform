package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话消息实体
 * 表名：ai_chat_messages
 */
@TableName("ai_chat_messages")
@Data
public class AiChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private String sessionId;

    /** 角色：user / assistant */
    private String role;

    private String content;

    private Integer deleted;

    /** 自动填充创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}
