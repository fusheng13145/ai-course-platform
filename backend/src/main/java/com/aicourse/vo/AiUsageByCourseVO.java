package com.aicourse.vo;

import lombok.Data;

/**
 * 按课程聚合的 AI 用量行（跨课程 AI 统计 · 管理端）。
 *
 * <p>数据来源：{@code ai_call_logs} 与 {@code courses} 左连接后的分组聚合。
 * {@code courseId} 为 {@code null} 的行表示"未绑定课程"的调用（通识问答、作文批改等），
 * 该行必须保留展示——把它当成异常丢弃会让总量对不上。</p>
 */
@Data
public class AiUsageByCourseVO {

    /** 课程ID；null = 未绑定课程 */
    private Long courseId;

    /** 课程名称；null = 未绑定课程（含课程已删除的情况） */
    private String courseName;

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
