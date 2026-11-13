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

    /**
     * 根据角色与管理员作用域查询关联权限（V12）。
     *
     * <p>有效范围 = {@code ALL} ∪ 自身作用域：校级管理员取 ALL+SCHOOL，
     * 院系管理员取 ALL+COLLEGE，非管理员只取 ALL。旧数据回填 ALL 后，
     * 在尚无 SCHOOL/COLLEGE 权限项时结果与单参方法等价。</p>
     *
     * @param role 角色标识
     * @param scope 管理员作用域（SCHOOL/COLLEGE；null 或 ALL 表示只取 ALL）
     * @return 角色权限列表
     */
    List<RolePermission> getRolePermissions(String role, String scope);
}