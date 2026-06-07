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

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final PermissionService permissionService;
    private final UserService userService;

    public AuthController(AuthService authService, PermissionService permissionService, UserService userService) {
        this.authService = authService;
        this.permissionService = permissionService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String account = (String) body.get("account");
        String password = (String) body.get("password");
        Boolean rememberMe = (Boolean) body.getOrDefault("rememberMe", false);
        return authService.login(account, password, rememberMe, request);
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return authService.register(
            (String) body.get("email"), (String) body.get("username"),
            (String) body.get("password"), (String) body.get("fullName"),
            (String) body.get("role"), (String) body.get("phone"),
            (String) body.get("code"), request);
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refreshToken(@RequestBody Map<String, Object> body) {
        return authService.refreshToken((String) body.get("refreshToken"));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication auth, HttpServletRequest request) {
        Long userId = (Long) auth.getPrincipal();
        return authService.logout(userId, request);
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(Authentication auth, @RequestBody Map<String, Object> body) {
        Long userId = (Long) auth.getPrincipal();
        return authService.changePassword(userId,
            (String) body.get("oldPassword"), (String) body.get("newPassword"));
    }

    @PutMapping("/password")
    public ApiResponse<Void> altChangePassword(Authentication auth, @RequestBody Map<String, Object> body) {
        return changePassword(auth, body);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@RequestBody Map<String, Object> body) {
        return authService.resetPassword(
            (String) body.get("phone"), (String) body.get("code"),
            (String) body.get("newPassword"));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> getCurrentUser(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return authService.getCurrentUser(userId);
    }

    @PutMapping("/me")
    public ApiResponse<User> updateCurrentUser(Authentication auth, @RequestBody User updates) {
        Long userId = (Long) auth.getPrincipal();
        User user = userService.getById(userId);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        if (updates.getFullName() != null) user.setFullName(updates.getFullName());
        if (updates.getPhone() != null) user.setPhone(updates.getPhone());
        if (updates.getAvatarUrl() != null) user.setAvatarUrl(updates.getAvatarUrl());
        if (updates.getBio() != null) user.setBio(updates.getBio());
        userService.updateById(user);
        User updated = userService.getById(userId);
        updated.setPassword(null);
        return ApiResponse.success("更新成功", updated);
    }

    @GetMapping("/permissions")
    public ApiResponse<Map<String, Object>> getPermissions(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("获取成功", permissionService.getUserPermissions(userId));
    }
}
