package com.aicourse.service.impl;

import com.aicourse.entity.RolePermission;
import com.aicourse.entity.User;
import com.aicourse.mapper.RolePermissionMapper;
import com.aicourse.service.PermissionService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限服务实现类
 * 提供用户权限、角色权限查询及菜单权限筛选功能
 */
@Service
public class PermissionServiceImpl extends ServiceImpl<RolePermissionMapper, RolePermission> implements PermissionService {

    private static final String CACHE_NAME = "permissions";

    private final UserService userService;

    public PermissionServiceImpl(UserService userService) {
        this.userService = userService;
    }

    /**
     * 获取用户权限集合（按钮权限 + 菜单权限）
     * @param userId 用户ID
     * @return 权限与菜单标识集合
     */
    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "#userId")
    public Map<String, Object> getUserPermissions(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            Map<String, Object> emptyMap = new HashMap<>(2);
            emptyMap.put("permissions", List.of());
            emptyMap.put("menus", List.of());
            return emptyMap;
        }

        List<RolePermission> rolePermissionList = getRolePermissions(user.getRole());
        List<String> menuKeyList = rolePermissionList.stream()
                .filter(perm -> "MENU".equals(perm.getPermissionType()))
                .map(RolePermission::getPermissionKey)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>(2);
        result.put("permissions", rolePermissionList);
        result.put("menus", menuKeyList);
        return result;
    }

    /**
     * 根据角色查询已启用的权限列表
     * @param role 角色标识
     * @return 权限集合
     */
    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'role:' + #role")
    public List<RolePermission> getRolePermissions(String role) {
        return list(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRole, role)
                .eq(RolePermission::getIsActive, Boolean.TRUE));
    }

    // ===== IService 写操作覆写，角色权限变更时清除相关缓存 =====

    @Override
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public boolean save(RolePermission entity) {
        return super.save(entity);
    }

    @Override
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public boolean updateById(RolePermission entity) {
        return super.updateById(entity);
    }

    @Override
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }
}
