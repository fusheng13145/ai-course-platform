package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Question;
import com.aicourse.service.AIService;
import com.aicourse.service.QuestionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 题目管理接口控制器
 * 提供题目CRUD、AI生成、审核、搜索、统计等RESTful API
 */
@RestController
@RequestMapping("/questions")
public class QuestionController {

    private final QuestionService questionService;
    private final AIService aiService;

    public QuestionController(QuestionService questionService, AIService aiService) {
        this.questionService = questionService;
        this.aiService = aiService;
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
    @GetMapping
    public ApiResponse<PagedResponse<Question>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty) {
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<Question>()
                .eq(courseId != null, Question::getCourseId, courseId)
                .eq(type != null, Question::getType, type)
                .eq(difficulty != null, Question::getDifficulty, difficulty)
                .orderByDesc(Question::getCreatedAt);
        Page<Question> questionPage = questionService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(questionPage));
    }

    /**
     * 根据ID查询题目详情
     * @param id 题目ID
     * @return 题目信息
     */
    @GetMapping("/{id}")
    public ApiResponse<Question> getById(@PathVariable Long id) {
        Question question = questionService.getById(id);
        return ApiResponse.success(question);
    }

    /**
     * 创建题目（增强版，含知识点关联处理）
     * @param question 题目信息
     * @param auth 认证信息
     * @return 新增题目数据
     */
    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Question> create(@RequestBody Question question, Authentication auth) {
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
    public ApiResponse<Question> generateByAI(@RequestBody Map<String, String> params, Authentication auth) {
        Long courseId = Long.valueOf(params.get("courseId"));
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
    public ApiResponse<Void> review(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
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
    public ApiResponse<Question> copy(@PathVariable Long id) {
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
    @GetMapping("/search")
    public ApiResponse<PagedResponse<Question>> search(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Question> result = questionService.searchQuestions(courseId, type, difficulty, keyword, page, size);
        return ApiResponse.success(PagedResponse.of(result));
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
    public ApiResponse<List<Question>> randomSelect(
            @RequestParam Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "10") int count) {
        List<Question> questions = questionService.randomSelect(courseId, type, difficulty, count);
        return ApiResponse.success(questions);
    }

    /**
     * 按知识点查询题目
     * @param kpId 知识点ID
     * @return 题目列表
     */
    @GetMapping("/kp/{kpId}")
    public ApiResponse<List<Question>> getByKnowledgePoint(@PathVariable Long kpId) {
        List<Question> questions = questionService.getByKnowledgePoint(kpId);
        return ApiResponse.success(questions);
    }

    /**
     * 获取题目使用统计
     * @param id 题目ID
     * @return 统计信息
     */
    @GetMapping("/{id}/stats")
    public ApiResponse<Map<String, Object>> getStats(@PathVariable Long id) {
        Map<String, Object> stats = questionService.getQuestionStats(id);
        return ApiResponse.success(stats);
    }

    /**
     * 获取易错题TOP N
     * @param courseId 课程ID（可选）
     * @param limit 返回数量（默认20）
     * @return 易错题列表
     */
    @GetMapping("/error-prone")
    public ApiResponse<List<Question>> errorProne(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "20") int limit) {
        List<Question> questions = questionService.getErrorProneQuestions(courseId, limit);
        return ApiResponse.success(questions);
    }

    /**
     * 新增题目（基础版）
     * @param question 题目信息
     * @param auth 认证信息
     * @return 新增题目数据
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Question> createBasic(@RequestBody Question question, Authentication auth) {
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
    public ApiResponse<Question> update(@PathVariable Long id, @RequestBody Question question) {
        question.setId(id);
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
    public ApiResponse<Void> delete(@PathVariable Long id) {
        questionService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * AI 智能生成题目（基础版，仅返回内容不保存）
     * @param data 生成参数
     * @return 生成的题目内容
     */
    @PostMapping("/ai-generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Map<String, String>> generateByAIBasic(@RequestBody Map<String, Object> data) {
        Long courseId = Long.valueOf(data.get("courseId").toString());
        String type = (String) data.get("type");
        String difficulty = (String) data.get("difficulty");
        int count = data.containsKey("count") ? Integer.parseInt(data.get("count").toString()) : 1;

        String result = aiService.generateQuestion(courseId, type, difficulty, count);
        return ApiResponse.success("AI生成成功", Map.of("questions", result));
    }
}
