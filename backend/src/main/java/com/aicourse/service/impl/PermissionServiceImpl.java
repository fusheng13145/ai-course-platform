package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(PermissionServiceImpl.class);

    private static final String CACHE_NAME = "permissions";

    private final UserService userService;

    public PermissionServiceImpl(UserService userService) {
        this.userService = userService;
    }

    /**
     * 获取用户权限集合（按钮权限 + 菜单权限）。
     *
     * <p>响应形状契约（2026-10-16 修复）：前端 stores/auth 消费三个字段——
     * {@code role}（服务端权威角色，压过可篡改的本地 user.role）、
     * {@code menus}（MENU 类型权限键列表）、{@code buttons}（BUTTON 类型扁平化为
     * {@code {key: true}} 映射）。原实现只回传 {@code permissions}/{@code menus}，
     * 导致前端 serverRole 回退与 hasButtonPermission 整套机制从未真正生效。</p>
     *
     * @param userId 用户ID
     * @return 权限与菜单标识集合
     */
    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "#userId")
    public Map<String, Object> getUserPermissions(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            Map<String, Object> emptyMap = new HashMap<>(4);
            emptyMap.put("role", "");
            emptyMap.put("permissions", List.of());
            emptyMap.put("menus", List.of());
            emptyMap.put("buttons", Map.of());
            return emptyMap;
        }

        List<RolePermission> rolePermissionList = getRolePermissions(user.getRole());
        List<String> menuKeyList = rolePermissionList.stream()
                .filter(perm -> "MENU".equals(perm.getPermissionType()))
                .map(RolePermission::getPermissionKey)
                .collect(Collectors.toList());
        // BUTTON 类型扁平化为 {key: true}：与前端 hasButtonPermission 的读取形状对齐。
        // 过滤 null 键——Collectors.toMap 拒绝 null value，脏数据不应让整个响应 500。
        Map<String, Boolean> buttonKeyMap = rolePermissionList.stream()
                .filter(perm -> "BUTTON".equals(perm.getPermissionType())
                        && perm.getPermissionKey() != null)
                .collect(Collectors.toMap(RolePermission::getPermissionKey, perm -> Boolean.TRUE, (a, b) -> a));

        Map<String, Object> result = new HashMap<>(4);
        // 服务端回传角色：前端 effectiveRole 以此压过本地可篡改的 user.role
        result.put("role", user.getRole());
        result.put("permissions", rolePermissionList);
        result.put("menus", menuKeyList);
        result.put("buttons", buttonKeyMap);
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
        log.info("保存权限");

        return super.save(entity);
    }


    /** 更新权限 */
    @Override
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public boolean updateById(RolePermission entity) {
        log.info("更新权限");

        return super.updateById(entity);
    }


    /** 移除权限 */
    @Override
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public boolean removeById(Serializable id) {
        log.info("移除权限");

        return super.removeById(id);
    }
}
