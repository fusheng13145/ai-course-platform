package com.aicourse.service.impl;

import com.aicourse.entity.RolePermission;
import com.aicourse.mapper.RolePermissionMapper;
import com.aicourse.service.PermissionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class PermissionServiceImpl extends ServiceImpl<RolePermissionMapper, RolePermission> implements PermissionService {
}