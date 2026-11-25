package com.aicourse.controller;

import com.aicourse.application.permission.PermissionApplicationService;
import com.aicourse.common.ApiResponse;
import com.aicourse.dto.RolePermissionSaveDTO;
import com.aicourse.vo.RolePermissionVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限管理接口控制器（仅管理员可访问）
 *
 * <p>3E-05E：作用域收窄读口径、校级写校验、作用域规范化与 2 处 wrapper 已下移到
 * {@link PermissionApplicationService}。</p>
 */
@Tag(name = "权限管理", description = "角色权限的增删改查")
@RestController
@RequestMapping("/admin/permissions")
@PreAuthorize("hasRole('ADMIN')")
public class PermissionController {

    private final PermissionApplicationService permissionApplicationService;

    public PermissionController(PermissionApplicationService permissionApplicationService) {
        this.permissionApplicationService = permissionApplicationService;
    }

    /**
     * 根据角色查询权限列表
     * @param role 角色标识
     * @return 角色对应权限集合
     */
    @GetMapping("/role/{role}")
    public ApiResponse<List<RolePermissionVO>> getByRole(@PathVariable String role) {
        return ApiResponse.success(permissionApplicationService.listByRole(role));
    }

    /**
     * 查询全部权限配置
     * @return 所有权限数据
     */
    @GetMapping("/all")
    public ApiResponse<List<RolePermissionVO>> getAll() {
        return ApiResponse.success(permissionApplicationService.listAll());
    }

    /**
     * 新增权限配置
     * @param dto 权限信息（收 DTO：不接受客户端传 id）
     * @return 新增权限数据
     */
    @PostMapping("/create")
    public ApiResponse<RolePermissionVO> create(@Valid @RequestBody RolePermissionSaveDTO dto) {
        return ApiResponse.success("创建成功", permissionApplicationService.create(dto));
    }

    /**
     * 修改权限配置
     * @param id 权限ID
     * @param dto 待更新权限数据
     * @return 更新后权限数据
     */
    @PutMapping("/update/{id}")
    public ApiResponse<RolePermissionVO> update(@PathVariable Long id,
                                                @Valid @RequestBody RolePermissionSaveDTO dto) {
        return ApiResponse.success("更新成功", permissionApplicationService.update(id, dto));
    }

    /**
     * 删除权限配置
     * @param id 权限ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionApplicationService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 切换权限启用/禁用状态
     * @param id 权限ID
     * @return 状态切换后权限数据
     */
    @PatchMapping("/toggle/{id}")
    public ApiResponse<RolePermissionVO> toggleStatus(@PathVariable Long id) {
        return ApiResponse.success("切换成功", permissionApplicationService.toggle(id));
    }
}
