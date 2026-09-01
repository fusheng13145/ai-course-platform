package com.aicourse.vo;

import lombok.Data;

/**
 * 管理端 AI 供应商当前状态出参。
 * 字段名与历史 Map 键一致（provider / llmName / configured），保证前端零改动。
 * <p>
 * 三个键在原实现中均无条件 put，故采用默认 INCLUDE.ALWAYS 策略（不加 NON_NULL）。
 */
@Data
public class AdminAiCurrentVO {

    /** 供应商标识（deepseek / openai / qwen），由 provider 名称解析而来 */
    private String provider;

    /** LLM 供应商原始名称，形如 router(deepseek) */
    private String llmName;

    /** 是否已配置 API Key */
    private boolean configured;

    public AdminAiCurrentVO() {
    }

    public AdminAiCurrentVO(String provider, String llmName, boolean configured) {
        this.provider = provider;
        this.llmName = llmName;
        this.configured = configured;
    }
}
