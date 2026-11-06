package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.dto.OrgAdminClassDTO;
import com.aicourse.dto.OrgCollegeDTO;
import com.aicourse.dto.OrgMajorDTO;
import com.aicourse.entity.AdminClass;
import com.aicourse.entity.College;
import com.aicourse.entity.Major;
import com.aicourse.service.OrgService;
import com.aicourse.vo.OrgTreeNodeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 组织架构接口控制器（V12 / B-12-P-08 WP1；V14 / WP4 增加组织表 CRUD）。
 *
 * <p>写操作作用域：学院仅校级管理员；专业/行政班院系管理员仅限本院（服务层校验）。
 * 删除语义为归档（status=ARCHIVED）。</p>
 */
@RestController
@RequestMapping("/admin/org")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "组织架构", description = "学校/学院/专业/行政班组织树与 CRUD")
public class AdminOrgController {

    private final OrgService orgService;

    public AdminOrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    /**
     * 获取组织树（校级管理员全树；院系管理员仅本学院）。
     */
    @GetMapping("/tree")
    @Operation(summary = "组织树", description = "按作用域返回学校→学院→专业→行政班")
    public ApiResponse<List<OrgTreeNodeVO>> getTree() {
        return ApiResponse.success(orgService.getTree());
    }

    // ===================== 学院 =====================

    @GetMapping("/colleges")
    @Operation(summary = "学院列表", description = "院系管理员仅本院")
    public ApiResponse<List<College>> listColleges() {
        return ApiResponse.success(orgService.listColleges());
    }

    @PostMapping("/colleges")
    @Operation(summary = "新建学院", description = "仅校级管理员；编码唯一")
    public ApiResponse<College> createCollege(@Valid @RequestBody OrgCollegeDTO body) {
        return ApiResponse.success("学院创建成功", orgService.createCollege(body));
    }

    @PutMapping("/colleges/{id}")
    @Operation(summary = "修改学院", description = "仅校级管理员")
    public ApiResponse<College> updateCollege(@PathVariable Long id,
                                              @Valid @RequestBody OrgCollegeDTO body) {
        return ApiResponse.success("学院已更新", orgService.updateCollege(id, body));
    }

    @DeleteMapping("/colleges/{id}")
    @Operation(summary = "归档学院", description = "仅校级管理员；仍有未归档专业时拒绝")
    public ApiResponse<Void> archiveCollege(@PathVariable Long id) {
        orgService.archiveCollege(id);
        return ApiResponse.success("学院已归档", null);
    }

    // ===================== 专业 =====================

    @GetMapping("/majors")
    @Operation(summary = "专业列表", description = "可按学院过滤；院系管理员仅本院")
    public ApiResponse<List<Major>> listMajors(@RequestParam(required = false) Long collegeId) {
        return ApiResponse.success(orgService.listMajors(collegeId));
    }

    @PostMapping("/majors")
    @Operation(summary = "新建专业", description = "院系管理员仅本院")
    public ApiResponse<Major> createMajor(@Valid @RequestBody OrgMajorDTO body) {
        return ApiResponse.success("专业创建成功", orgService.createMajor(body));
    }

    @PutMapping("/majors/{id}")
    @Operation(summary = "修改专业", description = "仅调整编码/名称/状态，不可改所属学院")
    public ApiResponse<Major> updateMajor(@PathVariable Long id,
                                          @Valid @RequestBody OrgMajorDTO body) {
        return ApiResponse.success("专业已更新", orgService.updateMajor(id, body));
    }

    @DeleteMapping("/majors/{id}")
    @Operation(summary = "归档专业", description = "仍有未归档行政班时拒绝")
    public ApiResponse<Void> archiveMajor(@PathVariable Long id) {
        orgService.archiveMajor(id);
        return ApiResponse.success("专业已归档", null);
    }

    // ===================== 行政班 =====================

    @GetMapping("/admin-classes")
    @Operation(summary = "行政班列表", description = "可按学院/专业过滤；院系管理员仅本院")
    public ApiResponse<List<AdminClass>> listAdminClasses(
            @RequestParam(required = false) Long collegeId,
            @RequestParam(required = false) Long majorId) {
        return ApiResponse.success(orgService.listAdminClasses(collegeId, majorId));
    }

    @PostMapping("/admin-classes")
    @Operation(summary = "新建行政班", description = "院系管理员仅本院")
    public ApiResponse<AdminClass> createAdminClass(@Valid @RequestBody OrgAdminClassDTO body) {
        return ApiResponse.success("行政班创建成功", orgService.createAdminClass(body));
    }

    @PutMapping("/admin-classes/{id}")
    @Operation(summary = "修改行政班", description = "仅调整名称/编码/年级/班主任/容量/状态")
    public ApiResponse<AdminClass> updateAdminClass(@PathVariable Long id,
                                                    @Valid @RequestBody OrgAdminClassDTO body) {
        return ApiResponse.success("行政班已更新", orgService.updateAdminClass(id, body));
    }

    @DeleteMapping("/admin-classes/{id}")
    @Operation(summary = "归档行政班", description = "仍有学生归属时拒绝")
    public ApiResponse<Void> archiveAdminClass(@PathVariable Long id) {
        orgService.archiveAdminClass(id);
        return ApiResponse.success("行政班已归档", null);
    }
}
