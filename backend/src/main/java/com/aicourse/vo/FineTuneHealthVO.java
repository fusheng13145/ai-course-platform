package com.aicourse.vo;

import lombok.Data;

/**
 * 微调（LoRA）服务健康检查出参。
 * 字段名与历史 Map 键一致（available），保证前端零改动。
 * <p>
 * 该键在原实现中无条件 put，故采用默认 INCLUDE.ALWAYS 策略（不加 NON_NULL）。
 */
@Data
public class FineTuneHealthVO {

    /** 微调服务是否可达（对应 LLMFineTuneService.healthCheck()） */
    private boolean available;

    public FineTuneHealthVO() {
    }

    public FineTuneHealthVO(boolean available) {
        this.available = available;
    }
}
