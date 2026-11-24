package com.aicourse.dto;

import com.aicourse.entity.RolePermission;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 角色权限配置入参（入参收实体加固）。
 *
 * <p>{@code id} 由路径变量给出（更新时强制覆盖），不接受请求体传入 —— 否则
 * {@code PUT /update/{id}} 的 {@code setId(id)} 与请求体 {@code id} 之间会存在「谁生效」的争议面。</p>
 */
@Schema(name = "RolePermissionSaveDTO", description = "角色权限配置入参；主键由路径变量给出")
@Data
public class RolePermissionSaveDTO {

    @Schema(description = "角色标识", example = "TEACHER")
    private String role;

    @Schema(description = "权限键", example = "course:read")
    private String permissionKey;

    @Schema(description = "作用域：SCHOOL/COLLEGE/OWN", example = "OWN")
    private String scope;

    @Schema(description = "权限名称", example = "查看课程")
    private String permissionName;

    @Schema(description = "权限类型", example = "API")
    private String permissionType;

    public RolePermission toEntity() {
        RolePermission permission = new RolePermission();
        permission.setRole(role);
        permission.setPermissionKey(permissionKey);
        permission.setScope(scope);
        permission.setPermissionName(permissionName);
        permission.setPermissionType(permissionType);
        return permission;
    }
}
