package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.vo.QuestionVO;
import com.aicourse.dto.QuestionGenerateDTO;
import com.aicourse.entity.Question;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.exception.BusinessException;
import jakarta.validation.Valid;
import com.aicourse.security.RoleUtil;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.AIService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.component.ai.AiCallContext;
import com.aicourse.service.component.ai.AiGenerationPolicy;
import com.aicourse.vo.BatchImportResultVO;
import com.aicourse.vo.QuestionStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题目管理接口控制器
 * 提供题目CRUD、AI生成、审核、搜索、统计等RESTful API
 */
@RestController
@RequestMapping("/questions")
@Tag(name = "题库管理", description = "题目CRUD、AI生成、随机选题、批量导入")
public class QuestionController {

    private static final Logger log = LoggerFactory.getLogger(QuestionController.class);

    private final QuestionService questionService;
    private final AIService aiService;
    private final KnowledgePointService knowledgePointService;
    /** 角色矩阵「限制教师出题权限」的判定：平台级治理开关（ADMIN 不受限）。 */
    private final AiGenerationPolicy aiGenerationPolicy;
    private final CourseAccessControl courseAccessControl;

    public QuestionController(QuestionService questionService, AIService aiService,
                              KnowledgePointService knowledgePointService,
                              AiGenerationPolicy aiGenerationPolicy,
                              CourseAccessControl courseAccessControl) {
        this.questionService = questionService;
        this.aiService = aiService;
        this.knowledgePointService = knowledgePointService;
        this.aiGenerationPolicy = aiGenerationPolicy;
        this.courseAccessControl = courseAccessControl;
    }

    /**
     * AI 出题权限校验（**两层**，2026-10-13 修订为"平台总闸 + 逐人名单"）：
     * ① 管理员可在管理端关闭「教师 AI 出题」总闸 → 对所有非 ADMIN 调用方 403（紧急一键全禁）；
     * ② 管理员可把**特定教师**加入限制名单 → 只对该教师 403。
     * ADMIN 两层都不受限（治理者保留能力）。
     *
     * <p>刻意<b>不</b>改 {@code @PreAuthorize}（那是角色门，与治理开关是两件事）：
     * 角色门写死"谁能进来"，本校验表达"进来之后当前是否被允许"。</p>
     */
    private void assertQuestionGenerationAllowed(Authentication auth) {
        if (RoleUtil.isAdmin(auth)) {
            return;
        }
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        if (!aiGenerationPolicy.isTeacherQuestionGenerationEnabled()) {
            throw new BusinessException(ApiCode.FORBIDDEN,
                    "管理员已限制教师的 AI 出题权限，请联系管理员开通后再试。");
        }
        if (aiGenerationPolicy.isUserRestricted(userId)) {
            throw new BusinessException(ApiCode.FORBIDDEN,
                    "该账号已被管理员限制 AI 出题权限，请联系管理员解除后再试。");
        }
    }

