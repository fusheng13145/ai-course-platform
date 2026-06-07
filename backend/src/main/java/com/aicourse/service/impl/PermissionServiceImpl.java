package com.aicourse.service.impl;

import com.aicourse.entity.RolePermission;
import com.aicourse.entity.User;
import com.aicourse.mapper.RolePermissionMapper;
import com.aicourse.service.PermissionService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PermissionServiceImpl extends ServiceImpl<RolePermissionMapper, RolePermission> implements PermissionService {
    private final UserService userService;

    public PermissionServiceImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Map<String, Object> getUserPermissions(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("permissions", List.of());
            empty.put("menus", List.of());
            return empty;
        }
        List<RolePermission> perms = getRolePermissions(user.getRole());
        Map<String, Object> result = new HashMap<>();
        result.put("permissions", perms);
        result.put("menus", perms.stream()
            .filter(p -> "MENU".equals(p.getPermissionType()))
            .map(RolePermission::getPermissionKey)
            .collect(Collectors.toList()));
        return result;
    }

    @Override
    public List<RolePermission> getRolePermissions(String role) {
        return list(new LambdaQueryWrapper<RolePermission>()
            .eq(RolePermission::getRole, role)
            .eq(RolePermission::getIsActive, true));
    }
}
