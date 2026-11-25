package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.security.AccessSupport;
import com.aicourse.common.ApiResponse;
import com.aicourse.dto.CourseDefinitionSaveDTO;
import com.aicourse.vo.CurriculumVOs;
import com.aicourse.entity.CourseDefinition;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.CourseDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程库接口：稳定课程定义（Course 层）。
 */
@RestController
@RequestMapping("/course-definitions")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
@Tag(name = "课程库", description = "跨学期稳定的课程定义")
public class CourseDefinitionController {

    private final CourseDefinitionService definitionService;
    private final AdminScopeGuard adminScopeGuard;
    private final com.aicourse.service.UserService userService;

    public CourseDefinitionController(CourseDefinitionService definitionService,
                                      AdminScopeGuard adminScopeGuard,
                                      com.aicourse.service.UserService userService) {
        this.definitionService = definitionService;
        this.adminScopeGuard = adminScopeGuard;
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "课程库列表", description = "支持编号/名称关键词、学院、状态筛选")
    public ApiResponse<List<CurriculumVOs.CourseDefinitionVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long collegeId,
            @RequestParam(required = false) String status,
            Authentication auth) {
        Long scopedCollegeId = collegeId;
        if (adminScopeGuard.isCollegeAdmin()) {
            scopedCollegeId = adminScopeGuard.currentCollegeId();
        } else if (!AccessSupport.isAdmin(auth)) {
            // 教师维护本人学院课程库；跨学院定义不在开课候选内。
            com.aicourse.entity.User current =
                    userService.getById((Long) auth.getPrincipal());
            if (current == null || current.getCollegeId() == null) {
                scopedCollegeId = -1L;
            } else {
                scopedCollegeId = current.getCollegeId();
            }
        }
        return ApiResponse.success(CurriculumVOs.CourseDefinitionVO.from(
                definitionService.search(keyword, scopedCollegeId, status)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "课程库详情")
    public ApiResponse<CurriculumVOs.CourseDefinitionVO> detail(@PathVariable Long id) {
        CourseDefinition definition = definitionService.getById(id);
        if (definition == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程定义不存在");
        }
        adminScopeGuard.assertCollegeAccess(definition.getCollegeId());
        return ApiResponse.success(CurriculumVOs.CourseDefinitionVO.from(definition));
    }

    @PostMapping
    @Operation(summary = "新增课程定义", description = "教师新增到本人学院课程库；管理员可指定学院。"
            + "入参只接受课程库业务字段，id/createdBy/时间戳等由服务端填写。")
    public ApiResponse<CurriculumVOs.CourseDefinitionVO> create(@Valid @RequestBody CourseDefinitionSaveDTO dto,
                                                Authentication auth) {
        Long operatorId = (Long) auth.getPrincipal();
        boolean admin = AccessSupport.isAdmin(auth);
        CourseDefinition definition = dto.toEntity();
        if (admin) {
            if (adminScopeGuard.isCollegeAdmin()) {
                if (definition.getCollegeId() == null) {
                    definition.setCollegeId(adminScopeGuard.currentCollegeId());
                }
                adminScopeGuard.assertCollegeAccess(definition.getCollegeId());
            }
        }
        return ApiResponse.success("课程定义已创建",
                CurriculumVOs.CourseDefinitionVO.from(definitionService.create(definition, operatorId, admin)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新课程定义", description = "创建者或管理员可改；已开课任务保留创建时快照。"
            + "入参只接受课程库业务字段（null 表示不改），id/createdBy 恒取库内值。")
    public ApiResponse<CurriculumVOs.CourseDefinitionVO> update(@PathVariable Long id,
                                                @Valid @RequestBody CourseDefinitionSaveDTO dto,
                                                Authentication auth) {
        Long operatorId = (Long) auth.getPrincipal();
        boolean admin = AccessSupport.isAdmin(auth);
        CourseDefinition patch = dto.toEntity();
        CourseDefinition existing = definitionService.getById(id);
        if (existing == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程定义不存在");
        }
        adminScopeGuard.assertCollegeAccess(existing.getCollegeId());
        return ApiResponse.success("课程定义已更新",
                CurriculumVOs.CourseDefinitionVO.from(definitionService.update(id, patch, operatorId, admin)));
    }

}


