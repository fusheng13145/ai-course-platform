package com.aicourse.controller;

import com.aicourse.application.exam.ExamPaperApplicationService;
import com.aicourse.common.ApiResponse;
import com.aicourse.dto.ActivityTargetDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 试卷目标教学班控制器。
 *
 * <p>S-01 大控制器拆分：从 {@link ExamPaperController} 抽出 visibility/目标教学班
 * 的读取与设置，路由前缀仍为 {@code /exam-papers}，与主控制器按路径区分。</p>
 *
 * <p>3E-03C：两级守卫（试卷存在性、可见性、管理权）与 teachingClassIds 维护已下移到
 * {@link ExamPaperApplicationService}；本类只剩路由与响应封装。</p>
 */
@Tag(name = "试卷目标教学班", description = "试卷可见范围与目标教学班维护")
@RestController
@RequestMapping("/exam-papers")
public class ExamPaperTargetController {

    private final ExamPaperApplicationService examPaperApplicationService;

    public ExamPaperTargetController(ExamPaperApplicationService examPaperApplicationService) {
        this.examPaperApplicationService = examPaperApplicationService;
    }

    /**
     * 查询试卷目标教学班。
     */
    @GetMapping("/{id}/targets")
    @Operation(summary = "查询试卷目标教学班", description = "返回试卷可见范围与目标教学班ID")
    public ApiResponse<Map<String, Object>> getTargets(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(examPaperApplicationService.targets(id, auth));
    }

    /**
     * 设置试卷目标教学班（教师权限；管理员调用返回 403）。
     */
    @PutMapping("/{id}/targets")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "设置试卷目标教学班", description = "维护 visibility 与目标教学班；管理员调用返回 403")
    public ApiResponse<Map<String, Object>> updateTargets(@PathVariable Long id,
                                                          @Valid @RequestBody ActivityTargetDTO targets,
                                                          Authentication auth) {
        return ApiResponse.success("设置成功", examPaperApplicationService.updateTargets(id, targets, auth));
    }
}
