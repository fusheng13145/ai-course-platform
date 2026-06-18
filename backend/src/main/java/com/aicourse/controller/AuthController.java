package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.User;
import com.aicourse.service.AuthService;
import com.aicourse.service.PermissionService;
import com.aicourse.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证授权控制器
 * 提供登录、注册、令牌刷新、密码管理、个人信息、权限查询等接口
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final PermissionService permissionService;
    private final UserService userService;

    public AuthController(AuthService authService,
                          PermissionService permissionService,
                          UserService userService) {
        this.authService = authService;
        this.permissionService = permissionService;
        this.userService = userService;
    }

    /**
     * 账号密码登录
     * @param body 登录参数：account、password、rememberMe
     * @param request 请求对象
     * @return 登录令牌及用户信息
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, Object> body,
                                                  HttpServletRequest request) {
        String account = (String) body.get("account");
        String password = (String) body.get("password");
        Boolean rememberMe = (Boolean) body.getOrDefault("rememberMe", false);
        return authService.login(account, password, rememberMe, request);
    }

    /**
     * 用户注册
     * @param body 注册参数
     * @param request 请求对象
     * @return 注册结果
     */
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        String email = (String) body.get("email");
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        String fullName = (String) body.get("fullName");
        String role = (String) body.get("role");
        String phone = (String) body.get("phone");
        String code = (String) body.get("code");
        return authService.register(email, username, password, fullName, role, phone, code, request);
    }

    /**
     * 刷新访问令牌
     * @param body 携带刷新令牌参数
     * @return 新令牌信息
     */
    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refreshToken(@RequestBody Map<String, Object> body) {
        String refreshToken = (String) body.get("refreshToken");
        return authService.refreshToken(refreshToken);
    }

    /**
     * 退出登录
     * @param auth 登录认证信息
     * @param request 请求对象
     * @return 操作结果
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication auth, HttpServletRequest request) {
        Long userId = (Long) auth.getPrincipal();
        return authService.logout(userId, request);
    }

    /**
     * 修改登录密码
     * @param auth 登录认证信息
     * @param body 新旧密码参数
     * @return 操作结果
     */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(Authentication auth, @RequestBody Map<String, Object> body) {
        Long userId = (Long) auth.getPrincipal();
        String oldPassword = (String) body.get("oldPassword");
        String newPassword = (String) body.get("newPassword");
        return authService.changePassword(userId, oldPassword, newPassword);
    }

    /**
     * 密码修改备用接口
     * @param auth 登录认证信息
     * @param body 新旧密码参数
     * @return 操作结果
     */
    @PutMapping("/password")
    public ApiResponse<Void> altChangePassword(Authentication auth, @RequestBody Map<String, Object> body) {
        return changePassword(auth, body);
    }

    /**
     * 手机号/邮箱 + 验证码 重置密码
     * 需求文档: POST /api/auth/reset-password
     * @param body 手机号或邮箱、验证码、新密码
     * @return 操作结果
     */
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@RequestBody Map<String, Object> body) {
        String email = (String) body.get("email");
        String phone = (String) body.get("phone");
        String code = (String) body.get("code");
        String newPassword = (String) body.get("newPassword");
        if (email != null && !email.isEmpty()) {
            return authService.resetPasswordByEmail(email, code, newPassword);
        }
        if (phone != null && !phone.isEmpty()) {
            return authService.resetPassword(phone, code, newPassword);
        }
        return ApiResponse.error(400, "请提供邮箱或手机号");
    }

    /**
     * 获取当前登录用户信息
     * @param auth 登录认证信息
     * @return 用户基础信息
     */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> getCurrentUser(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return authService.getCurrentUser(userId);
    }

    /**
     * 更新当前用户个人资料
     * @param auth 登录认证信息
     * @param updates 待更新用户字段
     * @return 更新后的用户信息（密码已脱敏）
     */
    @PutMapping("/me")
    public ApiResponse<User> updateCurrentUser(Authentication auth, @RequestBody User updates) {
        Long userId = (Long) auth.getPrincipal();
        User user = userService.getById(userId);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }

        // 按需更新非空字段
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
        // 密码脱敏，禁止返回
        updatedUser.setPassword(null);
        return ApiResponse.success("个人资料更新成功", updatedUser);
    }

    /**
     * 获取当前用户权限列表
     * @param auth 登录认证信息
     * @return 权限集合
     */
    @GetMapping("/permissions")
    public ApiResponse<Map<String, Object>> getPermissions(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(permissionService.getUserPermissions(userId));
    }

    /**
     * 发送邮箱验证码
     * @param body 邮箱、验证码类型
     * @param request 请求对象
     * @return 操作结果
     */
    @PostMapping("/send-code")
    public ApiResponse<Void> sendVerificationCode(@RequestBody Map<String, Object> body,
                                                  HttpServletRequest request) {
        String email = (String) body.get("email");
        String type = (String) body.getOrDefault("type", "FORGOT_PASSWORD");
        return authService.sendVerificationCode(email, type, request);
    }

    /**
     * 邮箱 + 验证码 重置密码
     * @param body 邮箱、验证码、新密码
     * @return 操作结果
     */
    @PostMapping("/reset-password-by-email")
    public ApiResponse<Void> resetPasswordByEmail(@RequestBody Map<String, Object> body) {
        String email = (String) body.get("email");
        String code = (String) body.get("code");
        String newPassword = (String) body.get("newPassword");
        return authService.resetPasswordByEmail(email, code, newPassword);
    }
}