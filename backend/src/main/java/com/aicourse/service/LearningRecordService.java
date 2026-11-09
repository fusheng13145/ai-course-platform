package com.aicourse.service;

import com.aicourse.entity.LearningRecord;
import com.aicourse.vo.DailyLearningStatVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;


/**
 * 学习记录接口：相关业务逻辑入口，提供增删改查与编排能力。
 */
public interface LearningRecordService extends IService<LearningRecord> {

    /**
     * 课程维度按日活跃用户数聚合（V7 批：教师驾驶舱 14 天趋势的单条 GROUP BY，
     * 取代"每天一条串行 list × 14"的 N 次往返）。
     * 无记录的日期不出行（date=yyyy-MM-dd, active=当日去重活跃用户数），由调用方补零。
     */
    java.util.List<java.util.Map<String, Object>> dailyActiveByCourse(Long courseId, java.time.LocalDateTime since);

    /**
     * 获取用户学习统计数据
     * @param userId 用户ID
     * @return 统计数据Map
     */
    Map<String, Object> getLearningStats(Long userId);

    /**
     * 获取用户学习趋势数据
     * @param userId 用户ID
     * @param days 天数
     * @return 趋势数据列表
     */
    List<DailyLearningStatVO> getLearningTrend(Long userId, int days);

    /**
     * 获取用户月度学习数据（用于日历）
     * @param userId 用户ID
     * @param year 年份
     * @param month 月份
     * @return 每日学习数据
     */
    List<DailyLearningStatVO> getMonthlyLearningData(Long userId, int year, int month);
}
