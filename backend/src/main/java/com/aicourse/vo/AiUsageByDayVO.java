package com.aicourse.vo;

import lombok.Data;

/**
 * 按日聚合的 AI 用量行（管理端，2026-11-07 P2 批次补齐「按日维度」）。
 *
 * <p>数据来源：{@code ai_call_logs} 按 {@code DATE(created_at)} 分组聚合。
 * 没有调用的日期不出行（调用日志表只存事实行），前端按区间补零渲染趋势。
 * 院系口径（ForCollege 变体）为内连接 {@code courses.college_id}，未绑定课程的
 * 调用无学院归属、不计入本院视图——与 {@link AiUsageByCourseVO} 的院系口径一致。</p>
 */
@Data
public class AiUsageByDayVO {

    /** 日期（yyyy-MM-dd，按服务器时区） */
    private String day;

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
