package com.aicourse.service;

import com.aicourse.entity.LearningRecord;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;


/**
 * 学习记录接口：相关业务逻辑入口，提供增删改查与编排能力。
 */
public interface LearningRecordService extends IService<LearningRecord> {

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
    List<Map<String, Object>> getLearningTrend(Long userId, int days);

    /**
     * 获取用户月度学习数据（用于日历）
     * @param userId 用户ID
     * @param year 年份
     * @param month 月份
     * @return 每日学习数据
     */
    List<Map<String, Object>> getMonthlyLearningData(Long userId, int year, int month);
}
