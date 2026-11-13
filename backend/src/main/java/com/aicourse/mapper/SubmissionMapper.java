package com.aicourse.mapper;

import com.aicourse.entity.Submission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Submission 数据访问 Mapper 接口
 * 继承自 MyBatis-Plus 的 {@code BaseMapper}，提供该实体的基础 CRUD 操作，未定义额外自定义方法。
 */
@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {

    /**
     * 任务集合维度的按日提交数聚合（V7 批：同上，取代 14 天 × 1 条串行 count）。
     *
     * @param taskIds 任务ID集合（课程内全部任务；空集合由调用方短路）
     * @param since   起始时间（含）
     * @return date=yyyy-MM-dd, subs=当日提交数；无记录的日期不出行
     */
    @org.apache.ibatis.annotations.Select(
            "<script>SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS date, COUNT(*) AS subs "
            + "FROM submissions WHERE task_id IN "
            + "<foreach collection='taskIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "AND created_at >= #{since} GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d')</script>")
    java.util.List<java.util.Map<String, Object>> selectDailySubmissionsByTasks(
            @org.apache.ibatis.annotations.Param("taskIds") java.util.List<Long> taskIds,
            @org.apache.ibatis.annotations.Param("since") java.time.LocalDateTime since);
}
