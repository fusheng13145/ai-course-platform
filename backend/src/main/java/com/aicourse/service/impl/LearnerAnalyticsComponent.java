package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.constant.SubmissionStatus;

import com.aicourse.entity.*;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习者（学生）视角数据分析协作者
 * <p>承接原 {@link AnalyticsServiceImpl} 中学生视角的统计职责：个人仪表盘、学习趋势、
 * 学习热力图及其相关私有 helper（连续学习天数、累计时长、热力等级）。</p>
 */
@Component
public class LearnerAnalyticsComponent {

    private static final Logger log = LoggerFactory.getLogger(LearnerAnalyticsComponent.class);

    private final CourseEnrollmentService enrollmentService;
    private final LearningRecordService learningRecordService;
    private final TaskService taskService;
    private final SubmissionService submissionService;

    public LearnerAnalyticsComponent(
            CourseEnrollmentService enrollmentService,
            LearningRecordService learningRecordService,
            TaskService taskService,
            SubmissionService submissionService
    ) {
        this.enrollmentService = enrollmentService;
        this.learningRecordService = learningRecordService;
        this.taskService = taskService;
        this.submissionService = submissionService;
    }


    /** 查询学习者分析 */
    public Map<String, Object> getUserDashboard(Long userId) {
        Map<String, Object> userData = new HashMap<>(12);
        try {
            // ---- 已选课程数量（Student statCards[0]）----
            long enrolledCount = enrollmentService.count(
                    new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getStudentId, userId)
            );
            userData.put("enrolledCourses", enrolledCount);
            userData.put("courseCount", enrolledCount);

            // ---- 平均学习进度 ----
            List<CourseEnrollment> enrollmentList = enrollmentService.list(
                    new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getStudentId, userId)
            );
            double avgProgress = enrollmentList.stream()
                    .mapToDouble(e -> e.getProgress() != null ? e.getProgress().doubleValue() : 0D)
                    .average()
                    .orElse(0D);
            userData.put("averageProgress", Math.round(avgProgress * 100.0) / 100.0);

            // ---- 累计学习时长（分钟）---- 优化：数据库层 SUM，避免全量拉取学习记录再内存求和
            int totalStudyTime = sumLearningDuration(userId);
            userData.put("totalStudyTime", totalStudyTime);

            // ---- 平均成绩（Student statCards[1]，从选课记录的 finalScore 计算）----
            double avgScore = enrollmentList.stream()
                    .filter(e -> e.getFinalScore() != null)
                    .mapToDouble(e -> e.getFinalScore().doubleValue())
                    .average()
                    .orElse(0D);
            userData.put("averageScore", Math.round(avgScore * 100.0) / 100.0);
            userData.put("avgScore", Math.round(avgScore));

            // ---- 连续学习天数（Student statCards[2]）----
            Set<LocalDate> studyDates = learningRecordService.list(
                    new LambdaQueryWrapper<LearningRecord>()
                            .eq(LearningRecord::getUserId, userId)
                            .orderByDesc(LearningRecord::getCreatedAt)
                            .last("LIMIT 90")
            ).stream()
                    .map(r -> r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate() : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            int streakDays = calculateStreakDays(studyDates);
            userData.put("streakDays", streakDays);
            userData.put("consecutiveDays", streakDays);

            // ---- 待办/未完成任务数（Student statCards[3]）----
            List<Long> courseIds = enrollmentList.stream()
                    .map(CourseEnrollment::getCourseId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            long pendingTasks = 0L;
            if (!courseIds.isEmpty()) {
                pendingTasks = taskService.count(
                        new LambdaQueryWrapper<Task>()
                                .in(Task::getCourseId, courseIds)
                                .eq(Task::getIsPublished, Boolean.TRUE)
                                .and(w -> w.isNull(Task::getDueDate).or().ge(Task::getDueDate, LocalDate.now()))
                );
            }
            long submittedCount = submissionService.count(
                    new LambdaQueryWrapper<Submission>()
                            .eq(Submission::getStudentId, userId)
                            .in(Submission::getStatus, SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED)
            );
            userData.put("pendingTasks", Math.max(0, pendingTasks - submittedCount));
            userData.put("todoCount", Math.max(0, pendingTasks - submittedCount));
        } catch (Exception e) {
            log.error("查询学习者分析失败: {}", e.getMessage(), e);

            userData.put("error", "学生数据加载异常: " + e.getMessage());
            userData.putIfAbsent("enrolledCourses", 0L);
            userData.putIfAbsent("courseCount", 0L);
            userData.putIfAbsent("averageScore", 0D);
            userData.putIfAbsent("avgScore", 0);
            userData.putIfAbsent("streakDays", 0);
            userData.putIfAbsent("pendingTasks", 0L);
            userData.putIfAbsent("todoCount", 0L);
        }
        return userData;
    }

