package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.RolePermission;
import com.aicourse.service.PermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限管理接口控制器（仅管理员可访问）
 */
@RestController
@RequestMapping("/admin/permissions")
@PreAuthorize("hasRole('ADMIN')")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
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
        List<RolePermission> permissionList = permissionService.list(queryWrapper);
        return ApiResponse.success(permissionList);
    }

    /**
     * 查询全部权限配置
     * @return 所有权限数据
     */
    @GetMapping("/all")
    public ApiResponse<List<RolePermission>> getAll() {
        List<RolePermission> permissionList = permissionService.list();
        return ApiResponse.success(permissionList);
    }

    /**
     * 新增权限配置
     * @param permission 权限信息
     * @return 新增权限数据
     */
    @PostMapping("/create")
    public ApiResponse<RolePermission> create(@RequestBody RolePermission permission) {
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
    public ApiResponse<RolePermission> update(@PathVariable Long id, @RequestBody RolePermission permission) {
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
        RolePermission permission = permissionService.getById(id);
        if (permission != null) {
            permission.setIsActive(!permission.getIsActive());
            permissionService.updateById(permission);
        }
        return ApiResponse.success("切换成功", permission);
    }
}