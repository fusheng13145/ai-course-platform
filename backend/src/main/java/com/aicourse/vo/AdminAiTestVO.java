package com.aicourse.vo;

import lombok.Data;

/**
 * 管理端 AI 供应商连通性测试出参。
 * 字段名与历史 Map 键一致（ok / provider / configured / message），保证前端零改动。
 * <p>
 * 四个键在原实现中均无条件 put，故采用默认 INCLUDE.ALWAYS 策略（不加 NON_NULL）。
 */
@Data
public class AdminAiTestVO {

    /** 连通性测试是否通过 */
    private boolean ok;

    /** 供应商标识（deepseek / openai / qwen） */
    private String provider;

    /** 是否已配置 API Key */
    private boolean configured;

    /** 面向管理员的可读提示语 */
    private String message;

    public AdminAiTestVO() {
    }

    public AdminAiTestVO(boolean ok, String provider, boolean configured, String message) {
        this.ok = ok;
        this.provider = provider;
        this.configured = configured;
        this.message = message;
    }
}
