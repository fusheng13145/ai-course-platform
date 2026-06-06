package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.RolePermission;
import com.aicourse.service.PermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/permissions")
@PreAuthorize("hasRole('ADMIN')")
public class PermissionController {
    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/role/{role}")
    public ApiResponse<List<RolePermission>> getByRole(@PathVariable String role) {
        return ApiResponse.success(permissionService.list(
            new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRole, role.toUpperCase())));
    }

    @GetMapping("/all")
    public ApiResponse<List<RolePermission>> getAll() {
        return ApiResponse.success(permissionService.list());
    }

    @PostMapping("/create")
    public ApiResponse<RolePermission> create(@RequestBody RolePermission permission) {
        permissionService.save(permission);
        return ApiResponse.success("创建成功", permission);
    }

    @PutMapping("/update/{id}")
    public ApiResponse<RolePermission> update(@PathVariable Long id, @RequestBody RolePermission permission) {
        permission.setId(id);
        permissionService.updateById(permission);
        return ApiResponse.success("更新成功", permissionService.getById(id));
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    @PatchMapping("/toggle/{id}")
    public ApiResponse<RolePermission> toggleStatus(@PathVariable Long id) {
        RolePermission p = permissionService.getById(id);
        if (p != null) {
            p.setIsActive(!p.getIsActive());
            permissionService.updateById(p);
        }
        return ApiResponse.success("切换成功", p);
    }
}
