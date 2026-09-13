package com.aicourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 继续对话请求。
 *
 * <p>安全说明：{@code message} 施加长度硬上限（{@value #MAX_MESSAGE_LEN} 字符），
 * 锁死单轮对话的 token 成本上界，防止超长 prompt 造成 LLM 成本型 DoS。</p>
 */
@Data
public class QaContinueDTO {

    /** 单轮消息的字符硬上限。 */
    public static final int MAX_MESSAGE_LEN = 4000;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = MAX_MESSAGE_LEN, message = "消息内容过长，单轮对话不得超过 " + MAX_MESSAGE_LEN + " 字符")
    private String message;
    private String sessionId;
}
