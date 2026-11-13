package com.aicourse.mapper;

import com.aicourse.entity.LearningRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * LearningRecord 数据访问 Mapper 接口
 * 继承自 MyBatis-Plus 的 {@code BaseMapper}，提供该实体的基础 CRUD 操作，未定义额外自定义方法。
 */
@Mapper
public interface LearningRecordMapper extends BaseMapper<LearningRecord> {

    /**
     * 课程维度的按日活跃用户数聚合（V7 批：教师驾驶舱 14 天趋势从"每天 2 条串行查询×14"改为单条 GROUP BY）。
     *
     * @param courseId 课程ID
     * @param since    起始时间（含，含当天 00:00）
     * @return date=yyyy-MM-dd, active=当日去重活跃用户数；无记录的日期不出行，由调用方补零
     */
    @org.apache.ibatis.annotations.Select(
            "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS date, COUNT(DISTINCT user_id) AS active "
            + "FROM learning_records WHERE course_id = #{courseId} AND created_at >= #{since} "
            + "GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d')")
    java.util.List<java.util.Map<String, Object>> selectDailyActiveByCourse(
            @org.apache.ibatis.annotations.Param("courseId") Long courseId,
            @org.apache.ibatis.annotations.Param("since") java.time.LocalDateTime since);
}
