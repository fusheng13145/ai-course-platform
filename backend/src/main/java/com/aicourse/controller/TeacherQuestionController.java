package com.aicourse.controller;

import com.aicourse.application.question.TeacherQuestionApplicationService;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.QuestionSaveDTO;
import com.aicourse.entity.Question;
import com.aicourse.util.ExcelUtil;
import com.aicourse.vo.TeacherQuestionVO;
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
 *
 * <p>3E-03C：归属校验、教师可见范围拼装与「更新沿用原课程」口径已下移到
 * {@link TeacherQuestionApplicationService}。导出仍在本类写响应流（Excel + servlet 输出属 Web 关注点）。</p>
 */
@RestController
@RequestMapping("/teacher")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
@Tag(name = "教师端题库接口", description = "题目 CRUD / 列表筛选 / 导出")
public class TeacherQuestionController {

    private final TeacherQuestionApplicationService teacherQuestionApplicationService;

    public TeacherQuestionController(TeacherQuestionApplicationService teacherQuestionApplicationService) {
        this.teacherQuestionApplicationService = teacherQuestionApplicationService;
    }

    /** 创建题目（若指定课程则需归属校验） */
    @PostMapping("/questions")
    @Operation(summary = "创建题目",
            description = "字段真名：correctAnswers（MySQL JSON 列，必须是合法 JSON 文本：单选 \"A\"、多选 [\"A\",\"B\"]、判断 \"true\"；裸 A 会 400）；"
                    + "options/knowledgePoints 为 JSON 文本字符串列。创建时 courseId 必填。")
    public ApiResponse<TeacherQuestionVO> createQuestion(@Validated(QuestionSaveDTO.Create.class) @RequestBody QuestionSaveDTO dto,
                                                        Authentication auth) {
        return ApiResponse.success("创建成功",
                teacherQuestionApplicationService.createQuestion(dto, auth));
    }

    /** 分页查询题目列表（可按课程/题型/难度筛选；教师默认限定本人课程与本人创建的历史题） */
    @GetMapping("/questions")
    @Operation(summary = "分页查询题目列表")
    public ApiResponse<PagedResponse<TeacherQuestionVO>> getQuestions(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            Authentication auth) {
        return ApiResponse.success(teacherQuestionApplicationService.pageQuestions(
                page, size, courseId, type, difficulty, auth));
    }

    /** 获取题目详情（需题目归属校验） */
    @GetMapping("/questions/{questionId}")
    @Operation(summary = "获取题目详情")
    public ApiResponse<TeacherQuestionVO> getQuestionDetail(@PathVariable Long questionId, Authentication auth) {
        return ApiResponse.success(teacherQuestionApplicationService.questionDetail(questionId, auth));
    }

    /** 更新题目（需题目归属校验） */
    @PutMapping("/questions/{questionId}")
    @Operation(summary = "更新题目",
            description = "字段真名与 JSON 列契约同创建端点；courseId 忽略请求值，始终沿用原题目课程。")
    public ApiResponse<TeacherQuestionVO> updateQuestion(@PathVariable Long questionId,
                                                        @Valid @RequestBody QuestionSaveDTO dto,
                                                        Authentication auth) {
        return ApiResponse.success("更新成功",
                teacherQuestionApplicationService.updateQuestion(questionId, dto, auth));
    }

    /** 删除题目（需题目归属校验） */
    @DeleteMapping("/questions/{questionId}")
    @Operation(summary = "删除题目")
    public ApiResponse<Void> deleteQuestion(@PathVariable Long questionId, Authentication auth) {
        teacherQuestionApplicationService.deleteQuestion(questionId, auth);
        return ApiResponse.success("删除成功", null);
    }

    /** 导出题库为 Excel 文件（可按课程筛选） */
    @GetMapping("/questions/export")
    @Operation(summary = "导出题库", description = "导出题库为 Excel(.xlsx) 文件，可按课程筛选")
    public void exportQuestions(@RequestParam(required = false) Long courseId, Authentication auth,
                                HttpServletResponse response) throws IOException {
        List<Question> questions = teacherQuestionApplicationService.exportQuestions(courseId, auth);
        ExcelUtil.setDownloadHeaders(response, "题库导出.xlsx");
        ExcelUtil.writeQuestions(response.getOutputStream(), questions);
    }
}
