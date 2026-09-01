package com.aicourse.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 投票实时结果统计出参。
 * 字段名与历史 Map 键一致：total(总票数)/counts(选项→票数，动态键 Map)/textAnswers(文本题答案列表)/mode(投票模式)。
 * counts 为动态键 Map（键即投票选项标识），保留 {@code Map<String,Long>} 不强行结构化。
 * 原实现用 LinkedHashMap 无条件 put 四键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 */
@Data
public class VoteResultVO {

    private Long total;
    private Map<String, Long> counts;
    private List<String> textAnswers;
    private String mode;

    public VoteResultVO() {
    }

    public VoteResultVO(Long total, Map<String, Long> counts, List<String> textAnswers, String mode) {
        this.total = total;
        this.counts = counts;
        this.textAnswers = textAnswers;
        this.mode = mode;
    }
}
