package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.dto.QuestionGenerateDTO;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.vo.TeacherQuestionVO;
import com.aicourse.entity.Question;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.QuestionAccessSupport;
import com.aicourse.service.AIService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.QuestionService;
import com.aicourse.common.AiCallContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题库 AI 出题控制器
 * 提供 AI 生成、直接落库与批量生成三条入口
 */
@RestController
@RequestMapping("/questions")
@Tag(name = "题库管理", description = "题目CRUD、AI生成、随机选题、批量导入")
public class QuestionAiController {

    private final QuestionService questionService;
    private final AIService aiService;
    private final KnowledgePointService knowledgePointService;
    private final com.aicourse.application.knowledge.KnowledgePointApplicationService
            knowledgePointApplicationService;
    private final CourseAccessControl courseAccessControl;
    private final QuestionAccessSupport questionAccessSupport;

    public QuestionAiController(QuestionService questionService,
                                AIService aiService,
                                KnowledgePointService knowledgePointService,
                                CourseAccessControl courseAccessControl,
                                QuestionAccessSupport questionAccessSupport,
                                com.aicourse.application.knowledge.KnowledgePointApplicationService
                                        knowledgePointApplicationService) {
        this.questionService = questionService;
        this.aiService = aiService;
        this.knowledgePointService = knowledgePointService;
        this.courseAccessControl = courseAccessControl;
        this.questionAccessSupport = questionAccessSupport;
        this.knowledgePointApplicationService = knowledgePointApplicationService;
    }

    /**
     * AI智能生成题目（增强版，直接保存到题库）
     */
    @PostMapping("/generate-by-ai")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "AI智能生成题目")
    public ApiResponse<TeacherQuestionVO> generateByAI(@Valid @RequestBody Map<String, String> params, Authentication auth) {
        Long courseId = Long.valueOf(params.get("courseId"));
        courseAccessControl.assertCanManageCourse(courseId, auth);
        // 出题治理两层校验（2026-11-14 审计 D3）：本端点此前缺治理闸，管理员总闸与逐人名单
        // 在「生成并直接落库」的真实出题路径上失效。与 /ai-generate、/generate-by-ai/batch 同口径。
        questionAccessSupport.assertQuestionGenerationAllowed(auth);
        String type = params.get("type");
        String difficulty = params.get("difficulty");
        String knowledgePointNames = params.get("knowledgePointNames");

        Question question = questionService.generateByAI(courseId, type, difficulty, knowledgePointNames);
        question.setCreatedBy((Long) auth.getPrincipal());
        return ApiResponse.success("AI生成成功", TeacherQuestionVO.from(question));
    }

    /**
     * AI 智能生成题目（基础版，仅返回内容不保存）
     */
    @PostMapping("/ai-generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "AI生成题目")
    public ApiResponse<Map<String, String>> generateByAIBasic(@Valid @RequestBody QuestionGenerateDTO dto,
                                                              Authentication auth) {
        Long courseId = dto.getCourseId();
        String type = dto.getType();
        String difficulty = dto.getDifficulty();
        int count = dto.getCount() != null ? dto.getCount() : 1;

        courseAccessControl.assertCanManageCourse(courseId, auth);
        questionAccessSupport.assertQuestionGenerationAllowed(auth);
        // 跨课程 AI 统计：把调用者与归属课程带进作用域，落库发生在 AiCoreComponent
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        String result = AiCallContext.run(userId, courseId, "QUESTION_GENERATE",
                () -> aiService.generateQuestion(courseId, type, difficulty, count));
        return ApiResponse.success("AI生成成功", Map.of("questions", result));
    }

    /**
     * AI 批量生成题目（进阶：一次生成多题并直接入库）
     */
    @PostMapping("/generate-by-ai/batch")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "AI批量生成题目")
    public ApiResponse<List<TeacherQuestionVO>> generateBatchByAI(@Valid @RequestBody QuestionGenerateDTO dto, Authentication auth) {
        Long courseId = dto.getCourseId();
        String type = dto.getType();
        String difficulty = dto.getDifficulty();
        String kp = dto.getKnowledgePointNames();
        int count = dto.getCount() != null ? dto.getCount() : 5;

        courseAccessControl.assertCanManageCourse(courseId, auth);
        questionAccessSupport.assertQuestionGenerationAllowed(auth);

        // 知识点感知增强：用户未指定知识点时，自动从课程知识图谱中提取
        if ((kp == null || kp.isBlank()) && courseId != null) {
            // 3E-05E：知识点取数与拼接（limit 15、按库序）下移到 application 层
            kp = knowledgePointApplicationService.namesByCourse(courseId, 15).orElse(null);
        }

        final String resolvedKp = kp;
        List<Question> questions = AiCallContext.run((Long) auth.getPrincipal(), courseId, "QUESTION_GENERATE",
                () -> questionService.generateBatchByAI(
                        courseId, type, difficulty, resolvedKp, count, (Long) auth.getPrincipal()));
        return ApiResponse.success("AI批量生成成功（" + (resolvedKp != null ? "知识点感知" : "通用模式") + "）",
                TeacherQuestionVO.from(questions));
    }
}

