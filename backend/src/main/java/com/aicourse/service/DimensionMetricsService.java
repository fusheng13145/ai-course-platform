package com.aicourse.service;


import com.aicourse.mapper.MetricsMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 业务指标「维度下钻」采集服务。
 * <p>在 {@link BusinessMetricsService} 的全局聚合 Gauge 之外，补齐按业务维度的可下钻指标：
 * 课程维度（选课/提交/待批改/题目/试卷）与班级维度（人数/选课/提交/待批改）。每条维度序列以
 * 课程编号/班级编号为主标识打 tag，并附加名称 tag 便于 Grafana 展示。</p>
 *
 * <p>维度集合（课程/班级）规模有限但会随业务变化（新课程上线、旧课程下线）。因此使用
 * {@link GaugeDimension} 通用管理器：首次出现时懒注册 Gauge，后续仅更新底层 {@link AtomicLong}，
 * 对已消失的维度主动 {@code meterRegistry.remove} 以防止 Meter 泄漏与 Prometheus 指标僵尸。</p>
 */
@Service
@Slf4j
public class DimensionMetricsService {


    /** 采集周期：60s（与全局业务指标一致，业务总量变化频率低）。 */
    private static final long COLLECT_INTERVAL_MS = 60_000L;

    private final MeterRegistry meterRegistry;
    private final MetricsMapper metricsMapper;

    // ===================== 课程维度（tag: course=课程编号, course_name=课程名）=====================
    private final GaugeDimension courseEnrollments = new GaugeDimension("aicourse_course_enrollments", "每门课程已审批选课人数");
    private final GaugeDimension courseSubmissions = new GaugeDimension("aicourse_course_submissions", "每门课程作业提交总数");
    private final GaugeDimension coursePending = new GaugeDimension("aicourse_course_pending_submissions", "每门课程待批改作业数");
    private final GaugeDimension courseQuestions = new GaugeDimension("aicourse_course_questions", "每门课程题库题目数");
    private final GaugeDimension courseExams = new GaugeDimension("aicourse_course_exams", "每门课程试卷数");

    // ===================== 班级维度（tag: class=班级编号, class_name=班级名）=====================
    private final GaugeDimension classStudents = new GaugeDimension("aicourse_classroom_students", "每个班级学生总数");
    private final GaugeDimension classEnrollments = new GaugeDimension("aicourse_classroom_enrollments", "每个班级已审批选课人次");
    private final GaugeDimension classSubmissions = new GaugeDimension("aicourse_classroom_submissions", "每个班级作业提交总数");
    private final GaugeDimension classPending = new GaugeDimension("aicourse_classroom_pending_submissions", "每个班级待批改作业数");

    public DimensionMetricsService(MeterRegistry meterRegistry, MetricsMapper metricsMapper) {
        this.meterRegistry = meterRegistry;
        this.metricsMapper = metricsMapper;
    }

    /** 首次启动即采集一次，避免仪表盘在采集周期到来前长期缺失维度数据。 */
    @jakarta.annotation.PostConstruct
    public void init() {
        collect();
    }

    /**
     * 周期性刷新全部维度 Gauge。单条查询失败不影响其余指标采集。
     */
    @Scheduled(fixedDelay = COLLECT_INTERVAL_MS)
    public void collect() {
        safeRefresh(courseEnrollments, metricsMapper::courseEnrollments);
        safeRefresh(courseSubmissions, metricsMapper::courseSubmissions);
        safeRefresh(coursePending, metricsMapper::coursePendingSubmissions);
        safeRefresh(courseQuestions, metricsMapper::courseQuestions);
        safeRefresh(courseExams, metricsMapper::courseExams);
        safeRefresh(classStudents, metricsMapper::classroomStudents);
        safeRefresh(classEnrollments, metricsMapper::classroomEnrollments);
        safeRefresh(classSubmissions, metricsMapper::classroomSubmissions);
        safeRefresh(classPending, metricsMapper::classroomPendingSubmissions);
    }

    private void safeRefresh(GaugeDimension dim, Supplier<List<Map<String, Object>>> query) {
        try {
            dim.refresh(query.get());
        } catch (Exception e) {
            log.warn("维度指标采集失败，指标={}，原因={}", dim.name, e.getMessage());
        }
    }

    /**
     * 动态带 tag 的 Gauge 集合管理器。
     * <p>每行查询结果按 tag 列（除 {@code val} 外）构成维度实例，以所有 tag 值拼接作为稳定标识；
     * 懒注册、刷新值，并移除当前结果集中已不存在的维度，避免 Meter 无限增长。</p>
     */
    private class GaugeDimension {
        final String name;
        final String description;
        final Map<String, AtomicLong> values = new ConcurrentHashMap<>();
        final Map<String, Gauge> meters = new ConcurrentHashMap<>();

        GaugeDimension(String name, String description) {
            this.name = name;
            this.description = description;
        }

        void refresh(List<Map<String, Object>> rows) {
            Set<String> seen = new HashSet<>();
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Long val = toLong(row.get("val"));
                    Map<String, String> tags = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> e : row.entrySet()) {
                        if ("val".equals(e.getKey()) || e.getValue() == null) {
                            continue;
                        }
                        tags.put(e.getKey(), String.valueOf(e.getValue()));
                    }
                    if (tags.isEmpty()) {
                        continue;
                    }
                    String identity = tags.values().toString();
                    AtomicLong holder = values.computeIfAbsent(identity, k -> {
                        AtomicLong a = new AtomicLong(0L);
                        var b = Gauge.builder(name, a, AtomicLong::doubleValue)
                                .description(description);
                        tags.forEach(b::tag);
                        Gauge g = b.register(meterRegistry);
                        meters.put(identity, g);
                        return a;
                    });
                    holder.set(val);
                    seen.add(identity);
                }
            }
            // 移除当前结果集中已不存在的维度，回收 Meter
            Iterator<Map.Entry<String, Gauge>> it = meters.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Gauge> e = it.next();
                if (!seen.contains(e.getKey())) {
                    meterRegistry.remove(e.getValue());
                    it.remove();
                    values.remove(e.getKey());
                }
            }
        }
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            log.error("维度指标相关操作失败: {}", e.getMessage(), e);

            return 0L;
        }
    }
}
