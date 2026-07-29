package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.CookieUtils;
import com.aicourse.dto.*;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.AuthService;
import com.aicourse.service.PermissionService;
import com.aicourse.service.UserService;
import com.aicourse.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证授权控制器
 * 提供登录、注册、令牌刷新、密码管理、个人信息、权限查询等接口
 */
@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final PermissionService permissionService;
    private final UserService userService;
    private final CookieUtils cookieUtils;

    public AuthController(AuthService authService,
                          PermissionService permissionService,
                          UserService userService,
                          CookieUtils cookieUtils) {
        this.authService = authService;
        this.permissionService = permissionService;
        this.userService = userService;
        this.cookieUtils = cookieUtils;
    }

    /**
     * 账号密码登录
     * @param body 登录参数（强类型校验：用户名/密码必填）
     * @param request 请求对象
     * @return 登录令牌及用户信息
     */
    @Operation(summary = "账号密码登录", description = "使用用户名和密码登录，可选记住我。成功后自动写入 HttpOnly Cookie。")
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody UserLoginDTO body,
                                                 HttpServletRequest request, HttpServletResponse response) {
        ApiResponse<Map<String, Object>> result =
                authService.login(body.getUsername(), body.getPassword(), body.getRememberMe(), request);
        boolean rememberMe = body.getRememberMe() != null && body.getRememberMe();
        issueAuthCookiesAndStripBody(response, result, rememberMe);
        return result;
    }

    /**
     * 用户注册
     * @param body 注册参数（强类型校验：用户名/姓名/密码/确认密码/角色必填）
     * @param request 请求对象
     * @return 注册结果
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody UserRegisterDTO body,
                                                    HttpServletRequest request, HttpServletResponse response) {
        // 注意：角色由服务端强制为 STUDENT（见 AuthServiceImpl.register 安全加固），
        // 此处即使请求体携带 role 也仅做格式校验，最终角色以服务端为准。
        ApiResponse<Map<String, Object>> result = authService.register(body.getEmail(), body.getUsername(),
                body.getPassword(), body.getFullName(), body.getRole(), body.getPhone(), body.getCode(), request);
        // 注册会话统一按非记住我处理（刷新令牌有效期 1 天）
        issueAuthCookiesAndStripBody(response, result, false);
        return result;
    }

    /**
     * 刷新访问令牌。
     * 刷新令牌从 HttpOnly Cookie（ai_course_refresh）读取，前端 JS 不可触碰，
     * 避免刷新令牌经请求体暴露后被窃取复用。
     * @param request 请求对象（携带刷新 Cookie）
     * @param response 响应对象（写入新访问 Cookie）
     * @return 新令牌信息（已剥离响应体中的令牌字段）
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新访问令牌")
    public ApiResponse<Map<String, Object>> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieUtils.readCookie(request, JwtUtils.REFRESH_TOKEN_COOKIE);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("刷新令牌缺失或已失效");
        }
        ApiResponse<Map<String, Object>> result = authService.refreshToken(refreshToken);
        // 刷新后写入新的访问 Cookie（刷新令牌本身 Cookie 不变，仅访问令牌轮换）
        Map<String, Object> data = result.getData();
        if (data != null) {
            String newAccessToken = (String) data.get("token");
            cookieUtils.addAuthCookies(response, newAccessToken, null, true);
            data.remove("token");
        }
        return result;
    }

    /**
     * 退出登录
     * @param auth 登录认证信息
     * @param request 请求对象
     * @param response 响应对象（清除认证 Cookie）
     * @return 操作结果
     */
    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public ApiResponse<Void> logout(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        Long userId = (Long) auth.getPrincipal();
        ApiResponse<Void> result = authService.logout(userId, request);
        cookieUtils.clearAuthCookies(response);
        return result;
    }

    /**
     * 将令牌写入 HttpOnly Cookie 并从响应体移除令牌字段，
     * 确保前端永远拿不到原始令牌字符串（消除 XSS 窃取面）。
     */
    private void issueAuthCookiesAndStripBody(HttpServletResponse response,
                                              ApiResponse<Map<String, Object>> result, boolean rememberMe) {
        Map<String, Object> data = result.getData();
        if (data == null) {
            return;
        }
        String token = (String) data.get("token");
        String refreshToken = (String) data.get("refreshToken");
        cookieUtils.addAuthCookies(response, token, refreshToken, rememberMe);
        data.remove("token");
        data.remove("refreshToken");
    }

    /**
     * 修改登录密码
     * @param auth 登录认证信息
     * @param body 新旧密码参数（强类型校验）
     * @return 操作结果
     */
    @PostMapping("/change-password")
    @Operation(summary = "修改登录密码")
    public ApiResponse<Void> changePassword(Authentication auth, @Valid @RequestBody ChangePasswordDTO body) {
        Long userId = (Long) auth.getPrincipal();
        return authService.changePassword(userId, body.getOldPassword(), body.getNewPassword());
    }

    /**
     * 密码修改备用接口（复用 change-password 逻辑）
     * @param auth 登录认证信息
     * @param body 新旧密码参数（强类型校验）
     * @return 操作结果
     */
    @PutMapping("/password")
    @Operation(summary = "修改登录密码（备用接口）")
    public ApiResponse<Void> altChangePassword(Authentication auth, @Valid @RequestBody ChangePasswordDTO body) {
        return changePassword(auth, body);
    }

    /**
     * 手机号/邮箱 + 验证码 重置密码
     * 需求文档: POST /api/auth/reset-password
     * @param body 手机号或邮箱、验证码、新密码（强类型校验）
     * @return 操作结果
     */
    @PostMapping("/reset-password")
    @Operation(summary = "重置密码")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        if (body.getEmail() != null && !body.getEmail().isEmpty()) {
            return authService.resetPasswordByEmail(body.getEmail(), body.getCode(), body.getNewPassword());
        }
        if (body.getPhone() != null && !body.getPhone().isEmpty()) {
            return authService.resetPassword(body.getPhone(), body.getCode(), body.getNewPassword());
        }
        return ApiResponse.error(ApiCode.BAD_REQUEST, "请提供邮箱或手机号");
    }

    /**
     * 获取当前登录用户信息
     * @param auth 登录认证信息
     * @return 用户基础信息
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户信息")
    public ApiResponse<?> getCurrentUser(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return authService.getCurrentUser(userId);
    }

    /**
     * 更新当前用户个人资料
     * @param auth 登录认证信息
     * @param updates 待更新用户字段（白名单 DTO，不含 role/password/id 等敏感字段）
     * @return 更新后的用户信息（密码已脱敏）
     */
    @PutMapping("/me")
    @Operation(summary = "更新当前用户个人资料")
    public ApiResponse<User> updateCurrentUser(Authentication auth, @Valid @RequestBody UserUpdateDTO updates) {
        Long userId = (Long) auth.getPrincipal();
        User user = userService.getById(userId);
        if (user == null) {
            return ApiResponse.error(ApiCode.NOT_FOUND, "用户不存在");
        }

        // 按需白名单更新非空字段（UserUpdateDTO 本身已不含 role/password/id 等敏感列）
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

        userService.updateById(user);
        User updatedUser = userService.getById(userId);
        // 密码脱敏，禁止返回
        if (updatedUser != null) {
            updatedUser.setPassword(null);
        }
        return ApiResponse.success("个人资料更新成功", updatedUser);
    }

    /**
     * 获取当前用户权限列表
     * @param auth 登录认证信息
     * @return 权限集合
     */
    @GetMapping("/permissions")
    @Operation(summary = "获取当前用户权限列表")
    public ApiResponse<?> getPermissions(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(permissionService.getUserPermissions(userId));
    }

    /**
     * 发送邮箱验证码
     * @param body 邮箱、验证码类型（强类型校验）
     * @param request 请求对象
     * @return 操作结果
     */
    @PostMapping("/send-code")
    @Operation(summary = "发送邮箱验证码")
    public ApiResponse<Void> sendVerificationCode(@Valid @RequestBody SendCodeRequest body,
                                                  HttpServletRequest request) {
        return authService.sendVerificationCode(body.getEmail(), body.getType(), request);
    }

    /**
     * 邮箱 + 验证码 重置密码
     * @param body 邮箱、验证码、新密码（强类型校验）
     * @return 操作结果
     */
    @PostMapping("/reset-password-by-email")
    @Operation(summary = "邮箱验证码重置密码")
    public ApiResponse<Void> resetPasswordByEmail(@Valid @RequestBody ResetPasswordByEmailRequest body) {
        return authService.resetPasswordByEmail(body.getEmail(), body.getCode(), body.getNewPassword());
    }
}
