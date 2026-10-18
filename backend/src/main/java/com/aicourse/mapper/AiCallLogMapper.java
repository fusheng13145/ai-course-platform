package com.aicourse.mapper;

import com.aicourse.entity.AiCallLog;
import com.aicourse.vo.AiUsageByCourseVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 调用日志 Mapper
 */
@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {

    /**
     * 按课程聚合指定时间窗内的 AI 用量。
     *
     * <p>时间窗为左闭右开 {@code [from, to)}，与前端"最近 N 天"的口径一致；
     * 关联 {@code courses} 取课程名，未绑定课程（{@code course_id IS NULL}）与课程已删除
     * 的行都会作为独立分组返回（左连接，不丢行）。</p>
     *
     * @param from 起始时间（含）
     * @param to   结束时间（不含）
     * @return 按调用次数倒序的聚合行
     */
    @Select("""
            SELECT l.course_id    AS courseId,
                   c.name         AS courseName,
                   COUNT(*)       AS calls,
                   COALESCE(SUM(l.prompt_tokens + l.completion_tokens), 0) AS tokens,
                   COALESCE(AVG(l.duration_ms), 0)                          AS avgDurationMs,
                   COALESCE(SUM(CASE WHEN l.success = 1 THEN 1 ELSE 0 END), 0) AS successCalls
            FROM ai_call_logs l
            LEFT JOIN courses c ON c.id = l.course_id
            WHERE l.created_at >= #{from} AND l.created_at < #{to}
            GROUP BY l.course_id, c.name
            ORDER BY calls DESC, l.course_id ASC
            """)
    List<AiUsageByCourseVO> selectUsageByCourse(@Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);
}
