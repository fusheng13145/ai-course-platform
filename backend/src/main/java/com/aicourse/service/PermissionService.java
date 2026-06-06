package com.aicourse.service;

import com.aicourse.entity.RolePermission;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface PermissionService extends IService<RolePermission> {
    Map<String, Object> getUserPermissions(Long userId);
    List<RolePermission> getRolePermissions(String role);
}
