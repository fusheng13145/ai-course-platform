package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.LearningRecord;
import com.aicourse.mapper.LearningRecordMapper;
import com.aicourse.service.LearningRecordService;
import com.aicourse.vo.DailyLearningStatVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 学习记录服务实现：相关业务逻辑入口，提供增删改查与编排能力。
 */
@Service
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements LearningRecordService {

    private static final Logger log = LoggerFactory.getLogger(LearningRecordServiceImpl.class);


    /** 查询学习记录 */
    @Override
    public Map<String, Object> getLearningStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        
        LambdaQueryWrapper<LearningRecord> queryWrapper = new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId);
        
        List<LearningRecord> records = this.list(queryWrapper);
        
        // 总学习时长（分钟）
        int totalDuration = records.stream()
                .mapToInt(r -> r.getDuration() != null ? r.getDuration() : 0)
                .sum();
        stats.put("totalDuration", totalDuration);
        
        // 学习天数
        Set<String> learningDays = records.stream()
                .filter(r -> r.getCreatedAt() != null)
                .map(r -> r.getCreatedAt().toLocalDate().toString())
                .collect(Collectors.toSet());
        stats.put("learningDays", learningDays.size());
        
        // 完成课程数（去重）
        Set<Long> completedCourses = records.stream()
                .filter(r -> r.getCourseId() != null && r.getProgress() != null && r.getProgress().doubleValue() >= 1.0)
                .map(LearningRecord::getCourseId)
                .collect(Collectors.toSet());
        stats.put("completedCourses", completedCourses.size());
        
        // 当前学习课程数
        Set<Long> currentCourses = records.stream()
                .filter(r -> r.getCourseId() != null)
                .map(LearningRecord::getCourseId)
                .collect(Collectors.toSet());
        stats.put("currentCourses", currentCourses.size());
        
        // 今日学习时长
        LocalDate today = LocalDate.now();
        int todayDuration = records.stream()
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().toLocalDate().equals(today))
                .mapToInt(r -> r.getDuration() != null ? r.getDuration() : 0)
                .sum();
        stats.put("todayDuration", todayDuration);
        
        return stats;
    }


    /** 查询学习记录 */
    @Override
    public List<DailyLearningStatVO> getLearningTrend(Long userId, int days) {
        List<DailyLearningStatVO> trend = new ArrayList<>();
        if (days <= 0) days = 7;
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days - 1);

        // 优化：仅一次范围查询拉取整个窗口的记录，再按日期分组（避免按天循环 N+1 单查）
        LambdaQueryWrapper<LearningRecord> queryWrapper = new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .ge(LearningRecord::getCreatedAt, startDate.atStartOfDay())
                .lt(LearningRecord::getCreatedAt, today.plusDays(1).atStartOfDay());

        Map<LocalDate, List<LearningRecord>> groupedByDate = this.list(queryWrapper).stream()
                .filter(r -> r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(r -> r.getCreatedAt().toLocalDate()));

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);

            List<LearningRecord> dayRecords = groupedByDate.getOrDefault(date, Collections.emptyList());
            int duration = dayRecords.stream()
                    .mapToInt(r -> r.getDuration() != null ? r.getDuration() : 0)
                    .sum();

            trend.add(new DailyLearningStatVO(date.toString(), duration, dayRecords.size()));
        }

        return trend;
    }


    /** 查询学习记录 */
    @Override
    public List<DailyLearningStatVO> getMonthlyLearningData(Long userId, int year, int month) {
        List<DailyLearningStatVO> monthlyData = new ArrayList<>();
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);
        
        LambdaQueryWrapper<LearningRecord> queryWrapper = new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .ge(LearningRecord::getCreatedAt, startDate.atStartOfDay())
                .lt(LearningRecord::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        
        List<LearningRecord> records = this.list(queryWrapper);
        
        // 按日期分组
        Map<LocalDate, List<LearningRecord>> groupedByDate = records.stream()
                .filter(r -> r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(r -> r.getCreatedAt().toLocalDate()));
        
        // 填充整月数据
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            List<LearningRecord> dayRecords = groupedByDate.getOrDefault(current, Collections.emptyList());
            int duration = dayRecords.stream()
                    .mapToInt(r -> r.getDuration() != null ? r.getDuration() : 0)
                    .sum();

            monthlyData.add(new DailyLearningStatVO(current.toString(), duration, dayRecords.size()));
            current = current.plusDays(1);
        }
        
        return monthlyData;
    }

    /** V7 批：单条 GROUP BY 的按日活跃聚合（取代 14 天 × 1 条串行 list 的驾驶舱趋势查询）。 */
    @Override
    public java.util.List<java.util.Map<String, Object>> dailyActiveByCourse(Long courseId, java.time.LocalDateTime since) {
        return baseMapper.selectDailyActiveByCourse(courseId, since);
    }
}
