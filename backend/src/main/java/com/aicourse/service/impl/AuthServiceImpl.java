package com.aicourse.service.impl;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.*;
import com.aicourse.service.component.AuthPasswordComponent;
import com.aicourse.service.component.AuthSessionComponent;
import com.aicourse.service.component.AuthVerificationComponent;
import com.aicourse.util.IpUtil;
import com.aicourse.util.JwtUtils;
import com.aicourse.util.UserAgentUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 认证授权服务门面：登录/注册/会话、密码管理、验证码三类职责委托给 service.component 下协作者组件。
 * <p>拆分自原 548 行单类（2026-08-31）。保留唯一 10 参构造器（生产由 Spring 注入、旧单测直构不变），
 * 内部按「验证码 → 密码 → 会话」无环依赖顺序装配组件。{@code @Transactional} 保留在门面委托方法上，
 * 由 Spring 代理按方法拦截；组件持有实际业务逻辑。</p>
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final AuthSessionComponent sessionComponent;
    private final AuthPasswordComponent passwordComponent;
    private final AuthVerificationComponent verificationComponent;

    public AuthServiceImpl(UserService userService, UserSessionService userSessionService,
                           LoginLogService loginLogService, VerificationCodeService verificationCodeService,
                           EmailService emailService, JwtUtils jwtUtils, PasswordEncoder passwordEncoder,
                           IpUtil ipUtil, UserAgentUtil userAgentUtil,
                           ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        StringRedisTemplate redisTemplate = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        this.verificationComponent = new AuthVerificationComponent(verificationCodeService, emailService, ipUtil, redisTemplate);
        this.passwordComponent = new AuthPasswordComponent(userService, verificationCodeService, passwordEncoder, verificationComponent);
        this.sessionComponent = new AuthSessionComponent(userService, userSessionService, verificationCodeService,
                jwtUtils, passwordEncoder, ipUtil, userAgentUtil, loginLogService, passwordComponent);
    }

    // ===================== 登录 =====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> login(String account, String password,
                                                  Boolean rememberMe, HttpServletRequest request) {
        return sessionComponent.login(account, password, rememberMe, request);
    }

    // ===================== 注册 =====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> register(String email, String username, String password,
                                                     String fullName, String role, String phone,
                                                     String code, HttpServletRequest request) {
        return sessionComponent.register(email, username, password, fullName, role, phone, code, request);
    }

    // ===================== 刷新令牌 =====================
    @Override
    public ApiResponse<Map<String, Object>> refreshToken(String refreshToken) {
        return sessionComponent.refreshToken(refreshToken);
    }

    // ===================== 退出登录 =====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> logout(Long userId, HttpServletRequest request) {
        return sessionComponent.logout(userId, request);
    }

    // ===================== 手机号找回密码 =====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> resetPassword(String phone, String code, String newPassword) {
        return passwordComponent.resetPassword(phone, code, newPassword);
    }

    // ===================== 修改密码（已知原密码） =====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> changePassword(Long userId, String oldPassword, String newPassword) {
        return passwordComponent.changePassword(userId, oldPassword, newPassword);
    }

    // ===================== 获取当前登录用户信息 =====================
    @Override
    public ApiResponse<Map<String, Object>> getCurrentUser(Long userId) {
        return sessionComponent.getCurrentUser(userId);
    }

    // ===================== 发送邮箱验证码 =====================
    @Override
    public ApiResponse<Void> sendVerificationCode(String email, String type, HttpServletRequest request) {
        return verificationComponent.sendVerificationCode(email, type, request);
    }

    // ===================== 邮箱找回密码 =====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> resetPasswordByEmail(String email, String code, String newPassword) {
        return passwordComponent.resetPasswordByEmail(email, code, newPassword);
    }

    // ===================== 校验邮箱验证码有效性 =====================
    @Override
    public ApiResponse<Void> verifyCode(String email, String code) {
        return verificationComponent.verifyCode(email, code);
    }
}
