package com.aicourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 作文批改 / 润色请求。
 *
 * <p>安全说明：{@code content} 施加长度硬上限（{@value #MAX_CONTENT_LEN} 字符）。
 * 作文场景允许比问答更长的正文，但仍需封顶以锁死单次 LLM 调用的 token 成本上界，
 * 防止「贴满整篇论文」式的成本型 DoS。</p>
 */
@Data
public class EssayDTO {

    /** 作文正文的字符硬上限。 */
    public static final int MAX_CONTENT_LEN = 8000;

    @NotBlank(message = "内容不能为空")
    @Size(max = MAX_CONTENT_LEN, message = "内容过长，单次提交不得超过 " + MAX_CONTENT_LEN + " 字符")
    private String content;
}
