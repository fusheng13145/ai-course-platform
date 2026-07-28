package com.aicourse.service.impl;

import com.aicourse.entity.Resource;
import com.aicourse.entity.VideoLearningRecord;
import com.aicourse.mapper.VideoLearningRecordMapper;
import com.aicourse.service.ResourceService;
import com.aicourse.service.VideoLearningRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 视频学习记录服务实现类
 * 实现视频播放进度跟踪、完播检测、观看历史统计等核心业务逻辑
 */
@Service
public class VideoLearningRecordServiceImpl extends ServiceImpl<VideoLearningRecordMapper, VideoLearningRecord> implements VideoLearningRecordService {

    /**
     * 防拖拽容差：允许的"位置前进量"相对"真实经过时长"的额外秒数，避免正常播放的边界抖动被误判为作弊
     */
    private static final long SEEK_TOLERANCE_SEC = 3L;

    private final ResourceService resourceService;

    public VideoLearningRecordServiceImpl(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /**
     * 更新视频学习进度
     * 接收播放位置、总时长、播放速度等信息，自动检测完播并更新记录
     * 修复 P0-5：引入"有效观看时长"与"防拖拽"——累计时长不再等于位置差，而是受真实经过时长约束，
     *            完播判定基于有效观看时长 >= 90% 内容时长，而非仅看末位置（防止拖到 95% 即算完播）。
     * 修复 P0-6 的数据基础：写入由 resource 解析出的 courseId，使按课程统计生效。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoLearningRecord updateProgress(Long userId, Long resourceId, Integer position,
                                               Integer totalDuration, Double playbackSpeed) {
        Long courseId = resolveCourseId(resourceId);

        // 查询是否已有该用户+资源的记录
        LambdaQueryWrapper<VideoLearningRecord> queryWrapper = new LambdaQueryWrapper<VideoLearningRecord>()
                .eq(VideoLearningRecord::getUserId, userId)
                .eq(VideoLearningRecord::getResourceId, resourceId);
        VideoLearningRecord record = this.getOne(queryWrapper);

        int currentPosition = position != null ? position : 0;

        if (record == null) {
            // 首次观看，创建新记录
            record = new VideoLearningRecord();
            record.setUserId(userId);
            record.setResourceId(resourceId);
            record.setCourseId(courseId);
            record.setTotalDuration(totalDuration);
            record.setLastPosition(currentPosition);
            record.setWatchDuration(0);
            record.setCompleteCount(0);
            record.setProgress(calculateProgressPercent(currentPosition, totalDuration));
            record.setLastWatchedAt(LocalDateTime.now());
            this.save(record);
        } else {
            int oldPosition = record.getLastPosition() != null ? record.getLastPosition() : 0;

            // 本次真实经过时长（以服务端时间戳为准，防客户端伪造）
            LocalDateTime prevUpdate = record.getUpdatedAt() != null ? record.getUpdatedAt() : record.getCreatedAt();
            long realElapsedSec = prevUpdate != null
                    ? Math.max(0, ChronoUnit.SECONDS.between(prevUpdate, LocalDateTime.now()))
                    : 0;

            // 防拖拽/防快进：有效增量 = min(位置前进量, 真实经过时长 + 容差)，回退/快进不计入
            int posDelta = currentPosition - oldPosition;
            int validIncrement;
            if (posDelta <= 0) {
                validIncrement = 0; // 回退或暂停不动：不计观看时长
            } else {
                validIncrement = (int) Math.min(posDelta, realElapsedSec + SEEK_TOLERANCE_SEC);
                if (validIncrement < 0) validIncrement = 0;
            }

            int currentWatchDuration = record.getWatchDuration() != null ? record.getWatchDuration() : 0;
            int newWatchDuration = currentWatchDuration + validIncrement;

            record.setCourseId(courseId);
            record.setLastPosition(currentPosition);
            if (totalDuration != null && totalDuration > 0) {
                record.setTotalDuration(totalDuration);
            }
            record.setWatchDuration(newWatchDuration);

            // 完播检测（修复 P0-5）：基于"有效观看时长 >= 90% 内容时长"，每次跨过阈值计 1 次完播
            if (totalDuration != null && totalDuration > 0) {
                int threshold = (int) (totalDuration * 0.9);
                boolean wasComplete = currentWatchDuration >= threshold;
                boolean nowComplete = newWatchDuration >= threshold;
                if (nowComplete && !wasComplete) {
                    int currentCompleteCount = record.getCompleteCount() != null ? record.getCompleteCount() : 0;
                    record.setCompleteCount(currentCompleteCount + 1);
                }
            }

            // 更新进度百分比
            record.setProgress(calculateProgressPercent(currentPosition, totalDuration));
            record.setLastWatchedAt(LocalDateTime.now());

            this.updateById(record);
        }

        return record;
    }

    /**
     * 由 resourceId 解析课程ID（用于按课程统计视频完播，修复 P0-6 数据基础）
     */
    private Long resolveCourseId(Long resourceId) {
        if (resourceId == null) return null;
        try {
            Resource resource = resourceService.getById(resourceId);
            return resource != null ? resource.getCourseId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取用户对某视频的学习进度详情
     */
    @Override
    public Map<String, Object> getProgress(Long userId, Long resourceId) {
        LambdaQueryWrapper<VideoLearningRecord> queryWrapper = new LambdaQueryWrapper<VideoLearningRecord>()
                .eq(VideoLearningRecord::getUserId, userId)
                .eq(VideoLearningRecord::getResourceId, resourceId);
        VideoLearningRecord record = this.getOne(queryWrapper);

        Map<String, Object> progress = new HashMap<>();
        progress.put("userId", userId);
        progress.put("resourceId", resourceId);

        if (record == null) {
            progress.put("hasRecord", false);
            progress.put("lastPosition", 0);
            progress.put("completeCount", 0);
            progress.put("totalDuration", 0);
            progress.put("progressPercent", BigDecimal.ZERO);
            progress.put("watchDuration", 0);
            return progress;
        }

        progress.put("hasRecord", true);
        progress.put("lastPosition", record.getLastPosition() != null ? record.getLastPosition() : 0);
        progress.put("completeCount", record.getCompleteCount() != null ? record.getCompleteCount() : 0);
        progress.put("totalDuration", record.getTotalDuration() != null ? record.getTotalDuration() : 0);
        progress.put("progressPercent", record.getProgress() != null ? record.getProgress() : BigDecimal.ZERO);
        progress.put("watchDuration", record.getWatchDuration() != null ? record.getWatchDuration() : 0);
        progress.put("lastWatchedAt", record.getLastWatchedAt());

        // 计算完播状态
        boolean isCompleted = record.getCompleteCount() != null && record.getCompleteCount() > 0;
        progress.put("isCompleted", isCompleted);

        return progress;
    }

    /**
     * 获取用户的观看历史记录（按日期聚合）
     */
    @Override
    public List<Map<String, Object>> getWatchHistory(Long userId, int days) {
        // 查询最近N天的所有学习记录
        LocalDateTime startTime = LocalDate.now().minusDays(days).atStartOfDay();
        LambdaQueryWrapper<VideoLearningRecord> queryWrapper = new LambdaQueryWrapper<VideoLearningRecord>()
                .eq(VideoLearningRecord::getUserId, userId)
                .ge(VideoLearningRecord::getLastWatchedAt, startTime)
                .orderByDesc(VideoLearningRecord::getLastWatchedAt);

        List<VideoLearningRecord> records = this.list(queryWrapper);

        // 按日期分组聚合
        Map<LocalDate, List<VideoLearningRecord>> recordsByDate = records.stream()
                .collect(Collectors.groupingBy(r -> r.getLastWatchedAt().toLocalDate()));

        List<Map<String, Object>> history = new ArrayList<>();
        for (Map.Entry<LocalDate, List<VideoLearningRecord>> entry : recordsByDate.entrySet()) {
            Map<String, Object> dayData = new LinkedHashMap<>();
            dayData.put("date", entry.getKey().toString());

            List<VideoLearningRecord> dayRecords = entry.getValue();

            // 当日观看视频数
            dayData.put("videoCount", dayRecords.size());

            // 当日累计观看时长（秒）
            int totalWatchDuration = dayRecords.stream()
                    .filter(r -> r.getWatchDuration() != null)
                    .mapToInt(VideoLearningRecord::getWatchDuration)
                    .sum();
            dayData.put("totalWatchDuration", totalWatchDuration);

            // 当日完播数
            long completedCount = dayRecords.stream()
                    .filter(r -> r.getCompleteCount() != null && r.getCompleteCount() > 0)
                    .count();
            dayData.put("completedCount", completedCount);

            // 观看的视频列表
            dayData.put("videos", dayRecords.stream().map(r -> {
                Map<String, Object> videoInfo = new HashMap<>();
                videoInfo.put("resourceId", r.getResourceId());
                videoInfo.put("lastPosition", r.getLastPosition());
                videoInfo.put("progress", r.getProgress());
                videoInfo.put("completeCount", r.getCompleteCount());
                videoInfo.put("watchedAt", r.getLastWatchedAt());
                return videoInfo;
            }).collect(Collectors.toList()));

            history.add(dayData);
        }

        // 按日期倒序排列
        history.sort((a, b) -> ((String) a.get("date")).compareTo((String) b.get("date")));
        Collections.reverse(history);

        return history;
    }

    /**
     * 获取用户视频学习完播统计
     */
    @Override
    public Map<String, Object> getCompletionStats(Long userId, Long courseId) {
        // 查询该用户的视频学习记录；修复 P0-6：courseId 非 null 时按课程过滤，否则跨全部课程
        LambdaQueryWrapper<VideoLearningRecord> baseQuery = new LambdaQueryWrapper<VideoLearningRecord>()
                .eq(VideoLearningRecord::getUserId, userId);
        if (courseId != null) {
            baseQuery.eq(VideoLearningRecord::getCourseId, courseId);
        }

        List<VideoLearningRecord> allRecords = this.list(baseQuery);

        Map<String, Object> stats = new HashMap<>();

        // 总视频数
        int totalVideos = allRecords.size();
        stats.put("totalVideos", totalVideos);

        // 已完播视频数（completeCount > 0）
        long completedVideos = allRecords.stream()
                .filter(r -> r.getCompleteCount() != null && r.getCompleteCount() > 0)
                .count();
        stats.put("completedVideos", completedVideos);

        // 完播率
        BigDecimal completionRate = totalVideos > 0
                ? BigDecimal.valueOf(completedVideos)
                        .divide(BigDecimal.valueOf(totalVideos), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        stats.put("completionRate", completionRate);

        // 累计观看时长（秒）
        int totalWatchDuration = allRecords.stream()
                .filter(r -> r.getWatchDuration() != null)
                .mapToInt(VideoLearningRecord::getWatchDuration)
                .sum();
        stats.put("totalWatchDuration", totalWatchDuration);
        stats.put("totalWatchDurationMinutes", Math.round(totalWatchDuration / 60.0));

        // 累计完播次数
        int totalCompletions = allRecords.stream()
                .filter(r -> r.getCompleteCount() != null)
                .mapToInt(VideoLearningRecord::getCompleteCount)
                .sum();
        stats.put("totalCompletions", totalCompletions);

        // 平均每视频完播次数
        double avgCompletionsPerVideo = totalVideos > 0 ? (double) totalCompletions / totalVideos : 0;
        stats.put("avgCompletionsPerVideo", Math.round(avgCompletionsPerVideo * 100.0) / 100.0);

        // 最近一次观看时间
        Optional<VideoLearningRecord> lastWatched = allRecords.stream()
                .max(Comparator.comparing(VideoLearningRecord::getLastWatchedAt));
        lastWatched.ifPresent(record -> stats.put("lastWatchedAt", record.getLastWatchedAt()));

        return stats;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 计算进度百分比（position / totalDuration * 100）
     */
    private BigDecimal calculateProgressPercent(Integer position, Integer totalDuration) {
        if (position == null || totalDuration == null || totalDuration <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        double percent = (double) position / totalDuration * 100;
        // 最大不超过100%
        if (percent > 100) percent = 100;
        return BigDecimal.valueOf(percent).setScale(2, RoundingMode.HALF_UP);
    }
}
