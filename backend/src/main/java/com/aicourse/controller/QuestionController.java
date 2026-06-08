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

import java.util.Map;

/**
 * 题目管理接口控制器
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
     * 新增题目（教师/管理员权限）
     * @param question 题目信息
     * @param auth 认证信息
     * @return 新增题目数据
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Question> create(@RequestBody Question question, Authentication auth) {
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
     * AI 智能生成题目（教师/管理员权限）
     * @param data 生成参数
     * @param auth 认证信息
     * @return 生成的题目内容
     */
    @PostMapping("/ai-generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Map<String, String>> generateByAI(@RequestBody Map<String, Object> data, Authentication auth) {
        Long courseId = Long.valueOf(data.get("courseId").toString());
        String type = (String) data.get("type");
        String difficulty = (String) data.get("difficulty");
        int count = data.containsKey("count") ? Integer.parseInt(data.get("count").toString()) : 1;

        String result = aiService.generateQuestion(courseId, type, difficulty, count);
        return ApiResponse.success("AI生成成功", Map.of("questions", result));
    }
}