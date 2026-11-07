package com.aicourse.vo;

import lombok.Data;

/**
 * 按能力（调用场景）聚合的 AI 用量行（管理端，2026-11-07 P2 批次补齐「按能力维度」）。
 *
 * <p>数据来源：{@code ai_call_logs.scene} 分组聚合。scene 取值即 AiCallContext 登记的
 * 12 个场景（QA_ASK / QA_CONTINUE / ESSAY_CORRECT / QUESTION_GENERATE 等），
 * 前端将其映射为中文能力名展示。</p>
 */
@Data
public class AiUsageBySceneVO {

    /** 调用场景（QA_ASK / QA_CONTINUE / ESSAY_CORRECT / QUESTION_GENERATE / …） */
    private String scene;

    /** 调用次数 */
    private Long calls;

    /** token 估算合计（prompt + completion） */
    private Long tokens;

    /** 平均耗时(ms) */
    private Double avgDurationMs;

    /** 成功次数（success=1） */
    private Long successCalls;

    /** 成功率（0–1），由 successCalls / calls 计算，calls=0 时为 null */
    private Double successRate;
}
