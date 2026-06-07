package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.*;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/questions")
public class QuestionController {
    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public ApiResponse<PagedResponse<Question>> list(@RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "10") long size,
                                                      @RequestParam(required = false) Long courseId,
                                                      @RequestParam(required = false) String type,
                                                      @RequestParam(required = false) String difficulty) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
            .eq(courseId != null, Question::getCourseId, courseId)
            .eq(type != null, Question::getType, type)
            .eq(difficulty != null, Question::getDifficulty, difficulty)
            .orderByDesc(Question::getCreatedAt);
        return ApiResponse.success(PagedResponse.of(questionService.page(Page.of(page, size), wrapper)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Question> getById(@PathVariable Long id) {
        return ApiResponse.success(questionService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Question> create(@RequestBody Question question, Authentication auth) {
        question.setCreatedBy((Long) auth.getPrincipal());
        questionService.save(question);
        return ApiResponse.success("创建成功", question);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Question> update(@PathVariable Long id, @RequestBody Question question) {
        question.setId(id);
        questionService.updateById(question);
        return ApiResponse.success("更新成功", questionService.getById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        questionService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }
}
