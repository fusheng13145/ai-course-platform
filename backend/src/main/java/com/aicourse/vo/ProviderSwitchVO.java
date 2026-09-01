package com.aicourse.vo;

import lombok.Data;

/**
 * AI 供应商切换结果出参（POST /admin/ai/provider/switch）。
 * 字段名与历史 Map 键一致（provider / llmName）。
 * 原实现无条件 put 此两键（恒含），故默认 INCLUDE.ALWAYS，精确复刻。
 */
@Data
public class ProviderSwitchVO {

    private String provider;
    private String llmName;

    public ProviderSwitchVO() {
    }

    public ProviderSwitchVO(String provider, String llmName) {
        this.provider = provider;
        this.llmName = llmName;
    }
}
