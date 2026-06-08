package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理接口控制器
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 分页查询用户列表（支持角色、关键词筛选）
     * @param page 当前页码
     * @param size 每页条数
     * @param role 用户角色（可选）
     * @param keyword 关键词（用户名/姓名/邮箱）
     * @return 分页用户数据（密码已脱敏）
     */
    @GetMapping
    public ApiResponse<PagedResponse<User>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .eq(role != null && !role.isEmpty(), User::getRole, role)
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(User::getUsername, keyword)
                        .or().like(User::getFullName, keyword)
                        .or().like(User::getEmail, keyword))
                .orderByDesc(User::getCreatedAt);
        Page<User> userPage = userService.page(Page.of(page, size), queryWrapper);
        userPage.getRecords().forEach(u -> u.setPassword(null));
        return ApiResponse.success(PagedResponse.of(userPage));
    }

    /**
     * 根据ID查询用户详情（密码已脱敏）
     * @param id 用户ID
     * @return 用户信息
     */
    @GetMapping("/{id}")
    public ApiResponse<User> getById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user != null) {
            user.setPassword(null);
        }
        return ApiResponse.success(user);
    }

    /**
     * 新增用户（仅管理员）
     * @param user 用户信息
     * @return 新增用户（密码已脱敏）
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<User> create(@RequestBody User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userService.save(user);
        user.setPassword(null);
        return ApiResponse.success("创建成功", user);
    }

    /**
     * 修改用户信息（仅管理员）
     * @param id 用户ID
     * @param user 待更新数据
     * @return 更新后用户（密码已脱敏）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<User> update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        userService.updateById(user);
        User updatedUser = userService.getById(id);
        if (updatedUser != null) {
            updatedUser.setPassword(null);
        }
        return ApiResponse.success("更新成功", updatedUser);
    }

    /**
     * 删除用户（仅管理员）
     * @param id 用户ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 切换用户启用/禁用状态（仅管理员）
     * @param id 用户ID
     * @param body 状态参数
     * @return 操作结果
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> toggleStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        boolean newStatus = body.getOrDefault("status", !user.getIsActive());
        user.setIsActive(newStatus);
        userService.updateById(user);
        return ApiResponse.success("状态已更新", null);
    }

    /**
     * 查询当前登录用户个人信息
     * @param auth 认证信息
     * @return 用户信息（密码已脱敏）
     */
    @GetMapping("/profile")
    public ApiResponse<User> profile(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(null);
        return ApiResponse.success(user);
    }

    /**
     * 修改当前登录用户个人信息
     * @param auth 认证信息
     * @param updates 更新字段
     * @return 更新后用户信息（密码已脱敏）
     */
    @PutMapping("/profile")
    public ApiResponse<User> updateProfile(Authentication auth, @RequestBody User updates) {
        Long userId = (Long) auth.getPrincipal();
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (updates.getFullName() != null) {
            user.setFullName(updates.getFullName());
        }
        if (updates.getPhone() != null) {
            user.setPhone(updates.getPhone());
        }
        if (updates.getAvatarUrl() != null) {
            user.setAvatarUrl(updates.getAvatarUrl());
        }
        if (updates.getBio() != null) {
            user.setBio(updates.getBio());
        }
        userService.updateById(user);
        User updatedUser = userService.getById(userId);
        updatedUser.setPassword(null);
        return ApiResponse.success("更新成功", updatedUser);
    }
}