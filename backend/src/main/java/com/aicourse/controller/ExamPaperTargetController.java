package com.aicourse.controller;

import com.aicourse.common.ActivityVisibility;
import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.dto.ActivityTargetDTO;
import com.aicourse.entity.ExamPaper;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.ActivityVisibilityGuard;
import com.aicourse.security.ExamPaperAccessControl;
import com.aicourse.service.ActivityTargetService;
import com.aicourse.service.ExamPaperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 试卷目标教学班控制器。
 *
 * <p>S-01 大控制器拆分：从 {@link ExamPaperController} 抽出 visibility/目标教学班
 * 的读取与设置，路由前缀仍为 {@code /exam-papers}，与主控制器按路径区分。</p>
 */
@Tag(name = "试卷目标教学班", description = "试卷可见范围与目标教学班维护")
@RestController
@RequestMapping("/exam-papers")
public class ExamPaperTargetController {

    private final ExamPaperService examPaperService;
    private final ActivityTargetService activityTargetService;
    private final ActivityVisibilityGuard activityVisibilityGuard;
    private final ExamPaperAccessControl examPaperAccessControl;

    public ExamPaperTargetController(ExamPaperService examPaperService,
                                     ActivityTargetService activityTargetService,
                                     ActivityVisibilityGuard activityVisibilityGuard,
                                     ExamPaperAccessControl examPaperAccessControl) {
        this.examPaperService = examPaperService;
        this.activityTargetService = activityTargetService;
        this.activityVisibilityGuard = activityVisibilityGuard;
        this.examPaperAccessControl = examPaperAccessControl;
    }

    /**
     * 查询试卷目标教学班。
     */
    @GetMapping("/{id}/targets")
    @Operation(summary = "查询试卷目标教学班", description = "返回试卷可见范围与目标教学班ID")
    public ApiResponse<Map<String, Object>> getTargets(@PathVariable Long id, Authentication auth) {
        ExamPaper paper = requirePaper(id);
        activityVisibilityGuard.assertVisibleExamPaper(paper, auth);
        return ApiResponse.success(buildTargetsPayload(
                ActivityVisibility.normalize(paper.getVisibility()),
                activityTargetService.examPaperTargetClassIds(id)));
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
        activityVisibilityGuard.assertNotAdmin(auth);
        ExamPaper existing = requirePaper(id);
        examPaperAccessControl.assertCanManage(existing, auth);
        String visibility = activityTargetService.normalizeAndValidate(
                id, existing.getCourseId(), targets.getVisibility(), targets.getTeachingClassIds(), true);
        ExamPaper patch = new ExamPaper();
        patch.setId(id);
        patch.setVisibility(visibility);
        examPaperService.updateById(patch);
        activityTargetService.replaceExamPaperTargets(id, visibility, targets.getTeachingClassIds());
        return ApiResponse.success("设置成功", buildTargetsPayload(visibility,
                activityTargetService.examPaperTargetClassIds(id)));
    }

    private ExamPaper requirePaper(Long id) {
        ExamPaper paper = examPaperService.getById(id);
        if (paper == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "试卷不存在：" + id);
        }
        return paper;
    }

    private static Map<String, Object> buildTargetsPayload(String visibility, List<Long> teachingClassIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("visibility", visibility);
        payload.put("teachingClassIds", teachingClassIds);
        return payload;
    }
}
