package com.aicourse.controller;

import jakarta.validation.Valid;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.GradeItem;
import com.aicourse.entity.GradePolicy;
import com.aicourse.service.GradeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 过程性成绩（多维加权）接口
 * 基础路径：/api/grade
 */
@RestController
@RequestMapping("/grade")
@Tag(name = "成绩管理")
public class GradeController {

    private final GradeService gradeService;

    public GradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @PostMapping("/policy")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<GradePolicy> savePolicy(@Valid @RequestBody GradePolicy policy, Authentication auth) {
        return ApiResponse.success("方案已保存",
                gradeService.savePolicy(policy, (Long) auth.getPrincipal()));
    }

    @GetMapping("/policy/course/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<GradePolicy> getPolicy(@PathVariable Long courseId) {
        return ApiResponse.success(gradeService.getPolicy(courseId));
    }

    @PostMapping("/item")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<GradeItem> upsertItem(@Valid @RequestBody GradeItem item, Authentication auth) {
        Long uid = (Long) auth.getPrincipal();
        return ApiResponse.success("成绩已保存",
                gradeService.upsertItem(item.getPolicyId(), item.getStudentId(), item.getComponentKey(),
                        item.getScore(), item.getRawScore(), item.getSourceId(), uid, item.getRemark()));
    }

    @GetMapping("/final/{policyId}/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Map<String, Object>> computeFinal(@PathVariable Long policyId,
                                                          @PathVariable Long studentId) {
        return ApiResponse.success(gradeService.computeFinal(policyId, studentId));
    }

    @GetMapping("/report/course/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<Map<String, Object>>> courseReport(@PathVariable Long courseId) {
        return ApiResponse.success(gradeService.courseReport(courseId));
    }
}
