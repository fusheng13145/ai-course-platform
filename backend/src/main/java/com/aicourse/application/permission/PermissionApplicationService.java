package com.aicourse.application.permission;

import com.aicourse.common.AdminScope;
import com.aicourse.common.ApiCode;
import com.aicourse.dto.RolePermissionSaveDTO;
import com.aicourse.entity.RolePermission;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.PermissionService;
import com.aicourse.vo.RolePermissionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色权限管理应用服务（3E-05E）。
 *
 * <p>从 {@code PermissionController} 下移 2 处 {@code LambdaQueryWrapper} 与全部写路径编排：</p>
 * <ul>
 *   <li><b>读</b>：院系管理员只能看到 {@code ALL} / {@code COLLEGE} 作用域的权限项；校级（含存量
 *       无作用域 ADMIN）看全量 —— 这是一条「按作用域收窄」的读权限口径；</li>
 *   <li><b>写</b>：一律 {@code assertSchoolOnly()}（院系管理员不可改权限表，防自我提权）；</li>
 *   <li><b>作用域规范化</b>：空值按 {@code ALL}，只接受 ALL/SCHOOL/COLLEGE，其余 400。</li>
 * </ul>
 */
@Service
public class PermissionApplicationService {

    private final PermissionService permissionService;
    private final AdminScopeGuard adminScopeGuard;

    public PermissionApplicationService(PermissionService permissionService,
                                        AdminScopeGuard adminScopeGuard) {
        this.permissionService = permissionService;
        this.adminScopeGuard = adminScopeGuard;
    }

    /** 按角色查询（角色名统一大写，避免大小写绕过收窄口径）。 */
    public List<RolePermissionVO> listByRole(String role) {
        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRole, role.toUpperCase());
        applyScopeFilter(wrapper);
        return RolePermissionVO.from(permissionService.list(wrapper));
    }

    /** 全部权限配置（院系管理员已按作用域收窄）。 */
    public List<RolePermissionVO> listAll() {
        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        applyScopeFilter(wrapper);
        return RolePermissionVO.from(permissionService.list(wrapper));
    }

    /** 新增权限配置（仅校级）。 */
    public RolePermissionVO create(RolePermissionSaveDTO dto) {
        RolePermission permission = dto.toEntity();
        adminScopeGuard.assertSchoolOnly();
        normalizeScope(permission);
        permissionService.save(permission);
        return RolePermissionVO.from(permission);
    }

    /** 修改权限配置（仅校级；未带 scope 时不改写作用域）。 */
    public RolePermissionVO update(Long id, RolePermissionSaveDTO dto) {
        RolePermission permission = dto.toEntity();
        adminScopeGuard.assertSchoolOnly();
        if (permission.getScope() != null) {
            normalizeScope(permission);
        }
        permission.setId(id);
        permissionService.updateById(permission);
        return RolePermissionVO.from(permissionService.getById(id));
    }

    /** 删除权限配置（仅校级）。 */
    public void delete(Long id) {
        adminScopeGuard.assertSchoolOnly();
        permissionService.removeById(id);
    }

    /** 切换启用状态（仅校级；记录不存在时返回 null，保持原响应形状）。 */
    public RolePermissionVO toggle(Long id) {
        adminScopeGuard.assertSchoolOnly();
        RolePermission permission = permissionService.getById(id);
        if (permission != null) {
            permission.setIsActive(!Boolean.TRUE.equals(permission.getIsActive()));
            permissionService.updateById(permission);
        }
        return permission == null ? null : RolePermissionVO.from(permission);
    }

    /** 院系管理员只能看到 ALL 与 COLLEGE 作用域；校级（含存量无作用域 ADMIN）保持全量。 */
    private void applyScopeFilter(LambdaQueryWrapper<RolePermission> wrapper) {
        if (AdminScope.COLLEGE.equals(adminScopeGuard.currentAdminScope())) {
            wrapper.in(RolePermission::getScope, List.of(AdminScope.ALL, AdminScope.COLLEGE));
        }
    }

    /** 规范化并校验作用域：空值按 ALL；只接受 ALL/SCHOOL/COLLEGE。 */
    private void normalizeScope(RolePermission permission) {
        if (permission.getScope() == null || permission.getScope().isBlank()) {
            permission.setScope(AdminScope.ALL);
            return;
        }
        String normalized = AdminScope.normalize(permission.getScope());
        if (!AdminScope.ALL.equals(normalized) && !AdminScope.isValid(normalized)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "作用域只能是 ALL、SCHOOL 或 COLLEGE");
        }
        permission.setScope(normalized);
    }
}