    /**
     * 分页查询题目列表（支持多条件筛选）
     * @param page 当前页码
     * @param size 每页条数
     * @param courseId 课程ID
     * @param type 题目类型
     * @param difficulty 难度等级
     * @return 分页题目数据
     */
    // 收口（2026-09-23，P0-3）：题库读接口限教师/管理员。
    // 学生端零依赖这些接口——随堂测走 /questions/random，考试取题走 /exam-papers/{id}/questions，
    // 错题本走 /wrong-questions。故学生调用应得 403，而非拿到（虽已脱敏的）全校题库。
    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "分页查询题目列表")
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            Authentication auth) {
        boolean privileged = RoleUtil.isTeacherOrAdmin(auth);
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<Question>()
                .eq(courseId != null, Question::getCourseId, courseId)
                .eq(type != null, Question::getType, type)
                .eq(difficulty != null, Question::getDifficulty, difficulty)
                .orderByDesc(Question::getCreatedAt);
        Page<Question> questionPage = questionService.page(Page.of(page, size), queryWrapper);
        if (questionPage == null) {
            questionPage = Page.of(page, size);
        }
        if (privileged) {
            return ApiResponse.success(PagedResponse.of(questionPage));
        }
        // 学生不可见答案与解析
        List<QuestionVO> vos = questionPage.getRecords().stream().map(QuestionVO::from).toList();
        return ApiResponse.success(PagedResponse.of(vos, questionPage.getTotal(), questionPage.getCurrent(), questionPage.getSize()));
    }

    /**
     * 根据ID查询题目详情
     * @param id 题目ID
     * @return 题目信息
     */
    // 收口（2026-09-23，P0-3）：题库详情同 list，仅供教师/管理员
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "根据ID查询题目详情")
    public ApiResponse<?> getById(@PathVariable Long id, Authentication auth) {
        Question question = questionService.getById(id);
        if (RoleUtil.isTeacherOrAdmin(auth)) {
            return ApiResponse.success(question);
        }
        // 学生不可见答案与解析
        return ApiResponse.success(QuestionVO.from(question));
    }

    /**
     * 创建题目（增强版，含知识点关联处理）
     * @param question 题目信息
     * @param auth 认证信息
     * @return 新增题目数据
     */
    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "创建题目")
    public ApiResponse<Question> create(@Valid @RequestBody Question question, Authentication auth) {
        courseAccessControl.assertCanManageCourse(question.getCourseId(), auth);
        question.setCreatedBy((Long) auth.getPrincipal());
        Question created = questionService.createQuestion(question);
        return ApiResponse.success("创建成功", created);
    }

    /**
     * AI智能生成题目（增强版，直接保存到题库）
     * @param params 生成参数（courseId, type, difficulty, knowledgePointNames）
     * @param auth 认证信息
     * @return 生成的题目
     */
    @PostMapping("/generate-by-ai")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "AI智能生成题目")
    public ApiResponse<Question> generateByAI(@Valid @RequestBody Map<String, String> params, Authentication auth) {
        Long courseId = Long.valueOf(params.get("courseId"));
        courseAccessControl.assertCanManageCourse(courseId, auth);
        String type = params.get("type");
        String difficulty = params.get("difficulty");
        String knowledgePointNames = params.get("knowledgePointNames");

        Question question = questionService.generateByAI(courseId, type, difficulty, knowledgePointNames);
        question.setCreatedBy((Long) auth.getPrincipal());
        return ApiResponse.success("AI生成成功", question);
    }

    /**
     * 审核题目（通过/拒绝）
     * @param id 题目ID
     * @param body 审核参数（approved: boolean）
     * @return 操作结果
     */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "审核题目")
    public ApiResponse<Void> review(@PathVariable Long id,
                                    @Valid @RequestBody Map<String, Boolean> body,
                                    Authentication auth) {
        Question existing = requireQuestion(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        boolean approved = body.getOrDefault("approved", true);
        questionService.reviewQuestion(id, approved);
        return ApiResponse.success(approved ? "审核通过" : "已拒绝", null);
    }

    /**
     * 复制题目（创建变式题）
     * @param id 源题目ID
     * @return 复制后的新题目
     */
    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "复制题目")
    public ApiResponse<Question> copy(@PathVariable Long id, Authentication auth) {
        Question existing = requireQuestion(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        Question copied = questionService.copyQuestion(id);
        return ApiResponse.success("复制成功", copied);
    }

    /**
     * 智能搜索题目（全文检索+筛选）
     * @param courseId 课程ID（可选）
     * @param type 题目类型（可选）
     * @param difficulty 难度等级（可选）
     * @param keyword 关键词（模糊匹配题干）
     * @param page 页码
     * @param size 每页大小
     * @return 分页搜索结果
     */
    // 收口（2026-09-23，P0-3）：题库搜索仅教师/管理员使用
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "智能搜索题目")
    public ApiResponse<?> search(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        boolean privileged = RoleUtil.isTeacherOrAdmin(auth);
        Page<Question> result = questionService.searchQuestions(courseId, type, difficulty, keyword, page, size);
        if (result == null) {
            result = new Page<>();
        }
        if (privileged) {
            return ApiResponse.success(PagedResponse.of(result));
        }
        List<QuestionVO> vos = result.getRecords().stream().map(QuestionVO::from).toList();
        return ApiResponse.success(PagedResponse.of(vos, result.getTotal(), result.getCurrent(), result.getSize()));
    }

    /**
     * 随机抽取题目（用于组卷）
     * @param courseId 课程ID
     * @param type 题目类型（可选）
     * @param difficulty 难度等级（可选）
     * @param count 抽取数量
     * @return 随机题目列表
     */
    @GetMapping("/random")
    @Operation(summary = "随机抽取题目")
    public ApiResponse<?> randomSelect(
            @RequestParam Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "10") int count,
            Authentication auth) {
        boolean privileged = RoleUtil.isTeacherOrAdmin(auth);
        List<Question> questions = questionService.randomSelect(courseId, type, difficulty, count);
        if (questions == null) {
            questions = java.util.Collections.emptyList();
        }
        if (privileged) {
            return ApiResponse.success(questions);
        }
        return ApiResponse.success(questions.stream().map(QuestionVO::from).toList());
    }

    /**
     * 按知识点查询题目
     * @param kpId 知识点ID
     * @return 题目列表
     */
    // 收口（2026-09-23，P0-3）：按知识点查题属题库读范围，仅供教师/管理员
    @GetMapping("/kp/{kpId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "按知识点查询题目")
    public ApiResponse<?> getByKnowledgePoint(@PathVariable Long kpId, Authentication auth) {
        boolean privileged = RoleUtil.isTeacherOrAdmin(auth);
        List<Question> questions = questionService.getByKnowledgePoint(kpId);
        if (questions == null) {
            questions = java.util.Collections.emptyList();
        }
        if (privileged) {
            return ApiResponse.success(questions);
        }
        return ApiResponse.success(questions.stream().map(QuestionVO::from).toList());
    }

    /**
     * 获取题目使用统计
     * @param id 题目ID
     * @return 统计信息
     */
    // 收口（2026-09-23，P0-3）：题目使用统计属教学侧数据
    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取题目使用统计")
    public ApiResponse<QuestionStatsVO> getStats(@PathVariable Long id) {
        QuestionStatsVO stats = questionService.getQuestionStats(id);
        return ApiResponse.success(stats);
    }

    /**
     * 获取易错题TOP N
     * @param courseId 课程ID（可选）
     * @param limit 返回数量（默认20）
     * @return 易错题列表
     */
    // 收口（2026-09-23，P0-3）：易错题榜属教学分析数据，仅教师/管理员
    @GetMapping("/error-prone")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取易错题TOP N")
    public ApiResponse<?> errorProne(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        boolean privileged = RoleUtil.isTeacherOrAdmin(auth);
        List<Question> questions = questionService.getErrorProneQuestions(courseId, limit);
        if (questions == null) {
            questions = java.util.Collections.emptyList();
        }
        if (privileged) {
            return ApiResponse.success(questions);
        }
        return ApiResponse.success(questions.stream().map(QuestionVO::from).toList());
    }

    /**
     * 新增题目（基础版）
     * @param question 题目信息
     * @param auth 认证信息
     * @return 新增题目数据
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "新增题目")
    public ApiResponse<Question> createBasic(@Valid @RequestBody Question question, Authentication auth) {
        courseAccessControl.assertCanManageCourse(question.getCourseId(), auth);
        question.setCreatedBy((Long) auth.getPrincipal());
        questionService.save(question);
        return ApiResponse.success("创建成功", question);
    }

    /**
     * 修改题目（教师/管理员权限）
     * @param id 题目ID
     * @param question 待更新题目数据
     * @return 更新后题目信息
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "修改题目")
    public ApiResponse<Question> update(@PathVariable Long id,
                                        @Valid @RequestBody Question question,
                                        Authentication auth) {
        Question existing = requireQuestion(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        question.setId(id);
        question.setCourseId(existing.getCourseId());
        questionService.updateById(question);
        Question updatedQuestion = questionService.getById(id);
        return ApiResponse.success("更新成功", updatedQuestion);
    }

    /**
     * 删除题目（教师/管理员权限）
     * @param id 题目ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "删除题目")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        Question existing = requireQuestion(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        questionService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * AI 智能生成题目（基础版，仅返回内容不保存）
     * @param dto 生成参数
     * @return 生成的题目内容
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
        assertQuestionGenerationAllowed(auth);
        // 跨课程 AI 统计：把调用者与归属课程带进作用域，落库发生在 AiCoreComponent
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        String result = AiCallContext.run(userId, courseId, "QUESTION_GENERATE",
                () -> aiService.generateQuestion(courseId, type, difficulty, count));
        return ApiResponse.success("AI生成成功", Map.of("questions", result));
    }

    /**
     * AI 批量生成题目（进阶：一次生成多题并直接入库）
     * @param dto courseId / type / difficulty / knowledgePointNames / count
     * @return 已保存的题目列表
     */
    @PostMapping("/generate-by-ai/batch")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "AI批量生成题目")
    public ApiResponse<List<Question>> generateBatchByAI(@Valid @RequestBody QuestionGenerateDTO dto, Authentication auth) {
        Long courseId = dto.getCourseId();
        String type = dto.getType();
        String difficulty = dto.getDifficulty();
        String kp = dto.getKnowledgePointNames();
        int count = dto.getCount() != null ? dto.getCount() : 5;

        courseAccessControl.assertCanManageCourse(courseId, auth);
        assertQuestionGenerationAllowed(auth);

        // 知识点感知增强：用户未指定知识点时，自动从课程知识图谱中提取
        if ((kp == null || kp.isBlank()) && courseId != null) {
            List<KnowledgePoint> kps = knowledgePointService.list(
                    new LambdaQueryWrapper<KnowledgePoint>()
                            .eq(KnowledgePoint::getCourseId, courseId));
            if (!kps.isEmpty()) {
                kp = kps.stream().map(KnowledgePoint::getName)
                        .limit(15).collect(Collectors.joining("、"));
            }
        }

        final String resolvedKp = kp;
        List<Question> questions = AiCallContext.run((Long) auth.getPrincipal(), courseId, "QUESTION_GENERATE",
                () -> questionService.generateBatchByAI(
                        courseId, type, difficulty, resolvedKp, count, (Long) auth.getPrincipal()));
        return ApiResponse.success("AI批量生成成功（" + (resolvedKp != null ? "知识点感知" : "通用模式") + "）", questions);
    }

    /**
     * 批量导入题目（进阶：一次性导入多题，自动跳过无效项）
     * @param questions 题目列表
     * @return 导入结果（成功数 / 总数）
     */
    @PostMapping("/batch-import")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "批量导入题目", description = "JSON 批量导入（兼容保留，自动跳过无效项）；Excel(.xlsx) 批量导入请使用 /teacher/questions/import-excel")
    public ApiResponse<BatchImportResultVO> batchImport(@Valid @RequestBody List<Question> questions, Authentication auth) {
        for (Question question : questions) {
            courseAccessControl.assertCanManageCourse(question.getCourseId(), auth);
        }
        int imported = questionService.batchImport(questions, (Long) auth.getPrincipal());
        return ApiResponse.success("导入完成", new BatchImportResultVO(imported, questions.size()));
    }

    private Question requireQuestion(Long id) {
        Question question = questionService.getById(id);
        if (question == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "题目不存在");
        }
        return question;
    }
}
