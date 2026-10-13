package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 调用日志实体
 * 表名：ai_call_logs
 *
 * <p>跨课程 AI 统计的<b>唯一数据源</b>：每次实际发起的大模型调用落一行，
 * 记录调用者、归属课程、业务场景、供应商、token 估算、耗时与成败。</p>
 *
 * <p>⚠️ 该表自 2026-10-13 埋点上线才开始有数据——此前的调用没有留痕，**无法回填**。
 * 任何基于本表的"历史趋势"都必须标注起算日，否则即为伪造。</p>
 */
@TableName("ai_call_logs")
@Data
public class AiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调用者用户ID；未认证为空 */
    @TableField("user_id")
    private Long userId;

    /** 关联课程ID；NULL 表示未绑定课程（通识问答/作文批改等） */
    @TableField("course_id")
    private Long courseId;

    /** 业务场景，见 {@code V8__ai_call_logging.sql} 列注释 */
    private String scene;

    /** LLM 供应商（deepseek/openai/qwen）；未配置时为 null */
    private String provider;

    /** 输入 token 估算 */
    @TableField("prompt_tokens")
    private Integer promptTokens;

    /** 输出 token 估算 */
    @TableField("completion_tokens")
    private Integer completionTokens;

    /** AI 调用耗时(ms) */
    @TableField("duration_ms")
    private Integer durationMs;

    /** 1-成功；0-失败或降级为兜底文案 */
    private Integer success;

    /** 失败原因（截断 255 字符） */
    @TableField("error_msg")
    private String errorMsg;

    /** 自动填充创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
