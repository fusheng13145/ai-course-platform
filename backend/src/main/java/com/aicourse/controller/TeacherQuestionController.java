package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.QuestionSaveDTO;
import com.aicourse.entity.Course;
import com.aicourse.entity.Question;
import com.aicourse.security.TeacherAccessGuard;
import com.aicourse.service.CourseService;
import com.aicourse.service.QuestionService;
import com.aicourse.util.ExcelUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 教师端题库 CRUD 与导出接口（2026-11-18 从 TeacherController 拆出）。
 *
 * <p>批量导入见 {@link TeacherImportController}；创建/更新仍走 QuestionSaveDTO 的 JSON 列契约。</p>
 */
@RestController
@RequestMapping("/teacher")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
@Tag(name = "教师端题库接口", description = "题目 CRUD / 列表筛选 / 导出")
public class TeacherQuestionController {

    private final QuestionService questionService;
    private final CourseService courseService;
    private final TeacherAccessGuard teacherAccessGuard;

    public TeacherQuestionController(QuestionService questionService,
                                     CourseService courseService,
                                     TeacherAccessGuard teacherAccessGuard) {
        this.questionService = questionService;
        this.courseService = courseService;
        this.teacherAccessGuard = teacherAccessGuard;
    }

    /** 创建题目（若指定课程则需归属校验） */
    @PostMapping("/questions")
    @Operation(summary = "创建题目",
            description = "字段真名：correctAnswers（MySQL JSON 列，必须是合法 JSON 文本：单选 \"A\"、多选 [\"A\",\"B\"]、判断 \"true\"；裸 A 会 400）；"
                    + "options/knowledgePoints 为 JSON 文本字符串列。创建时 courseId 必填。")
    public ApiResponse<Question> createQuestion(@Validated(QuestionSaveDTO.Create.class) @RequestBody QuestionSaveDTO dto,
                                                Authentication auth) {
        Question question = dto.toEntity();
        if (question.getCourseId() != null) {
            teacherAccessGuard.assertCourseOwner(question.getCourseId(), auth);
        }
        question.setCreatedBy((Long) auth.getPrincipal());
        questionService.createQuestion(question);
        return ApiResponse.success("创建成功", question);
    }

    /** 分页查询题目列表（可按课程/题型/难度筛选；教师默认限定本人课程与本人创建的历史题） */
    @GetMapping("/questions")
    @Operation(summary = "分页查询题目列表")
    public ApiResponse<PagedResponse<Question>> getQuestions(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (courseId != null) {
            teacherAccessGuard.assertCourseOwner(courseId, auth);
        }
        LambdaQueryWrapper<Question> qw = new LambdaQueryWrapper<Question>()
                .eq(courseId != null, Question::getCourseId, courseId)
                .eq(type != null, Question::getType, type)
                .eq(difficulty != null, Question::getDifficulty, difficulty);
        if (courseId == null && !teacherAccessGuard.isAdmin(auth)) {
            List<Long> ownCourseIds = courseService.list(
                            new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, userId))
                    .stream().map(Course::getId).toList();
            qw.and(w -> {
                if (!ownCourseIds.isEmpty()) {
                    w.in(Question::getCourseId, ownCourseIds).or();
                }
                w.eq(Question::getCreatedBy, userId);
            });
        }
        Page<Question> questionPage = questionService.page(Page.of(page, size), qw);
        return ApiResponse.success(PagedResponse.of(questionPage));
    }

    /** 获取题目详情（需题目归属校验） */
    @GetMapping("/questions/{questionId}")
    @Operation(summary = "获取题目详情")
    public ApiResponse<Question> getQuestionDetail(@PathVariable Long questionId, Authentication auth) {
        teacherAccessGuard.assertQuestionOwner(questionId, auth);
        return ApiResponse.success(questionService.getById(questionId));
    }

    /** 更新题目（需题目归属校验） */
    @PutMapping("/questions/{questionId}")
    @Operation(summary = "更新题目",
            description = "字段真名与 JSON 列契约同创建端点；courseId 忽略请求值，始终沿用原题目课程。")
    public ApiResponse<Question> updateQuestion(@PathVariable Long questionId,
                                                @Valid @RequestBody QuestionSaveDTO dto,
                                                Authentication auth) {
        teacherAccessGuard.assertQuestionOwner(questionId, auth);
        Question existing = questionService.getById(questionId);
        Question question = dto.toEntity();
        question.setId(questionId);
        if (existing != null) {
            question.setCourseId(existing.getCourseId());
        }
        questionService.updateById(question);
        return ApiResponse.success("更新成功", questionService.getById(questionId));
    }

    /** 删除题目（需题目归属校验） */
    @DeleteMapping("/questions/{questionId}")
    @Operation(summary = "删除题目")
    public ApiResponse<Void> deleteQuestion(@PathVariable Long questionId, Authentication auth) {
        teacherAccessGuard.assertQuestionOwner(questionId, auth);
        questionService.removeById(questionId);
        return ApiResponse.success("删除成功", null);
    }

    /** 导出题库为 Excel 文件（可按课程筛选） */
    @GetMapping("/questions/export")
    @Operation(summary = "导出题库", description = "导出题库为 Excel(.xlsx) 文件，可按课程筛选")
    public void exportQuestions(@RequestParam(required = false) Long courseId, Authentication auth,
                                HttpServletResponse response) throws IOException {
        if (courseId != null) {
            teacherAccessGuard.assertCourseOwner(courseId, auth);
        }
        LambdaQueryWrapper<Question> qw = new LambdaQueryWrapper<Question>()
                .eq(courseId != null, Question::getCourseId, courseId);
        List<Question> questions = questionService.list(qw);
        ExcelUtil.setDownloadHeaders(response, "题库导出.xlsx");
        ExcelUtil.writeQuestions(response.getOutputStream(), questions);
    }
}
