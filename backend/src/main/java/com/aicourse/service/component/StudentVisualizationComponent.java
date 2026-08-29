package com.aicourse.service.component;

import com.aicourse.entity.Course;
import com.aicourse.entity.LearningRecord;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.service.CourseService;
import com.aicourse.service.LearningRecordService;
import com.aicourse.service.WrongQuestionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生学情可视化协作者：近 14 天学习时长趋势、课程薄弱度雷达、课程 × 7 天活跃热力图。
 * <p>由 {@code StudentLearningServiceImpl} 门面委托调用。三个子聚合各自独立兜底。</p>
 */
@Component
public class StudentVisualizationComponent {

    private static final Logger log = LoggerFactory.getLogger(StudentVisualizationComponent.class);

    private final LearningRecordService learningRecordService;
    private final WrongQuestionService wrongQuestionService;
    private final CourseService courseService;

    public StudentVisualizationComponent(LearningRecordService learningRecordService,
                                         WrongQuestionService wrongQuestionService,
                                         CourseService courseService) {
        this.learningRecordService = learningRecordService;
        this.wrongQuestionService = wrongQuestionService;
        this.courseService = courseService;
    }

    /** 学生端学情可视化数据（D7 增强）：三子聚合各自独立兜底 */
    public Map<String, Object> getVisualizationData(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        result.put("durationTrend", buildDurationTrend(userId, today));
        result.put("radarData", buildRadarData(userId));
        result.put("heatmapData", buildHeatmapData(userId, today));
        return result;
    }

    private List<Map<String, Object>> buildDurationTrend(Long userId, LocalDate today) {
        try {
            LocalDateTime since = today.minusDays(13).atStartOfDay();
            List<LearningRecord> records = learningRecordService.list(
                    new LambdaQueryWrapper<LearningRecord>()
                            .eq(LearningRecord::getUserId, userId)
                            .ge(LearningRecord::getCreatedAt, since));
            Map<LocalDate, Long> secByDate = new HashMap<>();
            for (LearningRecord r : records) {
                if (r.getCreatedAt() == null) {
                    continue;
                }
                LocalDate d = r.getCreatedAt().toLocalDate();
                secByDate.merge(d, r.getDuration() != null ? (long) r.getDuration() : 0L, Long::sum);
            }
            List<Map<String, Object>> trend = new ArrayList<>();
            for (int i = 13; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                Map<String, Object> item = new HashMap<>();
                item.put("date", d.toString());
                item.put("seconds", secByDate.getOrDefault(d, 0L));
                trend.add(item);
            }
            return trend;
        } catch (Exception e) {
            log.warn("学情可视化-时长趋势聚合失败: userId={}, err={}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> buildRadarData(Long userId) {
        try {
            List<WrongQuestion> wrongs = wrongQuestionService.list(
                    new LambdaQueryWrapper<WrongQuestion>().eq(WrongQuestion::getStudentId, userId));
            // courseId -> [总错题数, 未掌握数]
            Map<Long, long[]> byCourse = new HashMap<>();
            Map<Long, String> courseNames = new HashMap<>();
            for (WrongQuestion w : wrongs) {
                if (w.getCourseId() == null) {
                    continue;
                }
                long[] arr = byCourse.computeIfAbsent(w.getCourseId(), k -> new long[2]);
                arr[0]++;
                if (w.getMastered() == null || !w.getMastered()) {
                    arr[1]++;
                }
                if (!courseNames.containsKey(w.getCourseId())) {
                    Course c = courseService.getById(w.getCourseId());
                    courseNames.put(w.getCourseId(), c != null ? c.getName() : ("课程" + w.getCourseId()));
                }
            }
            List<Map<String, Object>> radar = new ArrayList<>();
            for (Map.Entry<Long, long[]> e : byCourse.entrySet()) {
                if (e.getValue()[0] == 0) {
                    continue;
                }
                Map<String, Object> item = new HashMap<>();
                item.put("dimension", courseNames.getOrDefault(e.getKey(), "未命名课程"));
                // 薄弱率 = 未掌握错题 / 总错题（保留两位小数）
                double weakRate = Math.round(e.getValue()[1] * 10000.0 / e.getValue()[0]) / 100.0;
                item.put("weakRate", weakRate);
                radar.add(item);
            }
            radar.sort((a, b) -> Double.compare((double) b.get("weakRate"), (double) a.get("weakRate")));
            return radar.size() > 6 ? new ArrayList<>(radar.subList(0, 6)) : radar;
        } catch (Exception e) {
            log.warn("学情可视化-薄弱度雷达聚合失败: userId={}, err={}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> buildHeatmapData(Long userId, LocalDate today) {
        try {
            LocalDateTime since7 = today.minusDays(6).atStartOfDay();
            List<LearningRecord> week = learningRecordService.list(
                    new LambdaQueryWrapper<LearningRecord>()
                            .eq(LearningRecord::getUserId, userId)
                            .ge(LearningRecord::getCreatedAt, since7));
            // "courseId|date" -> seconds
            Map<String, Long> heat = new HashMap<>();
            for (LearningRecord r : week) {
                if (r.getCourseId() == null || r.getCreatedAt() == null) {
                    continue;
                }
                heat.merge(r.getCourseId() + "|" + r.getCreatedAt().toLocalDate(),
                        r.getDuration() != null ? (long) r.getDuration() : 0L, Long::sum);
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (Map.Entry<String, Long> e : heat.entrySet()) {
                String[] parts = e.getKey().split("\\|");
                Map<String, Object> item = new HashMap<>();
                item.put("courseId", Long.parseLong(parts[0]));
                item.put("date", parts[1]);
                item.put("seconds", e.getValue());
                list.add(item);
            }
            return list;
        } catch (Exception e) {
            log.warn("学情可视化-热力图聚合失败: userId={}, err={}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }
}
