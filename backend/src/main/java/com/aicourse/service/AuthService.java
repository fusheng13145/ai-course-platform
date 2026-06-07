package com.aicourse.service;

import com.aicourse.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

public interface AuthService {
    ApiResponse<Map<String, Object>> login(String account, String password, Boolean rememberMe, HttpServletRequest request);
    ApiResponse<Map<String, Object>> register(String email, String username, String password, String fullName, String role, String phone, String code, HttpServletRequest request);
    ApiResponse<Map<String, Object>> refreshToken(String refreshToken);
    ApiResponse<Void> logout(Long userId, HttpServletRequest request);
    ApiResponse<Void> resetPassword(String phone, String code, String newPassword);
    ApiResponse<Void> changePassword(Long userId, String oldPassword, String newPassword);
    ApiResponse<Map<String, Object>> getCurrentUser(Long userId);
}
