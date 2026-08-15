package com.aicourse.service;


import com.aicourse.mapper.MetricsMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务指标采集服务。
 * <p>通过 {@code @Scheduled} 周期性（默认 60s）从 {@link MetricsMapper} 聚合统计，刷新一组 Micrometer
 * Gauge，使 Grafana 能基于真实业务数据绘制趋势与统计卡。Gauge 在 {@code @PostConstruct} 阶段一次性注册，
 * 后续仅需更新底层 {@link AtomicDouble} 的值，避免重复注册导致 Meter 泄漏。</p>
 *
 * <p>指标命名遵循 Prometheus 约定：Gauge 不使用 {@code _total} 后缀（该后缀保留给 Counter）；
 * 高频事件类指标交由对应服务的 Counter（如 AI 调用、作业提交）承载。</p>
 */
@Service
@Slf4j
public class BusinessMetricsService {


    /** 采集周期：60s。业务总量变化频率低，60s 足够捕捉趋势且不给数据库造成压力。 */
    private static final long COLLECT_INTERVAL_MS = 60_000L;

    private final MeterRegistry meterRegistry;
    private final MetricsMapper metricsMapper;

    /** 指标名 → 当前值容器（Gauge 绑定到 AtomicLong 的 doubleValue） */
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    public BusinessMetricsService(MeterRegistry meterRegistry, MetricsMapper metricsMapper) {
        this.meterRegistry = meterRegistry;
        this.metricsMapper = metricsMapper;
        register("aicourse_courses", "进行中且已审批通过的课程数");
        register("aicourse_enrollments", "已通过审批的选课记录数");
        register("aicourse_active_students", "已激活的学生数");
        register("aicourse_teachers", "教师数");
        register("aicourse_submissions", "作业提交总数");
        register("aicourse_pending_submissions", "待批改作业数（status=SUBMITTED）");
        register("aicourse_exams", "试卷总数");
        register("aicourse_questions", "题库题目总数");
        register("aicourse_wrong_questions", "错题本条目总数");
        register("aicourse_learning_records", "学习记录总数");
        register("aicourse_discussions", "讨论区帖子总数");
    }

    private void register(String name, String description) {
        AtomicLong value = new AtomicLong(0L);
        gauges.put(name, value);
        Gauge.builder(name, value, AtomicLong::doubleValue)
                .description(description)
                .register(meterRegistry);
    }

    /**
     * 首次启动即采集一次，避免仪表盘在采集周期到来前长期显示 0。
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        collect();
    }

    /**
     * 周期性聚合刷新全部业务 Gauge。单条查询失败不影响其余指标；异常被捕获并记录，
     * 保证定时任务不会因为某次 DB 抖动而中断后续采集。
     */
    @Scheduled(fixedDelay = COLLECT_INTERVAL_MS)
    public void collect() {
        safeSet("aicourse_courses", metricsMapper::countActiveCourses);
        safeSet("aicourse_enrollments", metricsMapper::countApprovedEnrollments);
        safeSet("aicourse_active_students", metricsMapper::countActiveStudents);
        safeSet("aicourse_teachers", metricsMapper::countTeachers);
        safeSet("aicourse_submissions", metricsMapper::countSubmissions);
        safeSet("aicourse_pending_submissions", metricsMapper::countPendingSubmissions);
        safeSet("aicourse_exams", metricsMapper::countExams);
        safeSet("aicourse_questions", metricsMapper::countQuestions);
        safeSet("aicourse_wrong_questions", metricsMapper::countWrongQuestions);
        safeSet("aicourse_learning_records", metricsMapper::countLearningRecords);
        safeSet("aicourse_discussions", metricsMapper::countDiscussionPosts);
    }

    private void safeSet(String name, java.util.function.Supplier<Long> query) {
        try {
            long v = query.get();
            AtomicLong holder = gauges.get(name);
            if (holder != null) {
                holder.set(v);
            }
        } catch (Exception e) {
            log.warn("业务指标采集失败，指标={}，原因={}", name, e.getMessage());
        }
    }
}
