package com.aicourse.service;

import com.aicourse.entity.RolePermission;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 权限管理业务接口
 */
public interface PermissionService extends IService<RolePermission> {

    /**
     * 获取用户权限信息
     * @param userId 用户ID
     * @return 权限集合数据
     */
    Map<String, Object> getUserPermissions(Long userId);

    /**
     * 根据角色查询关联权限
     * @param role 角色标识
     * @return 角色权限列表
     */
    List<RolePermission> getRolePermissions(String role);
}