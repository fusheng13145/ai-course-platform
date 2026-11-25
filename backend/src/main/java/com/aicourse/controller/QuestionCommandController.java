package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.dto.QuestionSaveDTO;
import com.aicourse.vo.TeacherQuestionVO;
import com.aicourse.entity.Question;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.QuestionAccessSupport;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.BatchImportResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 题库写面控制器
 * 提供题目创建、审核、复制、修改、删除与 JSON 批量导入
 */
@RestController
@RequestMapping("/questions")
@Tag(name = "题库管理", description = "题目CRUD、AI生成、随机选题、批量导入")
public class QuestionCommandController {

    private final QuestionService questionService;
    private final CourseAccessControl courseAccessControl;
    private final QuestionAccessSupport questionAccessSupport;

    public QuestionCommandController(QuestionService questionService,
                                     CourseAccessControl courseAccessControl,
                                     QuestionAccessSupport questionAccessSupport) {
        this.questionService = questionService;
        this.courseAccessControl = courseAccessControl;
        this.questionAccessSupport = questionAccessSupport;
    }

    /**
     * 创建题目（增强版，含知识点关联处理）
     */
    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "创建题目",
            description = "字段真名：correctAnswers（MySQL JSON 列，必须是合法 JSON 文本：单选 \"A\"、多选 [\"A\",\"B\"]、判断 \"true\"；裸 A 会 400）；"
                    + "options/knowledgePoints 为 JSON 文本字符串列。创建时 courseId 必填。")
    public ApiResponse<TeacherQuestionVO> create(@Validated(QuestionSaveDTO.Create.class) @RequestBody QuestionSaveDTO dto,
                                        Authentication auth) {
        Question question = dto.toEntity();
        courseAccessControl.assertCanManageCourse(question.getCourseId(), auth);
        question.setCreatedBy((Long) auth.getPrincipal());
        Question created = questionService.createQuestion(question);
        return ApiResponse.success("创建成功", TeacherQuestionVO.from(created));
    }

    /**
     * 审核题目（通过/拒绝）
     */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "审核题目")
    public ApiResponse<Void> review(@PathVariable Long id,
                                    @Valid @RequestBody Map<String, Boolean> body,
                                    Authentication auth) {
        Question existing = questionAccessSupport.requireQuestion(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        boolean approved = body.getOrDefault("approved", true);
        questionService.reviewQuestion(id, approved);
        return ApiResponse.success(approved ? "审核通过" : "已拒绝", null);
    }

    /**
     * 复制题目（创建变式题）
     */
    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "复制题目")
    public ApiResponse<TeacherQuestionVO> copy(@PathVariable Long id, Authentication auth) {
        Question existing = questionAccessSupport.requireQuestion(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        Question copied = questionService.copyQuestion(id);
        return ApiResponse.success("复制成功", TeacherQuestionVO.from(copied));
    }

    /**
     * 新增题目（基础版）
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "新增题目",
            description = "字段真名与 JSON 列契约同 POST /questions/create：correctAnswers 为合法 JSON 文本，"
                    + "options/knowledgePoints 为 JSON 文本字符串列；创建时 courseId 必填。")
    public ApiResponse<TeacherQuestionVO> createBasic(@Validated(QuestionSaveDTO.Create.class) @RequestBody QuestionSaveDTO dto,
                                             Authentication auth) {
        Question question = dto.toEntity();
        courseAccessControl.assertCanManageCourse(question.getCourseId(), auth);
        question.setCreatedBy((Long) auth.getPrincipal());
        questionService.save(question);
        return ApiResponse.success("创建成功", TeacherQuestionVO.from(question));
    }

    /**
     * 修改题目（教师/管理员权限）。局部更新：只落传了的字段，课程归属恒取库内值。
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "修改题目",
            description = "字段真名与 JSON 列契约同创建端点；courseId 忽略请求值，始终沿用原题目课程。")
    public ApiResponse<TeacherQuestionVO> update(@PathVariable Long id,
                                        @Valid @RequestBody QuestionSaveDTO dto,
                                        Authentication auth) {
        Question existing = questionAccessSupport.requireQuestion(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        Question question = dto.toEntity();
        question.setId(id);
        question.setCourseId(existing.getCourseId());
        questionService.updateQuestion(question);
        Question updatedQuestion = questionService.getById(id);
        return ApiResponse.success("更新成功", TeacherQuestionVO.from(updatedQuestion));
    }

    /**
     * 删除题目（教师/管理员权限）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "删除题目")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        Question existing = questionAccessSupport.requireQuestion(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        questionService.deleteQuestion(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 批量导入题目（进阶：一次性导入多题，自动跳过无效项）
     * <p>入参逐项走 {@link QuestionSaveDTO}：只接受题目业务字段，{@code id}／{@code createdBy}／
     * {@code usageCount}／{@code correctRate} 等由服务端填写，客户端传入一律忽略。
     * 不做 Bean Validation 级联，保持「单条不合法只跳过、不整批 400」的既有语义。</p>
     */
    @PostMapping("/batch-import")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "批量导入题目", description = "JSON 批量导入（兼容保留，自动跳过无效项）；"
            + "入参字段真名同 POST /questions/create，服务端字段不接受传入；Excel(.xlsx) 批量导入请使用 /teacher/questions/import-excel")
    public ApiResponse<BatchImportResultVO> batchImport(@RequestBody List<QuestionSaveDTO> items, Authentication auth) {
        List<Question> questions = new ArrayList<>(items == null ? 0 : items.size());
        if (items != null) {
            for (QuestionSaveDTO item : items) {
                Question question = item == null ? new Question() : item.toEntity();
                courseAccessControl.assertCanManageCourse(question.getCourseId(), auth);
                questions.add(question);
            }
        }
        int imported = questionService.batchImport(questions, (Long) auth.getPrincipal());
        return ApiResponse.success("导入完成", new BatchImportResultVO(imported, questions.size()));
    }
}


