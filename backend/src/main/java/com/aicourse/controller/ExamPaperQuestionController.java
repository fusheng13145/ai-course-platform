package com.aicourse.controller;

import com.aicourse.application.exam.ExamPaperApplicationService;
import com.aicourse.common.ApiResponse;
import com.aicourse.dto.ExamQuestionBatchDTO;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.vo.ExamPaperQuestionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 试卷题目接口控制器。
 *
 * <p>S-01 大控制器拆分：从 {@link ExamPaperController} 抽出「题目读取 / 批量替换」两个端点，
 * 路由前缀仍为 {@code /exam-papers}，与主控制器的试卷 CRUD/审核端点按方法+路径区分。</p>
 *
 * <p><b>阶段3 · 3E-03A</b>：题目投影、答案可见性裁剪与批量取题都已移入
 * {@link ExamPaperApplicationService}（原来逐题 {@code getById} 是 N+1）。本类现在只剩参数与出参。</p>
 */
@Tag(name = "试卷题目", description = "试卷关联题目的读取与批量替换")
@RestController
@RequestMapping("/exam-papers")
public class ExamPaperQuestionController {

    private final ExamPaperApplicationService examPaperApplicationService;

    public ExamPaperQuestionController(ExamPaperApplicationService examPaperApplicationService) {
        this.examPaperApplicationService = examPaperApplicationService;
    }

    /**
     * 查询试卷关联的题目列表（学生答题 / 教师预览共用，返回体统一脱敏）。
     */
    @GetMapping("/{id}/questions")
    @Operation(summary = "查询试卷关联的题目列表")
    public ApiResponse<List<ExamPaperQuestionVO>> getQuestions(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(examPaperApplicationService.listQuestions(id, auth));
    }

    /**
     * 批量替换试卷题目（教师/管理员权限，手动选题保存通道）。
     */
    @PostMapping("/{id}/questions")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "批量替换试卷题目")
    public ApiResponse<List<com.aicourse.vo.ExamPaperVO.QuestionRefVO>> replaceQuestions(@PathVariable Long id,
            @Valid @RequestBody ExamQuestionBatchDTO body, Authentication auth) {
        List<ExamQuestion> saved = examPaperApplicationService.replaceQuestions(id, body, auth);
        return ApiResponse.success("保存成功", com.aicourse.vo.ExamPaperVO.QuestionRefVO.from(saved));
    }
}
