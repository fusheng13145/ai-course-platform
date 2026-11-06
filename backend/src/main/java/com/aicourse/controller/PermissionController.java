package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.AdminScope;
import com.aicourse.entity.RolePermission;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.PermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限管理接口控制器（仅管理员可访问）
 */
@Tag(name = "权限管理", description = "角色权限的增删改查")
@RestController
@RequestMapping("/admin/permissions")
@PreAuthorize("hasRole('ADMIN')")
public class PermissionController {

    private static final Logger log = LoggerFactory.getLogger(PermissionController.class);

    private final PermissionService permissionService;
    private final AdminScopeGuard adminScopeGuard;

    public PermissionController(PermissionService permissionService, AdminScopeGuard adminScopeGuard) {
        this.permissionService = permissionService;
        this.adminScopeGuard = adminScopeGuard;
    }

    /**
     * 根据角色查询权限列表
     * @param role 角色标识
     * @return 角色对应权限集合
     */
    @GetMapping("/role/{role}")
    public ApiResponse<List<RolePermission>> getByRole(@PathVariable String role) {
        LambdaQueryWrapper<RolePermission> queryWrapper = new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRole, role.toUpperCase());
        applyScopeFilter(queryWrapper);
        List<RolePermission> permissionList = permissionService.list(queryWrapper);
        return ApiResponse.success(permissionList);
    }

    /**
     * 查询全部权限配置
     * @return 所有权限数据
     */
    @GetMapping("/all")
    public ApiResponse<List<RolePermission>> getAll() {
        LambdaQueryWrapper<RolePermission> queryWrapper = new LambdaQueryWrapper<>();
        applyScopeFilter(queryWrapper);
        List<RolePermission> permissionList = permissionService.list(queryWrapper);
        return ApiResponse.success(permissionList);
    }

    /**
     * 院系管理员只能看到 ALL 与 COLLEGE 作用域的权限项；校级（含存量无作用域 ADMIN）保持全量。
     */
    private void applyScopeFilter(LambdaQueryWrapper<RolePermission> queryWrapper) {
        if (AdminScope.COLLEGE.equals(adminScopeGuard.currentAdminScope())) {
            queryWrapper.in(RolePermission::getScope, List.of(AdminScope.ALL, AdminScope.COLLEGE));
        }
    }

    /**
     * 规范化并校验权限作用域：空值按 ALL；只接受 ALL/SCHOOL/COLLEGE。
     */
    private void normalizeScope(RolePermission permission) {
        if (permission.getScope() == null || permission.getScope().isBlank()) {
            permission.setScope(AdminScope.ALL);
            return;
        }
        String normalized = AdminScope.normalize(permission.getScope());
        if (!AdminScope.ALL.equals(normalized) && !AdminScope.isValid(normalized)) {
            throw new BusinessException(400, "作用域只能是 ALL、SCHOOL 或 COLLEGE");
        }
        permission.setScope(normalized);
    }

    /**
     * 新增权限配置
     * @param permission 权限信息
     * @return 新增权限数据
     */
    @PostMapping("/create")
    public ApiResponse<RolePermission> create(@Valid @RequestBody RolePermission permission) {
        adminScopeGuard.assertSchoolOnly();
        normalizeScope(permission);
        permissionService.save(permission);
        return ApiResponse.success("创建成功", permission);
    }

    /**
     * 修改权限配置
     * @param id 权限ID
     * @param permission 待更新权限数据
     * @return 更新后权限数据
     */
    @PutMapping("/update/{id}")
    public ApiResponse<RolePermission> update(@PathVariable Long id, @Valid @RequestBody RolePermission permission) {
        adminScopeGuard.assertSchoolOnly();
        if (permission.getScope() != null) {
            normalizeScope(permission);
        }
        permission.setId(id);
        permissionService.updateById(permission);
        RolePermission updatedPermission = permissionService.getById(id);
        return ApiResponse.success("更新成功", updatedPermission);
    }

    /**
     * 删除权限配置
     * @param id 权限ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminScopeGuard.assertSchoolOnly();
        permissionService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 切换权限启用/禁用状态
     * @param id 权限ID
     * @return 状态切换后权限数据
     */
    @PatchMapping("/toggle/{id}")
    public ApiResponse<RolePermission> toggleStatus(@PathVariable Long id) {
        adminScopeGuard.assertSchoolOnly();
        RolePermission permission = permissionService.getById(id);
        if (permission != null) {
            permission.setIsActive(!permission.getIsActive());
            permissionService.updateById(permission);
        }
        return ApiResponse.success("切换成功", permission);
    }
}