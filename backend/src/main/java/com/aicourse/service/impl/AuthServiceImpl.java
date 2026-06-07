package com.aicourse.service.impl;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.User;
import com.aicourse.entity.LoginLog;
import com.aicourse.entity.UserSession;
import com.aicourse.entity.VerificationCode;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.*;
import com.aicourse.util.JwtUtils;
import com.aicourse.util.IpUtil;
import com.aicourse.util.UserAgentUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserService userService;
    private final UserSessionService userSessionService;
    private final LoginLogService loginLogService;
    private final VerificationCodeService verificationCodeService;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final IpUtil ipUtil;
    private final UserAgentUtil userAgentUtil;

    public AuthServiceImpl(UserService userService, UserSessionService userSessionService,
                           LoginLogService loginLogService, VerificationCodeService verificationCodeService,
                           JwtUtils jwtUtils, PasswordEncoder passwordEncoder,
                           IpUtil ipUtil, UserAgentUtil userAgentUtil) {
        this.userService = userService;
        this.userSessionService = userSessionService;
        this.loginLogService = loginLogService;
        this.verificationCodeService = verificationCodeService;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
        this.ipUtil = ipUtil;
        this.userAgentUtil = userAgentUtil;
    }

    @Override
    @Transactional
    public ApiResponse<Map<String, Object>> login(String account, String password, Boolean rememberMe, HttpServletRequest request) {
        User user = userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, account)
                .or().eq(User::getEmail, account)
                .or().eq(User::getPhone, account));

        if (user == null) {
            saveLoginLog(null, account, "FAILURE", request, "用户不存在");
            throw new BusinessException("用户名或密码错误");
        }

        if (!user.getIsActive()) {
            saveLoginLog(user.getId(), user.getUsername(), "FAILURE", request, "账号已禁用");
            throw new BusinessException("账号已被禁用");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            saveLoginLog(user.getId(), user.getUsername(), "FAILURE", request, "账号已锁定");
            throw new BusinessException("账号已被锁定，请稍后再试");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            int failedAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failedAttempts);
            if (failedAttempts >= 5) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            }
            userService.updateById(user);
            saveLoginLog(user.getId(), user.getUsername(), "FAILURE", request, "密码错误");
            throw new BusinessException("用户名或密码错误");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLogin(LocalDateTime.now());
        userService.updateById(user);

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole(), rememberMe != null && rememberMe);
        String refreshToken = UUID.randomUUID().toString().replace("-", "");

        // Create session
        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setSessionToken(token);
        session.setRefreshToken(refreshToken);
        session.setIsActive(true);
        session.setIsCurrent(true);
        session.setIpAddress(ipUtil.getClientIp(request));
        session.setDeviceName(userAgentUtil.getDeviceName(request));
        session.setBrowser(userAgentUtil.getBrowser(request));
        session.setOperatingSystem(userAgentUtil.getOperatingSystem(request));
        session.setUserAgent(request.getHeader("User-Agent"));
        session.setLoginTime(LocalDateTime.now());
        session.setLastActivity(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(rememberMe != null && rememberMe ? 7 : 1));
        userSessionService.save(session);

        saveLoginLog(user.getId(), user.getUsername(), "SUCCESS", request, null);

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());
        userData.put("email", user.getEmail());
        userData.put("fullName", user.getFullName());
        userData.put("role", user.getRole());
        userData.put("avatarUrl", user.getAvatarUrl());
        userData.put("phone", user.getPhone());
        userData.put("isActive", user.getIsActive());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("refreshToken", refreshToken);
        data.put("user", userData);

        return ApiResponse.success("登录成功", data);
    }

    @Override
    @Transactional
    public ApiResponse<Map<String, Object>> register(String email, String username, String password,
                                                      String fullName, String role, String phone,
                                                      String code, HttpServletRequest request) {
        if (userService.count(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
            throw new BusinessException("用户名已存在");
        }
        if (userService.count(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0) {
            throw new BusinessException("邮箱已被注册");
        }
        if (phone != null && userService.count(new LambdaQueryWrapper<User>().eq(User::getPhone, phone)) > 0) {
            throw new BusinessException("手机号已被注册");
        }

        if (code != null) {
            VerificationCode vc = verificationCodeService.getOne(new LambdaQueryWrapper<VerificationCode>()
                    .eq(VerificationCode::getEmail, email)
                    .eq(VerificationCode::getCode, code)
                    .eq(VerificationCode::getIsUsed, false)
                    .eq(VerificationCode::getType, "REGISTER")
                    .gt(VerificationCode::getExpiresAt, LocalDateTime.now())
                    .orderByDesc(VerificationCode::getCreatedAt)
                    .last("LIMIT 1"));
            if (vc == null) {
                throw new BusinessException("验证码无效或已过期");
            }
            vc.setIsUsed(true);
            verificationCodeService.updateById(vc);
        }

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setRole(role != null ? role : "STUDENT");
        user.setPhone(phone);
        user.setIsActive(true);
        user.setIsVerified(false);
        user.setFailedLoginAttempts(0);
        user.setRegisterIp(ipUtil.getClientIp(request));
        userService.save(user);

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole(), false);
        String refreshToken = UUID.randomUUID().toString().replace("-", "");

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("refreshToken", refreshToken);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("role", user.getRole());

        return ApiResponse.success("注册成功", data);
    }

    @Override
    public ApiResponse<Map<String, Object>> refreshToken(String refreshToken) {
        UserSession session = userSessionService.getOne(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getRefreshToken, refreshToken)
                .eq(UserSession::getIsActive, true));
        if (session == null) {
            throw new BusinessException("刷新令牌无效");
        }
        User user = userService.getById(session.getUserId());
        if (user == null || !user.getIsActive()) {
            throw new BusinessException("用户不存在或已禁用");
        }
        String newToken = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole(), true);
        session.setSessionToken(newToken);
        session.setLastActivity(LocalDateTime.now());
        userSessionService.updateById(session);

        Map<String, Object> data = new HashMap<>();
        data.put("token", newToken);
        data.put("userId", user.getId());
        data.put("role", user.getRole());
        return ApiResponse.success(data);
    }

    @Override
    @Transactional
    public ApiResponse<Void> logout(Long userId, HttpServletRequest request) {
        userSessionService.lambdaUpdate()
            .eq(UserSession::getUserId, userId)
            .eq(UserSession::getIsActive, true)
            .set(UserSession::getIsActive, false)
            .set(UserSession::getIsCurrent, false)
            .update();
        saveLoginLog(userId, null, "LOGOUT", request, "安全退出");
        return ApiResponse.success("退出成功", null);
    }

    @Override    @Transactional
    public ApiResponse<Void> resetPassword(String phone, String code, String newPassword) {
        VerificationCode vc = verificationCodeService.getOne(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getPhone, phone)
                .eq(VerificationCode::getCode, code)
                .eq(VerificationCode::getType, "RESET_PASSWORD")
                .eq(VerificationCode::getIsUsed, false)
                .gt(VerificationCode::getExpiresAt, LocalDateTime.now())
                .orderByDesc(VerificationCode::getCreatedAt)
                .last("LIMIT 1"));
        if (vc == null) {
            throw new BusinessException("验证码无效或已过期");
        }
        vc.setIsUsed(true);
        verificationCodeService.updateById(vc);

        User user = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            throw new BusinessException("手机号未注册");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userService.updateById(user);
        return ApiResponse.success("密码重置成功", null);
    }

    @Override
    @Transactional
    public ApiResponse<Void> changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userService.updateById(user);
        return ApiResponse.success("密码修改成功", null);
    }

    @Override
    public ApiResponse<Map<String, Object>> getCurrentUser(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("fullName", user.getFullName());
        data.put("role", user.getRole());
        data.put("avatarUrl", user.getAvatarUrl());
        data.put("phone", user.getPhone());
        data.put("isActive", user.getIsActive());
        data.put("lastLogin", user.getLastLogin());
        return ApiResponse.success(data);
    }

    private void saveLoginLog(Long userId, String username, String status, HttpServletRequest request, String reason) {
        LoginLog log = new LoginLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setLoginStatus(status);
        log.setIpAddress(ipUtil.getClientIp(request));
        log.setDeviceName(userAgentUtil.getDeviceName(request));
        log.setBrowser(userAgentUtil.getBrowser(request));
        log.setOperatingSystem(userAgentUtil.getOperatingSystem(request));
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setFailureReason(reason);
        loginLogService.save(log);
    }
}
