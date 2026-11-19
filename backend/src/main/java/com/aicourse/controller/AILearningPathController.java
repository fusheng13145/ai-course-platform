package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.dto.LearningPathDTO;
import com.aicourse.exception.BusinessException;
import com.aicourse.exception.LLMUnavailableException;
import com.aicourse.security.AIAccessSupport;
import com.aicourse.service.LearningPathService;
import com.aicourse.service.component.ai.AiCallContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * AI 学习路径接口（2026-11-18 从 AIController 拆出）。
 * 基础路径仍为 /api/ai。
 */
@RestController
@RequestMapping("/ai")
@Slf4j
@Tag(name = "AI 助手-学习路径")
@PreAuthorize("isAuthenticated()")
public class AILearningPathController {

    private final LearningPathService learningPathService;
    private final AIAccessSupport aiAccessSupport;

    public AILearningPathController(LearningPathService learningPathService,
                                    AIAccessSupport aiAccessSupport) {
        this.learningPathService = learningPathService;
        this.aiAccessSupport = aiAccessSupport;
    }

    /** 学习路径规划 - AI生成学习路径。POST /api/ai/learning-path/generate */
    @PostMapping("/learning-path/generate")
    public Callable<ApiResponse<Map<String, Object>>> generateLearningPath(
            @Valid @RequestBody LearningPathDTO body,
            Authentication auth) {
        // DR-32：与本控制器其余端点同口径做空值防护——Authentication 参数为 null 时
        // 直接解引用会抛 NPE 并回落 500，掩盖「缺少必要参数」这一业务 400。
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        final String courseId = body.getCourseId();
        final String goal = body.getGoal() != null ? body.getGoal() : "掌握课程核心知识";
        final Long cid = aiAccessSupport.parseCourseId(courseId);
        if (cid == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "课程ID不能为空");
        }
        return () -> AiCallContext.run(userId, cid, "LEARNING_PATH", () -> {
            Map<String, Object> result = new HashMap<>();
            if (cid != null && userId != null) {
                aiAccessSupport.assertCourseReadable(cid, auth);
                try {
                    String pathName = "AI学习路径";
                    // 传 null 掌握度 → 服务层自动从错题学情推导；无学情时退化为标准路径
                    learningPathService.generatePathPersonalized(userId, cid, pathName, goal, null);
                    result.put("status", "created");
                    result.put("message", "学习路径已生成");
                } catch (LLMUnavailableException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("学习路径生成失败: {}", e.getMessage(), e);
                    throw new BusinessException("学习路径生成失败: " + e.getMessage());
                }
            } else {
                throw new BusinessException("缺少必要参数：userId或courseId");
            }
            return ApiResponse.success(result);
        });
    }

    /**
     * 个性化学习路径规划 - 掌握度驱动版。
     * POST /api/ai/learning-path/generate-personalized
     */
    @PostMapping("/learning-path/generate-personalized")
    public Callable<ApiResponse<Map<String, Object>>> generatePersonalizedLearningPath(
            @Valid @RequestBody LearningPathDTO body,
            Authentication auth) {
        // DR-32：同 generateLearningPath，null Authentication 不再解引用抛 NPE
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        final String courseId = body.getCourseId();
        final String goal = body.getGoal() != null ? body.getGoal() : "针对性补强薄弱点";
        final Long cid = aiAccessSupport.parseCourseId(courseId);
        if (cid == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "课程ID不能为空");
        }
        // 掌握度数据：{知识点名称: 掌握度(0-100)}
        final Map<String, Double> mastery;
        if (body.getMasteryData() != null && !body.getMasteryData().isEmpty()) {
            mastery = new HashMap<>();
            for (Map.Entry<String, Object> e : body.getMasteryData().entrySet()) {
                if (e.getValue() instanceof Number) {
                    mastery.put(e.getKey(), ((Number) e.getValue()).doubleValue());
                }
            }
        } else {
            mastery = null;
        }
        return () -> AiCallContext.run(userId, cid, "PERSONALIZED_PATH", () -> {
            Map<String, Object> result = new HashMap<>();
            if (cid != null && userId != null) {
                aiAccessSupport.assertCourseReadable(cid, auth);
                try {
                    String pathName = mastery != null && !mastery.isEmpty()
                            ? "个性化学习路径（掌握度驱动）" : "AI学习路径";
                    learningPathService.generatePathPersonalized(userId, cid, pathName, goal, mastery);
                    result.put("status", "created");
                    result.put("message", "个性化学习路径已生成");
                    result.put("personalized", mastery != null && !mastery.isEmpty());
                } catch (LLMUnavailableException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("个性化学习路径生成失败: {}", e.getMessage(), e);
                    throw new BusinessException("学习路径生成失败: " + e.getMessage());
                }
            } else {
                throw new BusinessException("缺少必要参数：userId或courseId");
            }
            return ApiResponse.success(result);
        });
    }
}
