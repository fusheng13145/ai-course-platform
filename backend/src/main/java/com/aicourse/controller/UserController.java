package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理接口控制器
 * 提供用户CRUD、资料管理、班级同学、统计、搜索等RESTful API
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
        Page<User> userPage = userService.searchUsers(keyword, role, (int) page, (int) size);
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
     * 获取用户详细资料（含关联查询）
     * @param id 用户ID
     * @return 用户完整信息
     */
    @GetMapping("/{id}/profile")
    public ApiResponse<User> getProfile(@PathVariable Long id) {
        User user = userService.getUserProfile(id);
        return ApiResponse.success(user);
    }

    /**
     * 获取同班同学列表
     * @param id 用户ID
     * @return 同班同学列表
     */
    @GetMapping("/{id}/classmates")
    public ApiResponse<List<User>> getClassmates(@PathVariable Long id) {
        List<User> classmates = userService.getClassmates(id);
        return ApiResponse.success(classmates);
    }

    /**
     * 获取教师的学生列表
     * @param teacherId 教师ID
     * @return 学生列表
     */
    @GetMapping("/teacher/students")
    public ApiResponse<List<User>> getTeacherStudents(@RequestParam Long teacherId) {
        List<User> students = userService.getTeacherStudents(teacherId);
        return ApiResponse.success(students);
    }

    /**
     * 获取用户统计数据
     * @param id 用户ID
     * @return 统计信息
     */
    @GetMapping("/{id}/stats")
    public ApiResponse<Map<String, Object>> getUserStats(@PathVariable Long id) {
        Map<String, Object> stats = userService.getUserStats(id);
        return ApiResponse.success(stats);
    }

    /**
     * 搜索用户
     * @param keyword 关键词
     * @param role 角色筛选（可选）
     * @param page 页码
     * @param size 每页大小
     * @return 分页搜索结果
     */
    @GetMapping("/search")
    public ApiResponse<PagedResponse<User>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<User> result = userService.searchUsers(keyword, role, page, size);
        return ApiResponse.success(PagedResponse.of(result));
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
        User user = userService.getUserProfile(userId);
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
        userService.updateProfile(userId, updates);
        User updatedUser = userService.getUserProfile(userId);
        return ApiResponse.success("更新成功", updatedUser);
    }
}
