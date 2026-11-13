package com.aicourse.mapper;

import com.aicourse.entity.AiCallLog;
import com.aicourse.vo.AiUsageByCourseVO;
import com.aicourse.vo.AiUsageByDayVO;
import com.aicourse.vo.AiUsageBySceneVO;
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

    /**
     * 按课程聚合指定时间窗内、且属于指定学院的 AI 用量。
     *
     * <p>与 {@link #selectUsageByCourse} 的口径差异：使用内连接并按 {@code courses.college_id}
     * 过滤，因此 {@code course_id IS NULL} 的平台级调用不会出现在院系视图中（无学院归属）。</p>
     *
     * @param from 起始时间（含）
     * @param to   结束时间（不含）
     * @param collegeId 学院ID
     * @return 该学院按调用次数倒序的聚合行
     */
    @Select("""
            SELECT l.course_id    AS courseId,
                   c.name         AS courseName,
                   COUNT(*)       AS calls,
                   COALESCE(SUM(l.prompt_tokens + l.completion_tokens), 0) AS tokens,
                   COALESCE(AVG(l.duration_ms), 0)                          AS avgDurationMs,
                   COALESCE(SUM(CASE WHEN l.success = 1 THEN 1 ELSE 0 END), 0) AS successCalls
            FROM ai_call_logs l
            INNER JOIN courses c ON c.id = l.course_id
            WHERE l.created_at >= #{from} AND l.created_at < #{to}
              AND c.college_id = #{collegeId}
            GROUP BY l.course_id, c.name
            ORDER BY calls DESC, l.course_id ASC
            """)
    List<AiUsageByCourseVO> selectUsageByCourseForCollege(@Param("from") LocalDateTime from,
                                                         @Param("to") LocalDateTime to,
                                                         @Param("collegeId") Long collegeId);

    /**
     * 按日聚合指定时间窗内的 AI 用量（2026-11-07 P2 批次：补齐「按日维度」）。
     *
     * <p>时间窗为左闭右开 {@code [from, to)}；没有调用的日期不出行，由前端按区间补零。
     * day 按服务器时区的 {@code DATE(created_at)} 归组。</p>
     *
     * @return 按日期升序的聚合行
     */
    @Select("""
            SELECT DATE_FORMAT(l.created_at, '%Y-%m-%d') AS day,
                   COUNT(*)       AS calls,
                   COALESCE(SUM(l.prompt_tokens + l.completion_tokens), 0) AS tokens,
                   COALESCE(AVG(l.duration_ms), 0)                          AS avgDurationMs,
                   COALESCE(SUM(CASE WHEN l.success = 1 THEN 1 ELSE 0 END), 0) AS successCalls
            FROM ai_call_logs l
            WHERE l.created_at >= #{from} AND l.created_at < #{to}
            GROUP BY DATE_FORMAT(l.created_at, '%Y-%m-%d')
            ORDER BY day ASC
            """)
    List<AiUsageByDayVO> selectUsageByDay(@Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    /**
     * 按日聚合指定时间窗内、且属于指定学院的 AI 用量（内连接口径，未绑定课程不计入）。
     */
    @Select("""
            SELECT DATE_FORMAT(l.created_at, '%Y-%m-%d') AS day,
                   COUNT(*)       AS calls,
                   COALESCE(SUM(l.prompt_tokens + l.completion_tokens), 0) AS tokens,
                   COALESCE(AVG(l.duration_ms), 0)                          AS avgDurationMs,
                   COALESCE(SUM(CASE WHEN l.success = 1 THEN 1 ELSE 0 END), 0) AS successCalls
            FROM ai_call_logs l
            INNER JOIN courses c ON c.id = l.course_id
            WHERE l.created_at >= #{from} AND l.created_at < #{to}
              AND c.college_id = #{collegeId}
            GROUP BY DATE_FORMAT(l.created_at, '%Y-%m-%d')
            ORDER BY day ASC
            """)
    List<AiUsageByDayVO> selectUsageByDayForCollege(@Param("from") LocalDateTime from,
                                                    @Param("to") LocalDateTime to,
                                                    @Param("collegeId") Long collegeId);

    /**
     * 按能力（调用场景 scene）聚合指定时间窗内的 AI 用量（补齐「按能力维度」）。
     *
     * @return 按调用次数倒序的聚合行
     */
    @Select("""
            SELECT l.scene        AS scene,
                   COUNT(*)       AS calls,
                   COALESCE(SUM(l.prompt_tokens + l.completion_tokens), 0) AS tokens,
                   COALESCE(AVG(l.duration_ms), 0)                          AS avgDurationMs,
                   COALESCE(SUM(CASE WHEN l.success = 1 THEN 1 ELSE 0 END), 0) AS successCalls
            FROM ai_call_logs l
            WHERE l.created_at >= #{from} AND l.created_at < #{to}
            GROUP BY l.scene
            ORDER BY calls DESC, l.scene ASC
            """)
    List<AiUsageBySceneVO> selectUsageByScene(@Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    /**
     * 按能力聚合指定时间窗内、且属于指定学院的 AI 用量（内连接口径，未绑定课程不计入）。
     */
    @Select("""
            SELECT l.scene        AS scene,
                   COUNT(*)       AS calls,
                   COALESCE(SUM(l.prompt_tokens + l.completion_tokens), 0) AS tokens,
                   COALESCE(AVG(l.duration_ms), 0)                          AS avgDurationMs,
                   COALESCE(SUM(CASE WHEN l.success = 1 THEN 1 ELSE 0 END), 0) AS successCalls
            FROM ai_call_logs l
            INNER JOIN courses c ON c.id = l.course_id
            WHERE l.created_at >= #{from} AND l.created_at < #{to}
              AND c.college_id = #{collegeId}
            GROUP BY l.scene
            ORDER BY calls DESC, l.scene ASC
            """)
    List<AiUsageBySceneVO> selectUsageBySceneForCollege(@Param("from") LocalDateTime from,
                                                        @Param("to") LocalDateTime to,
                                                        @Param("collegeId") Long collegeId);
}
