package com.aicourse.service.impl;

import com.aicourse.entity.VideoLearningRecord;
import com.aicourse.mapper.VideoLearningRecordMapper;
import com.aicourse.service.VideoLearningRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 视频学习记录服务实现类
 * 实现视频播放进度跟踪、完播检测、观看历史统计等核心业务逻辑
 */
@Service
public class VideoLearningRecordServiceImpl extends ServiceImpl<VideoLearningRecordMapper, VideoLearningRecord> implements VideoLearningRecordService {

    /**
     * 更新视频学习进度
     * 接收播放位置、总时长、播放速度等信息，自动检测完播并更新记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoLearningRecord updateProgress(Long userId, Long resourceId, Integer position,
                                               Integer totalDuration, Double playbackSpeed) {
        // 查询是否已有该用户+资源的记录
        LambdaQueryWrapper<VideoLearningRecord> queryWrapper = new LambdaQueryWrapper<VideoLearningRecord>()
                .eq(VideoLearningRecord::getUserId, userId)
                .eq(VideoLearningRecord::getResourceId, resourceId);
        VideoLearningRecord record = this.getOne(queryWrapper);

        if (record == null) {
            // 首次观看，创建新记录
            record = new VideoLearningRecord();
            record.setUserId(userId);
            record.setResourceId(resourceId);
            record.setWatchDuration(position != null ? position : 0);
            record.setTotalDuration(totalDuration);
            record.setLastPosition(position != null ? position : 0);
            record.setCompleteCount(0);
            record.setProgress(calculateProgressPercent(position, totalDuration));
            record.setLastWatchedAt(LocalDateTime.now());
            this.save(record);
        } else {
            // 更新已有记录
            int oldPosition = record.getLastPosition() != null ? record.getLastPosition() : 0;

            // 更新最后播放位置
            record.setLastPosition(position != null ? position : 0);

            // 记录视频总时长
            if (totalDuration != null && totalDuration > 0) {
                record.setTotalDuration(totalDuration);
            }

            // 累计观看时长（增量计算：本次位置 - 上次位置的差值，最小为0）
            int incrementalDuration = Math.max(0, (position != null ? position : 0) - oldPosition);
            int currentWatchDuration = record.getWatchDuration() != null ? record.getWatchDuration() : 0;
            record.setWatchDuration(currentWatchDuration + incrementalDuration);

            // 完播检测：播放位置达到总时长的95%以上，且上次未完成过
            if (totalDuration != null && totalDuration > 0 && position != null) {
                double progressRatio = (double) position / totalDuration;
                if (progressRatio >= 0.95) {
                    // 检查是否是新的一次完播（避免同一位置重复计数）
                    if (oldPosition < (int) (totalDuration * 0.9)) {
                        int currentCompleteCount = record.getCompleteCount() != null ? record.getCompleteCount() : 0;
                        record.setCompleteCount(currentCompleteCount + 1);
                    }
                }
            }

            // 更新进度百分比
            record.setProgress(calculateProgressPercent(position, totalDuration));
            record.setLastWatchedAt(LocalDateTime.now());

            this.updateById(record);
        }

        return record;
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
        // 查询该用户的所有视频学习记录
        LambdaQueryWrapper<VideoLearningRecord> baseQuery = new LambdaQueryWrapper<VideoLearningRecord>()
                .eq(VideoLearningRecord::getUserId, userId);

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