    /**
     * 计算连续学习天数（从今天往前推算）
     */
    private int calculateStreakDays(Set<LocalDate> studyDates) {
        if (studyDates == null || studyDates.isEmpty()) return 0;
        LocalDate today = LocalDate.now();
        int streak = 0;
        LocalDate checkDay = today;
        // 允许今天还没学习的情况，从昨天开始检查
        if (!studyDates.contains(today)) {
            checkDay = today.minusDays(1);
        }
        while (studyDates.contains(checkDay)) {
            streak++;
            checkDay = checkDay.minusDays(1);
            // 防止无限循环，最多计算365天
            if (streak > 365) break;
        }
        return streak;
    }

    /**
     * 累计学习时长（分钟）：在数据库层用 SUM 聚合，避免拉取用户全部学习记录再内存求和（P2-1 性能治理）。
     */
    private int sumLearningDuration(Long userId) {
        try {
            QueryWrapper<LearningRecord> qw = new QueryWrapper<LearningRecord>()
                    .select("COALESCE(SUM(duration), 0)")
                    .eq("user_id", userId);
            List<Object> rows = learningRecordService.getBaseMapper().selectObjs(qw);
            if (rows != null && !rows.isEmpty() && rows.get(0) != null) {
                return ((Number) rows.get(0)).intValue();
            }
        } catch (Exception e) {
            log.error("查询学习者分析失败: {}", e.getMessage(), e);

            // 聚合失败时降级为 0，不影响整体仪表盘
        }
        return 0;
    }


    /** 查询学习者分析 */
    public List<Map<String, Object>> getLearningTrends(Long userId) {
        // 优化：限定最近 365 天窗口，避免无日期过滤的全量拉取
        List<LearningRecord> recordList = learningRecordService.list(
                new LambdaQueryWrapper<LearningRecord>()
                        .eq(LearningRecord::getUserId, userId)
                        .ge(LearningRecord::getCreatedAt, LocalDate.now().minusDays(365).atStartOfDay())
                        .orderByAsc(LearningRecord::getCreatedAt)
        );

        // 按日期分组并累加时长，有序Map保证日期顺序（过滤createdAt为null的记录）
        Map<String, Integer> dateDurationMap = recordList.stream()
                .filter(record -> record != null && record.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        record -> record.getCreatedAt().toLocalDate().toString(),
                        LinkedHashMap::new,
                        Collectors.summingInt(LearningRecord::getDuration)
                ));

        return dateDurationMap.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>(2);
                    item.put("date", entry.getKey());
                    item.put("duration", entry.getValue());
                    return item;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取学习热力图数据
     * 支持按日期范围筛选，返回每日学习时长用于前端热力图展示
     */
    public List<Map<String, Object>> getLearningHeatmap(Long userId, LocalDate startDate, LocalDate endDate) {
        // 构建查询条件
        LambdaQueryWrapper<LearningRecord> wrapper = new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId);

        // 添加日期范围筛选
        if (startDate != null) {
            wrapper.ge(LearningRecord::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(LearningRecord::getCreatedAt, endDate.atTime(LocalTime.MAX));
        }

        List<LearningRecord> recordList = learningRecordService.list(wrapper);

        // 按日期分组统计学习时长（过滤createdAt为null的记录）
        Map<String, Integer> heatmapData = recordList.stream()
                .filter(record -> record != null && record.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        record -> record.getCreatedAt().toLocalDate().toString(),
                        LinkedHashMap::new,
                        Collectors.summingInt(LearningRecord::getDuration)
                ));

        // 转换为列表格式
        return heatmapData.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>(3);
                    item.put("date", entry.getKey());
                    item.put("duration", entry.getValue());
                    // 根据学习时长设置强度等级（1-5级）
                    int level = calculateHeatLevel(entry.getValue());
                    item.put("level", level);
                    return item;
                })
                .collect(Collectors.toList());
    }

    /**
     * 计算热力等级（基于学习时长）
     */
    private int calculateHeatLevel(int durationMinutes) {
        if (durationMinutes == 0) return 0;
        if (durationMinutes < 30) return 1;      // <30分钟
        if (durationMinutes < 60) return 2;      // 30-60分钟
        if (durationMinutes < 120) return 3;     // 1-2小时
        if (durationMinutes < 180) return 4;     // 2-3小时
        return 5;                                 // >3小时
    }
}
