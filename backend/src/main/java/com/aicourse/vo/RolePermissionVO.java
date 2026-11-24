package com.aicourse.vo;

import com.aicourse.entity.RolePermission;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色权限出参。
 *
 * <p>字段与 {@link RolePermission} 一一对应、零删减。该表是系统配置数据，
 * 端点全部带 `hasRole('ADMIN')`，不存在学生侧可见性差异。
 */
@Data
public class RolePermissionVO {

    private Long id;
    private String role;
    private String permissionKey;
    private String scope;
    private String permissionName;
    private String permissionType;
    private String resourcePath;
    private Long parentId;
    private Integer sortOrder;
    private Boolean isActive;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 实体 → 出参。 */
    public static RolePermissionVO from(RolePermission permission) {
        if (permission == null) {
            return null;
        }
        RolePermissionVO vo = new RolePermissionVO();
        vo.setId(permission.getId());
        vo.setRole(permission.getRole());
        vo.setPermissionKey(permission.getPermissionKey());
        vo.setScope(permission.getScope());
        vo.setPermissionName(permission.getPermissionName());
        vo.setPermissionType(permission.getPermissionType());
        vo.setResourcePath(permission.getResourcePath());
        vo.setParentId(permission.getParentId());
        vo.setSortOrder(permission.getSortOrder());
        vo.setIsActive(permission.getIsActive());
        vo.setDescription(permission.getDescription());
        vo.setCreatedAt(permission.getCreatedAt());
        vo.setUpdatedAt(permission.getUpdatedAt());
        return vo;
    }

    /** 批量转换。 */
    public static List<RolePermissionVO> from(List<RolePermission> permissions) {
        return permissions == null ? List.of() : permissions.stream().map(RolePermissionVO::from).toList();
    }
}
