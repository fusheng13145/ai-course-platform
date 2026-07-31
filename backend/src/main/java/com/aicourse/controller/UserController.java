package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.AdminCreateUserDTO;
import com.aicourse.dto.AdminUpdateUserDTO;
import com.aicourse.dto.UserUpdateDTO;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@Tag(name = "用户管理")
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
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "分页查询用户列表")
    public ApiResponse<PagedResponse<User>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword) {
        Page<User> userPage = userService.searchUsers(keyword, role, (int) page, (int) size);
        return ApiResponse.success(PagedResponse.of(userPage));
    }

    /**
     * 根据ID查询用户详情（密码已脱敏，仅允许本人或管理员）
     * @param id 用户ID
     * @return 用户信息（敏感字段脱敏）
     */
    @GetMapping("/{id}")
    @PreAuthorize("#id == authentication.principal or hasRole('ADMIN')")
    @Operation(summary = "根据ID查询用户详情")
    public ApiResponse<User> getById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user != null) {
            user.setPassword(null);
            // 非本人/管理员时脱敏邮箱和手机号
        }
        return ApiResponse.success(user);
    }

    /**
     * 获取用户详细资料（含关联查询）
     * @param id 用户ID
     * @return 用户完整信息
     */
    @GetMapping("/{id}/profile")
    @Operation(summary = "获取用户详细资料")
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
    @Operation(summary = "获取同班同学列表")
    public ApiResponse<List<User>> getClassmates(@PathVariable Long id) {
        List<User> classmates = userService.getClassmates(id);
        return ApiResponse.success(classmates);
    }

    /**
     * 获取教师的学生列表
     * @param auth 认证信息
     * @return 学生列表
     */
    @GetMapping("/teacher/students")
    @Operation(summary = "获取教师的学生列表")
    public ApiResponse<List<User>> getTeacherStudents(Authentication auth) {
        Long teacherId = (Long) auth.getPrincipal();
        List<User> students = userService.getTeacherStudents(teacherId);
        return ApiResponse.success(students);
    }

    /**
     * 获取用户统计数据
     * @param id 用户ID
     * @return 统计信息
     */
    @GetMapping("/{id}/stats")
    @Operation(summary = "获取用户统计数据")
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
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "搜索用户")
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
     * <p>安全：使用白名单 {@link AdminCreateUserDTO} 接收参数，绝不直接绑定整个 User 实体，
     * 防止通过 JSON 注入 id / is_deleted / created_by 等任意列，或写入明文密码。</p>
     * @param dto 用户信息（强类型校验）
     * @return 新增用户（密码已脱敏）
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "新增用户")
    public ApiResponse<User> create(@Valid @RequestBody AdminCreateUserDTO dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setFullName(dto.getFullName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setRole(dto.getRole());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        userService.save(user);
        user.setPassword(null);
        return ApiResponse.success("创建成功", user);
    }

    /**
     * 修改用户信息（仅管理员）
     * <p>安全：使用白名单 {@link AdminUpdateUserDTO}（不含 password / id），
     * 仅将允许变更的字段从 DTO 拷贝到已存在的实体后更新，杜绝越权赋值与明文改密。</p>
     * @param id 用户ID（主键以 URL 路径为准）
     * @param dto 待更新数据
     * @return 更新后用户（密码已脱敏）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "修改用户信息")
    public ApiResponse<User> update(@PathVariable Long id, @Valid @RequestBody AdminUpdateUserDTO dto) {
        User existing = userService.getById(id);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        if (dto.getUsername() != null) {
            existing.setUsername(dto.getUsername());
        }
        if (dto.getFullName() != null) {
            existing.setFullName(dto.getFullName());
        }
        if (dto.getPhone() != null) {
            existing.setPhone(dto.getPhone());
        }
        if (dto.getEmail() != null) {
            existing.setEmail(dto.getEmail());
        }
        if (dto.getRole() != null) {
            existing.setRole(dto.getRole());
        }
        if (dto.getIsActive() != null) {
            existing.setIsActive(dto.getIsActive());
        }
        if (dto.getAvatarUrl() != null) {
            existing.setAvatarUrl(dto.getAvatarUrl());
        }
        if (dto.getBio() != null) {
            existing.setBio(dto.getBio());
        }
        // 注意：始终以 URL 路径 id 为准，且绝不从此处写入 password 字段
        existing.setId(id);
        userService.updateById(existing);
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
    @Operation(summary = "删除用户")
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
    @Operation(summary = "切换用户启用/禁用状态")
    public ApiResponse<Void> toggleStatus(@PathVariable Long id, @Valid @RequestBody Map<String, Boolean> body) {
        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 注意：不能用 getOrDefault(key, !user.getIsActive())——默认值参数会被急切求值，
        // 当 isActive 为 null 时即使客户端显式传了 status 也会 NPE。此处惰性求值并对 null 安全取反。
        Boolean requested = body.get("status");
        boolean newStatus = (requested != null) ? requested : !Boolean.TRUE.equals(user.getIsActive());
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
    @Operation(summary = "查询当前登录用户个人信息")
    public ApiResponse<User> profile(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        User user = userService.getUserProfile(userId);
        return ApiResponse.success(user);
    }

    /**
     * 修改当前登录用户个人信息
     * <p>安全：使用白名单 {@link UserUpdateDTO}（不含 role/password/id 等敏感字段），
     * 仅将允许的字段拷贝到实体后更新，杜绝越权赋值。</p>
     * @param auth 认证信息
     * @param updates 更新字段（强类型校验）
     * @return 更新后用户信息（密码已脱敏）
     */
    @PutMapping("/profile")
    @Operation(summary = "修改当前登录用户个人信息")
    public ApiResponse<User> updateProfile(Authentication auth, @Valid @RequestBody UserUpdateDTO updates) {
        Long userId = (Long) auth.getPrincipal();
        User user = new User();
        if (updates.getFullName() != null) {
            user.setFullName(updates.getFullName());
        }
        if (updates.getPhone() != null) {
            user.setPhone(updates.getPhone());
        }
        if (updates.getEmail() != null) {
            user.setEmail(updates.getEmail());
        }
        if (updates.getAvatarUrl() != null) {
            user.setAvatarUrl(updates.getAvatarUrl());
        }
        if (updates.getBio() != null) {
            user.setBio(updates.getBio());
        }
        userService.updateProfile(userId, user);
        User updatedUser = userService.getUserProfile(userId);
        return ApiResponse.success("更新成功", updatedUser);
    }
}
