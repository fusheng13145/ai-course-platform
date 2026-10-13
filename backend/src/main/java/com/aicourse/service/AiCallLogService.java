package com.aicourse.service;

import java.util.Map;

/**
 * AI 调用日志与统计服务：为"跨课程 AI 统计"提供唯一数据面。
 *
 * <p>职责边界：
 * <ul>
 *   <li>写入——每次真实发起的大模型调用落一行 {@code ai_call_logs}（由 {@code AiCoreComponent} 调用）；</li>
 *   <li>聚合——按课程维度的管理端统计。</li>
 * </ul>
 *
 * <p>"调用归属"（userId / courseId / scene）不经本接口传递，而是由控制器用
 * {@code AiCallContext#run} 建立线程级作用域（见其类注释）。</p>
 *
 * <p>⚠️ <b>写入必须失败安全</b>：埋点异常绝不能影响 AI 主流程（否则"统计"会把"问答"搞挂）。
 */
public interface AiCallLogService {

    /**
     * 记录一次 AI 调用。
     *
     * <p>本方法自身捕获全部异常（只记 warn 日志），调用方无需 try/catch。</p>
     *
     * @param userId       调用者用户ID（可空）
     * @param courseId     归属课程ID（可空，表示未绑定课程）
     * @param scene        业务场景（见 V8 迁移的列注释）
     * @param provider     供应商名（未配置则为 null）
     * @param promptTokens 输入 token 估算
     * @param outputText   模型输出原文（用于估算 completion token；失败传 null）
     * @param durationMs   本次调用耗时(ms)
     * @param success      是否成功（降级为兜底文案按失败计）
     * @param errorMsg     失败原因（可空，超长自动截断）
     */
    void record(Long userId, Long courseId, String scene, String provider,
                long promptTokens, String outputText, long durationMs,
                boolean success, String errorMsg);

    /**
     * 管理端统计：最近 {@code days} 天的 AI 用量（按课程聚合 + 总览）。
     *
     * <p>返回结构：{@code rangeDays / since / until / note / totalCalls / totalTokens /
     * distinctCourses / unboundCalls / avgDurationMs / successRate / courses[]}。</p>
     *
     * @param days 统计天数（1–365，越界自动收敛）
     * @return 统计结果
     */
    Map<String, Object> statistics(int days);
}
