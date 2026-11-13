package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.dto.SemesterCreateDTO;
import com.aicourse.entity.Semester;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.SemesterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学期管理接口控制器（仅管理员）。
 * 角色矩阵「① 创建学期 / ② 归档过期学期」的 REST 落点，基础路径 {@code /api/admin/semesters}。
 *
 * <p>⚠️ 如实边界：本控制器只管学期这一治理维度，**不改动任何课程归属**——
 * {@code courses.semester} 仍是自由文本列，未与本表建立外键（见 V9 迁移注释）。</p>
 */
@RestController
@RequestMapping("/admin/semesters")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "学期管理", description = "创建学期、归档单个/全部过期学期")
public class SemesterController {

    private final SemesterService semesterService;
    private final AdminScopeGuard adminScopeGuard;

    public SemesterController(SemesterService semesterService, AdminScopeGuard adminScopeGuard) {
        this.semesterService = semesterService;
        this.adminScopeGuard = adminScopeGuard;
    }

    /**
     * 学期列表（可按状态过滤）。10A：响应为 {@link SemesterVO}，附带现场推导的展示别名
     * （别名不落库，改名即随动）；既有 {@code name} 字段保持规范名不变，前端渐进切换。
     *
     * @param status ACTIVE / ARCHIVED（省略为全部，大小写不敏感）
     */
    @GetMapping
    @Operation(summary = "学期列表", description = "在册在前、其后按开始日期倒序；可按 status 过滤；含 10A 展示别名")
    public ApiResponse<List<com.aicourse.vo.SemesterVO>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(semesterService.listByStatus(status).stream()
                .map(com.aicourse.vo.SemesterVO::from).toList());
    }

    /**
     * 创建学期（学期名唯一）。
     */
    @PostMapping
    @Operation(summary = "创建学期", description = "学期名唯一；结束日期不得早于开始日期")
    public ApiResponse<Semester> create(@Valid @RequestBody SemesterCreateDTO body) {
        adminScopeGuard.assertSchoolOnly();
        return ApiResponse.success("学期创建成功",
                semesterService.create(body.getName(), body.getStartDate(), body.getEndDate()));
    }

    /**
     * 归档指定学期（幂等）。
     */
    @PostMapping("/{id}/archive")
    @Operation(summary = "归档学期", description = "幂等：已归档再次调用返回 archived=false，不覆盖原归档时间")
    public ApiResponse<Map<String, Object>> archive(@PathVariable Long id) {
        adminScopeGuard.assertSchoolOnly();
        boolean changed = semesterService.archive(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("archived", changed);
        data.put("message", changed ? "已归档" : "该学期已处于归档状态，未重复变更");
        return ApiResponse.success(data);
    }

    /**
     * 批量归档「已过期」的在册学期（结束日期早于今天）。
     */
    @PostMapping("/archive-expired")
    @Operation(summary = "归档过期学期", description = "归档所有 状态=ACTIVE 且 结束日期<今天 的学期，返回本次条数")
    public ApiResponse<Map<String, Object>> archiveExpired() {
        adminScopeGuard.assertSchoolOnly();
        int archived = semesterService.archiveExpired(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("archivedCount", archived);
        data.put("message", "本次归档 " + archived + " 个过期学期");
        return ApiResponse.success(data);
    }
}
