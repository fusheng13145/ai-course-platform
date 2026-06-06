package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
            (String) body.get("email"),
            (String) body.get("username"),
            (String) body.get("password"),
            (String) body.get("fullName"),
            (String) body.get("role"),
            (String) body.get("phone"),
            (String) body.get("code"),
            request);
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refreshToken(@RequestBody Map<String, Object> body) {
        return authService.refreshToken((String) body.get("refreshToken"));
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@RequestBody Map<String, Object> body) {
        return authService.resetPassword(
            (String) body.get("phone"),
            (String) body.get("code"),
            (String) body.get("newPassword"));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(Authentication auth, @RequestBody Map<String, Object> body) {
        Long userId = (Long) auth.getPrincipal();
        return authService.changePassword(userId,
            (String) body.get("oldPassword"),
            (String) body.get("newPassword"));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> getCurrentUser(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return authService.getCurrentUser(userId);
    }
}
