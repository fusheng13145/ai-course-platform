package com.aicourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 智能答疑请求。
 *
 * <p>安全说明：{@code question} 施加长度硬上限（{@value #MAX_QUESTION_LEN} 字符），
 * 从入口杜绝「超长 prompt 单次吃掉数千 token」的 LLM 成本型 DoS——即使限流按次放行，
 * 单次请求的 token 成本上界也被锁死。</p>
 */
@Data
public class QaAskRequest {

    /** 单次提问的字符硬上限（约对应数千 token 级别的成本天花板）。 */
    public static final int MAX_QUESTION_LEN = 4000;

    @NotBlank(message = "问题内容不能为空")
    @Size(max = MAX_QUESTION_LEN, message = "问题内容过长，单次提问不得超过 " + MAX_QUESTION_LEN + " 字符")
    private String question;
    private String courseId;
    private String sessionId;
}
