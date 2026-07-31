package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.service.AIService;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.LearningRecommendationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * 学习推荐服务实现类
 * 原逻辑位于 AIController#learningRecommendations，下沉至服务层：
 * 1) 查询学生的活跃选课；2) 构建课程/进度/薄弱点/成绩数据驱动上下文；
 * 3) 调用 LLM 生成个性化推荐语。任何单一数据源失败均降级，不影响整体推荐。
 */
@Service
@Slf4j
public class LearningRecommendationServiceImpl implements LearningRecommendationService {

    private static final BigDecimal WEAK_MASTERY_THRESHOLD = new BigDecimal("70");
    private static final int MAX_WEAK_POINTS = 8;

    private final CourseEnrollmentService courseEnrollmentService;
    private final CourseService courseService;
    private final KnowledgePointService knowledgePointService;
    private final AnalyticsService analyticsService;
    private final AIService aiService;

    public LearningRecommendationServiceImpl(CourseEnrollmentService courseEnrollmentService,
                                             CourseService courseService,
                                             KnowledgePointService knowledgePointService,
                                             AnalyticsService analyticsService,
                                             AIService aiService) {
        this.courseEnrollmentService = courseEnrollmentService;
        this.courseService = courseService;
        this.knowledgePointService = knowledgePointService;
        this.analyticsService = analyticsService;
        this.aiService = aiService;
    }

    @Override
    public Map<String, Object> recommendLearning(Long userId) {
        Map<String, Object> result = new HashMap<>();
        if (userId == null) {
            result.put("recommendation", "请先登录");
            return result;
        }

        List<CourseEnrollment> enrollments = courseEnrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getStudentId, userId)
                        .eq(CourseEnrollment::getIsActive, true)
        );
        Set<Long> courseIds = enrollments.stream()
                .map(CourseEnrollment::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (courseIds.isEmpty()) {
            result.put("recommendation", "您尚未加入任何课程，请先选课以获取个性化推荐。");
            return result;
        }

        // 构建数据驱动推荐上下文
        Map<String, Object> context = new LinkedHashMap<>();

        // 1) 课程列表
        Map<Long, String> nameMap = courseService.listByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getName, (a, b) -> a));
        String courseList = enrollments.stream()
                .map(e -> nameMap.getOrDefault(e.getCourseId(), ""))
                .filter(n -> !n.isEmpty()).collect(Collectors.joining("、"));
        context.put("courseList", courseList);

        // 2) 课程进度
        Double avgProgress = enrollments.stream()
                .map(CourseEnrollment::getProgress)
                .filter(Objects::nonNull)
                .mapToDouble(p -> p.doubleValue()).average().orElse(0);
        context.put("progress", String.format("%.0f", avgProgress));

        // 3) 薄弱知识点（按掌握度升序，取前N最弱项）
        try {
            List<Map<String, Object>> weakPoints = new ArrayList<>();
            for (Long cid : courseIds) {
                List<KnowledgePoint> kps = knowledgePointService.list(
                        new LambdaQueryWrapper<KnowledgePoint>()
                                .eq(KnowledgePoint::getCourseId, cid));
                for (KnowledgePoint kp : kps) {
                    BigDecimal mastery = knowledgePointService.calculateMastery(kp.getId(), userId);
                    if (mastery != null && mastery.compareTo(WEAK_MASTERY_THRESHOLD) < 0) {
                        Map<String, Object> wp = new LinkedHashMap<>();
                        wp.put("name", kp.getName());
                        wp.put("mastery", mastery);
                        weakPoints.add(wp);
                    }
                }
            }
            context.put("weakPoints", weakPoints);
        } catch (Exception e) {
            log.debug("获取薄弱知识点失败（不影响推荐）: {}", e.getMessage());
        }

        // 4) 近期成绩
        try {
            Map<String, Object> stats = analyticsService.getUserDashboard(userId);
            if (stats != null && stats.containsKey("avgScore")) {
                context.put("recentScores", "平均成绩：" + stats.get("avgScore"));
            }
        } catch (Exception e) {
            log.warn("获取用户成绩统计失败，推荐降级为纯LLM模式: userId={}", userId, e);
        }

        String recommendation = aiService.recommendLearningWithContext(context);
        result.put("recommendation", recommendation);
        result.put("dataDriven", true);
        return result;
    }
}
