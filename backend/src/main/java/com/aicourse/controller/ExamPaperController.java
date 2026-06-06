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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/exams")
public class ExamPaperController {
    private final ExamPaperService examPaperService;
    private final ExamQuestionService examQuestionService;
    private final QuestionService questionService;
    private final ExamSessionService examSessionService;

    public ExamPaperController(ExamPaperService examPaperService, ExamQuestionService examQuestionService,
                               QuestionService questionService, ExamSessionService examSessionService) {
        this.examPaperService = examPaperService;
        this.examQuestionService = examQuestionService;
        this.questionService = questionService;
        this.examSessionService = examSessionService;
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<PagedResponse<ExamPaper>> list(@PathVariable Long courseId,
                                                       @RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(PagedResponse.of(examPaperService.page(Page.of(page, size),
            new LambdaQueryWrapper<ExamPaper>().eq(ExamPaper::getCourseId, courseId))));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExamPaper> getById(@PathVariable Long id) {
        return ApiResponse.success(examPaperService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<ExamPaper> create(@RequestBody ExamPaper exam, Authentication auth) {
        exam.setCreatorId((Long) auth.getPrincipal());
        examPaperService.save(exam);
        return ApiResponse.success("创建成功", exam);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<ExamPaper> update(@PathVariable Long id, @RequestBody ExamPaper exam) {
        exam.setId(id);
        examPaperService.updateById(exam);
        return ApiResponse.success("更新成功", examPaperService.getById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        examPaperService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/{id}/questions")
    public ApiResponse<List<Map<String, Object>>> getQuestions(@PathVariable Long id) {
        List<ExamQuestion> eqs = examQuestionService.list(
            new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamPaperId, id));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ExamQuestion eq : eqs) {
            Question q = questionService.getById(eq.getQuestionId());
            if (q != null) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", eq.getId());
                item.put("orderIndex", eq.getOrderIndex());
                item.put("points", eq.getPoints());
                item.put("question", q);
                result.add(item);
            }
        }
        return ApiResponse.success(result);
    }
}
