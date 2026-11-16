package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.QuestionPracticeCheckDTO;
import com.aicourse.service.QuestionPracticeService;
import com.aicourse.vo.QuestionPracticeResultVO;
import com.aicourse.vo.QuestionPracticeVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 学生题库练习接口（P0-A1）。
 * 与教师题库管理接口分开：这里只返回不含答案的题目投影，提交后才返回答案与解析。
 */
@RestController
@RequestMapping("/questions/practice")
@Tag(name = "学生题库练习", description = "教师按课程开放后，学生按课程刷题并即时判分")
public class QuestionPracticeController {

    private final QuestionPracticeService questionPracticeService;

    public QuestionPracticeController(QuestionPracticeService questionPracticeService) {
        this.questionPracticeService = questionPracticeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    @Operation(summary = "分页获取学生练习题目", description = "学生需已选课且课程教师已开放刷题；返回不含答案与解析")
    public ApiResponse<PagedResponse<QuestionPracticeVO>> list(
            @RequestParam Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            Authentication auth) {
        Page<QuestionPracticeVO> result = questionPracticeService.listPracticeQuestions(
                courseId, type, difficulty, page, size, auth);
        return ApiResponse.success(PagedResponse.of(result));
    }

    @PostMapping("/check")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    @Operation(summary = "提交练习答案并判分", description = "仅在提交后返回正确答案与解析")
    public ApiResponse<QuestionPracticeResultVO> check(@Valid @RequestBody QuestionPracticeCheckDTO dto,
                                                       Authentication auth) {
        return ApiResponse.success(questionPracticeService.checkAnswer(dto.getQuestionId(), dto.getAnswer(), auth));
    }
}
