package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.exception.BusinessException;
import com.aicourse.exception.LLMUnavailableException;
import com.aicourse.security.AIAccessSupport;
import com.aicourse.service.AIService;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.LearningRecommendationService;
import com.aicourse.common.AiCallContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * AI 推荐与学情接口（2026-11-18 从 AIController 拆出）。
 * 基础路径仍为 /api/ai。
 */
@RestController
@RequestMapping("/ai")
@Slf4j
@Tag(name = "AI 助手-推荐与学情")
@PreAuthorize("isAuthenticated()")
public class AIInsightController {

    private final AIService aiService;
    private final AnalyticsService analyticsService;
    private final CourseEnrollmentService courseEnrollmentService;
    private final com.aicourse.application.enrollment.CourseEnrollmentApplicationService
            courseEnrollmentApplicationService;
    private final KnowledgePointService knowledgePointService;
    private final LearningRecommendationService learningRecommendationService;
    private final AIAccessSupport aiAccessSupport;

    public AIInsightController(AIService aiService,
                               AnalyticsService analyticsService,
                               CourseEnrollmentService courseEnrollmentService,
                               KnowledgePointService knowledgePointService,
                               LearningRecommendationService learningRecommendationService,
                               AIAccessSupport aiAccessSupport,
                               com.aicourse.application.enrollment.CourseEnrollmentApplicationService
                                       courseEnrollmentApplicationService) {
        this.aiService = aiService;
        this.analyticsService = analyticsService;
        this.courseEnrollmentService = courseEnrollmentService;
        this.knowledgePointService = knowledgePointService;
        this.learningRecommendationService = learningRecommendationService;
        this.aiAccessSupport = aiAccessSupport;
        this.courseEnrollmentApplicationService = courseEnrollmentApplicationService;
    }

    /** 个性化学习推荐。GET /api/ai/recommendations/learning */
    @GetMapping("/recommendations/learning")
    public Callable<ApiResponse<Map<String, Object>>> learningRecommendations(Authentication auth) {
        // 业务编排已下沉至 LearningRecommendationService（学情聚合 + 数据驱动上下文 + LLM 推荐）
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        return () -> AiCallContext.run(userId, null, "LEARNING_RECOMMEND",
                () -> ApiResponse.success(learningRecommendationService.recommendLearning(userId)));
    }

    /** 作业题目推荐。GET /api/ai/recommendations/questions */
    @GetMapping("/recommendations/questions")
    public Callable<ApiResponse<Map<String, Object>>> questionRecommendations(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String difficulty,
            Authentication auth) {
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        final Long cid = courseId;
        final String diff = difficulty;
        // 推荐内容来自课程题库/知识点；非空课程必须先过对象级 ACL。
        if (cid != null) {
            aiAccessSupport.assertCourseReadable(cid, auth);
        }
        return () -> AiCallContext.run(userId, cid, "QUESTION_RECOMMEND", () -> {
            Map<String, Object> result = new HashMap<>();
            result.put("courseId", cid);
            result.put("difficulty", diff);
            List<Map<String, Object>> recommendations = new ArrayList<>();
            if (cid != null) {
                try {
                    String json = aiService.recommendQuestionsJson(cid, diff != null ? diff : "MEDIUM", 3);
                    recommendations = aiService.parseQuestions(json, diff);
                } catch (LLMUnavailableException e) {
                    // 上游不可用必须保持 503，不得被下面的通用 catch 包成 400
                    throw e;
                } catch (Exception e) {
                    log.error("题目推荐生成失败: {}", e.getMessage(), e);
                    throw new BusinessException("题目生成失败: " + e.getMessage());
                }
            }
            result.put("recommendations", recommendations);
            return ApiResponse.success(result);
        });
    }

    /** 知识点薄弱项分析。GET /api/ai/analysis/weak-points */
    @GetMapping("/analysis/weak-points")
    public Callable<ApiResponse<Map<String, Object>>> weakPointsAnalysis(
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        final Long cid = courseId;
        return () -> AiCallContext.run(userId, cid, "WEAK_POINTS", () -> {
            Map<String, Object> result = new HashMap<>();
            result.put("courseId", cid);
            List<Map<String, String>> weakPoints = new ArrayList<>();
            if (userId != null) {
                Map<String, Object> userStats = analyticsService.getUserDashboard(userId);
                // 红线②收口：薄弱点诊断必须基于图谱——注入知识点级掌握度快照
                // （按本人有效选课课程聚合，掌握度升序），LLM 据此定位薄弱知识点。
                // 查询失败降级为无快照（与既有降级纪律一致）。
                try {
                    List<Long> courseIds = courseEnrollmentApplicationService.activeCourseIds(userId);
                    userStats.put("knowledgeMastery", knowledgePointService.masterySnapshot(userId, courseIds, 10));
                } catch (Exception e) {
                    log.warn("知识点掌握度快照获取失败，降级为纯统计口径: userId={}, err={}", userId, e.getMessage());
                }
                result.put("userStats", userStats);
                weakPoints = aiService.analyzeWeakPoints(userStats);
            }
            result.put("weakPoints", weakPoints);
            return ApiResponse.success(result);
        });
    }

    /** 学情 AI 建议面板：聚合学情统计 + 薄弱点，生成结构化学习建议。GET /api/ai/advice/learning */
    @GetMapping("/advice/learning")
    public Callable<ApiResponse<Map<String, Object>>> learningAdvice(
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        final Long cid = courseId;
        return () -> AiCallContext.run(userId, cid, "LEARNING_ADVICE", () -> {
            if (userId == null) {
                throw new BusinessException(ApiCode.UNAUTHORIZED, "未登录");
            }
            try {
                Map<String, Object> userStats = analyticsService.getUserDashboard(userId);
                Map<String, Object> advice = aiService.generateLearningAdvice(userStats);
                return ApiResponse.success(advice);
            } catch (BusinessException e) {
                // 原样上抛：AI 不可用已是 LLMUnavailableException(503)，若在这里一律重写成
                // "生成学习建议失败: xxx"(默认 400)，503 就退化成"你参数不对"，运维与前端都看不出是上游故障
                throw e;
            } catch (Exception e) {
                log.error("生成学习建议失败: {}", e.getMessage(), e);
                throw new BusinessException("生成学习建议失败: " + e.getMessage());
            }
        });
    }
}
