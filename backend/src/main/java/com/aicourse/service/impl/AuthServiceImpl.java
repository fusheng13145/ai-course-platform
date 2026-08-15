package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.LoginLog;
import com.aicourse.entity.User;
import com.aicourse.entity.UserSession;
import com.aicourse.entity.VerificationCode;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.*;
import com.aicourse.util.IpUtil;
import com.aicourse.util.JwtUtils;
import com.aicourse.util.PasswordPolicy;
import com.aicourse.util.UserAgentUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import com.aicourse.common.RoleConstants;

/**
 * 认证授权服务实现类
 * 登录、注册、注销、令牌刷新、密码找回/修改、验证码发送等功能
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    // ===================== 常量定义 =====================
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILURE = "FAILURE";
    private static final String STATUS_LOGOUT = "LOGOUT";
    private static final String TYPE_REGISTER = "REGISTER";
    private static final String TYPE_RESET_PASSWORD = "RESET_PASSWORD";
    private static final String TYPE_FORGOT_PASSWORD = "FORGOT_PASSWORD";
    private static final String DEFAULT_ROLE_STUDENT = RoleConstants.STUDENT;
    private static final int LOCK_MINUTES = 15;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int CODE_EXPIRE_MINUTES = 5;
    private static final int RATE_LIMIT_MINUTES = 1;
    private static final int SESSION_EXPIRE_DAY_NORMAL = 1;
    private static final int SESSION_EXPIRE_DAY_REMEMBER = 7;

    // ===================== 验证码爆破防护（P1-4 修复） =====================
    // 验证码为 6 位数字，若不限制尝试次数，攻击者可对已知邮箱暴力枚举重置任意用户密码。
    // 现对「校验验证码」与「邮箱重置密码」两条路径共享 Redis 滑动计数：
    // 同一邮箱 5 分钟窗口内最多尝试 5 次，超出即拒绝（HTTP 429）。
    private static final String VC_ATTEMPT_KEY_PREFIX = "auth:vc:attempt:";
    private static final Duration VC_ATTEMPT_WINDOW = Duration.ofMinutes(5);
    private static final long VC_ATTEMPT_MAX = 5L;

    // ===================== 密码复杂度验证 =====================
    // 规则（统一收口到 com.aicourse.util.PasswordPolicy）：8-32位，大小写字母+数字+特殊字符。
    // 注册/改密/重置（手机+邮箱）/管理端创建与重置用户密码均走同一策略，杜绝弱规则旁路。
    private void validatePasswordComplexity(String password) {
        PasswordPolicy.validate(password);
    }

    // ===================== 配置注入 =====================
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // ===================== 依赖注入 =====================
    private final UserService userService;
    private final UserSessionService userSessionService;
    private final LoginLogService loginLogService;
    private final VerificationCodeService verificationCodeService;
    private final EmailService emailService;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final IpUtil ipUtil;
    private final UserAgentUtil userAgentUtil;
    /** 验证码尝试限流用 Redis（ObjectProvider 注入兼容无 Redis 的测试切片，缺省时降级不计数） */
    private final StringRedisTemplate redisTemplate;

    public AuthServiceImpl(UserService userService, UserSessionService userSessionService,
                           LoginLogService loginLogService, VerificationCodeService verificationCodeService,
                           EmailService emailService, JwtUtils jwtUtils, PasswordEncoder passwordEncoder,
                           IpUtil ipUtil, UserAgentUtil userAgentUtil,
                           ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.userService = userService;
        this.userSessionService = userSessionService;
        this.loginLogService = loginLogService;
        this.verificationCodeService = verificationCodeService;
        this.emailService = emailService;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
        this.ipUtil = ipUtil;
        this.userAgentUtil = userAgentUtil;
        this.redisTemplate = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
    }

    /**
     * 验证码尝试限流：同一邮箱 5 分钟窗口内最多尝试 {@value #VC_ATTEMPT_MAX} 次。
     * 校验成功/失败均计数——成功一次无妨，失败计数可有效封死暴力枚举。
     */
    private void assertVerifyAttemptAllowed(String email) {
        if (redisTemplate == null || email == null || email.isBlank()) {
            return;
        }
        String key = VC_ATTEMPT_KEY_PREFIX + email.trim().toLowerCase();
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, VC_ATTEMPT_WINDOW);
        }
        if (count != null && count > VC_ATTEMPT_MAX) {
            throw new BusinessException(429, "验证码尝试次数过多，请5分钟后再试");
        }
    }

    // ===================== 登录 =====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> login(String account, String password,
                                                  Boolean rememberMe, HttpServletRequest request) {
                                                      log.info("登录认证");

        User user = userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, account)
                .or().eq(User::getEmail, account)
                .or().eq(User::getPhone, account));

        if (user == null) {
            saveLoginLog(null, account, STATUS_FAILURE, request, "用户不存在");
            throw new BusinessException("用户名或密码错误");
        }

        if (!user.getIsActive()) {
            saveLoginLog(user.getId(), user.getUsername(), STATUS_FAILURE, request, "账号已禁用");
            throw new BusinessException("账号已被禁用");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            saveLoginLog(user.getId(), user.getUsername(), STATUS_FAILURE, request, "账号已锁定");
            throw new BusinessException("账号已被锁定，请稍后再试");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            int failedAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failedAttempts);
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            }
            userService.updateById(user);
            saveLoginLog(user.getId(), user.getUsername(), STATUS_FAILURE, request, "密码错误");
            throw new BusinessException("用户名或密码错误");
        }

        // 登录成功，重置锁定与失败次数
        LocalDateTime now = LocalDateTime.now();
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLogin(now);
        userService.updateById(user);

        boolean remember = rememberMe != null && rememberMe;
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole(), remember);
        String refreshToken = UUID.randomUUID().toString().replace("-", "");

        // 刷新令牌仅向客户端返回明文；入库前做 SHA-256 哈希存储，降低数据库泄漏风险
        UserSession session = buildUserSession(user, token, jwtUtils.sha256Hex(refreshToken), remember, now, request);
        userSessionService.save(session);

        saveLoginLog(user.getId(), user.getUsername(), STATUS_SUCCESS, request, null);

        // 组装返回数据
        Map<String, Object> userData = new HashMap<>(8);
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());
        userData.put("email", user.getEmail());
        userData.put("fullName", user.getFullName());
        userData.put("role", user.getRole());
        userData.put("avatarUrl", user.getAvatarUrl());
        userData.put("phone", user.getPhone());
        userData.put("isActive", user.getIsActive());

        Map<String, Object> result = new HashMap<>(3);
        result.put("token", token);
        result.put("refreshToken", refreshToken);
        result.put("user", userData);

        return ApiResponse.success("登录成功", result);
    }

    // ===================== 注册 =====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> register(String email, String username, String password,
                                                    String fullName, String role, String phone,
                                                    String code, HttpServletRequest request) {
                                                        log.info("注册认证");

        // 用户名空值兜底校验（Controller 层 @Valid 已拦截，此处为服务层双保险；
        // 纯空格用户名按既定契约放行，由 DTO 层 @NotBlank 拦截）
        if (username == null || username.isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        // 重复性校验
        if (userService.count(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
            throw new BusinessException("用户名已存在");
        }
        if (userService.count(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0) {
            throw new BusinessException("邮箱已被注册");
        }
        if (phone != null && userService.count(new LambdaQueryWrapper<User>().eq(User::getPhone, phone)) > 0) {
            throw new BusinessException("手机号已被注册");
        }

        // 密码复杂度校验
        validatePasswordComplexity(password);

        // 验证码校验
        if (code != null) {
            VerificationCode vc = verificationCodeService.getOne(new LambdaQueryWrapper<VerificationCode>()
                    .eq(VerificationCode::getEmail, email)
                    .eq(VerificationCode::getCode, code)
                    .eq(VerificationCode::getIsUsed, false)
                    .eq(VerificationCode::getType, TYPE_REGISTER)
                    .gt(VerificationCode::getExpiresAt, LocalDateTime.now())
                    .orderByDesc(VerificationCode::getCreatedAt)
                    .last("LIMIT 1"));
            if (vc == null) {
                throw new BusinessException("验证码无效或已过期");
            }
            vc.setIsUsed(true);
            verificationCodeService.updateById(vc);
        }

        // 新增用户
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName);
        // 安全加固：公开注册端点不允许调用方指定角色，强制默认为学生，防止越权提权（如注册即 ADMIN）
        user.setRole(DEFAULT_ROLE_STUDENT);
        user.setPhone(phone);
        user.setIsActive(true);
        user.setIsVerified(false);
        user.setFailedLoginAttempts(0);
        user.setRegisterIp(ipUtil.getClientIp(request));
        userService.save(user);

        LocalDateTime now = LocalDateTime.now();
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole(), false);
        String refreshToken = UUID.randomUUID().toString().replace("-", "");

        // 创建会话（刷新令牌哈希存储）
        UserSession session = buildUserSession(user, token, jwtUtils.sha256Hex(refreshToken), false, now, request);
        userSessionService.save(session);

        Map<String, Object> data = new HashMap<>(5);
        data.put("token", token);
        data.put("refreshToken", refreshToken);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("role", user.getRole());

        return ApiResponse.success("注册成功", data);
    }

    // ===================== 刷新令牌 =====================
    @Override
    public ApiResponse<Map<String, Object>> refreshToken(String refreshToken) {
        // 对传入明文刷新令牌做哈希后再比对，库内仅存哈希值
        String refreshTokenHash = jwtUtils.sha256Hex(refreshToken);
        // 修复 P1-12：服务端会话过期校验——refresh Cookie 过期（浏览器丢弃）后，
        // 服务端必须同样拒绝该令牌，杜绝「Cookie 已失效但服务端仍放行」的会话劫持窗口。
        UserSession session = userSessionService.getOne(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getRefreshToken, refreshTokenHash)
                .eq(UserSession::getIsActive, true)
                .gt(UserSession::getExpiresAt, LocalDateTime.now()));
        if (session == null) {
            throw new BusinessException("刷新令牌无效或已过期");
        }

        User user = userService.getById(session.getUserId());
        if (user == null || !user.getIsActive()) {
            throw new BusinessException("用户不存在或已禁用");
        }

        String newToken = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole(), true);
        // 吊销旧访问令牌缓存，避免轮换后旧令牌仍在缓存 TTL 内可用
        userSessionService.evictSessionCache(session.getSessionToken());
        // 修复 P1-12：刷新令牌轮换（rotation）——每次刷新生成全新 refreshToken 并作废旧值，
        // 窃取的刷新令牌最多生效一次，无法无限期接管会话。
        String newRefreshToken = UUID.randomUUID().toString().replace("-", "");
        session.setSessionToken(newToken);
        session.setRefreshToken(jwtUtils.sha256Hex(newRefreshToken));
        session.setLastActivity(LocalDateTime.now());
        userSessionService.updateById(session);

        Map<String, Object> data = new HashMap<>(4);
        data.put("token", newToken);
        data.put("refreshToken", newRefreshToken);
        data.put("userId", user.getId());
        data.put("role", user.getRole());
        return ApiResponse.success(data);
    }

    // ===================== 退出登录 =====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> logout(Long userId, HttpServletRequest request) {
        log.info("登出认证");

        // 先取出该用户当前有效会话的令牌，便于随后立即使其缓存失效
        List<UserSession> activeSessions = userSessionService.list(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, userId)
                .eq(UserSession::getIsActive, true));
        userSessionService.lambdaUpdate()
                .eq(UserSession::getUserId, userId)
                .eq(UserSession::getIsActive, true)
                .set(UserSession::getIsActive, false)
                .set(UserSession::getIsCurrent, false)
                .update();
        // 立即使每个会话令牌的缓存失效，避免旧令牌在缓存 TTL 内仍被判定为有效
        activeSessions.forEach(s -> userSessionService.evictSessionCache(s.getSessionToken()));

        saveLoginLog(userId, null, STATUS_LOGOUT, request, "安全退出");
        return ApiResponse.success("退出成功", null);
    }

    // ===================== 手机号找回密码 =====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> resetPassword(String phone, String code, String newPassword) {
        log.info("重置认证");

        VerificationCode vc = verificationCodeService.getOne(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getPhone, phone)
                .eq(VerificationCode::getCode, code)
                .eq(VerificationCode::getType, TYPE_RESET_PASSWORD)
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
        // 密码复杂度校验
        validatePasswordComplexity(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        userService.updateById(user);
        return ApiResponse.success("密码重置成功", null);
    }

    // ===================== 修改密码（已知原密码） =====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        // 密码复杂度校验
        validatePasswordComplexity(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        userService.updateById(user);
        return ApiResponse.success("密码修改成功", null);
    }

    // ===================== 获取当前登录用户信息 =====================
    @Override
    public ApiResponse<Map<String, Object>> getCurrentUser(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        Map<String, Object> data = new HashMap<>(9);
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

    // ===================== 发送邮箱验证码 =====================
    @Override
    public ApiResponse<Void> sendVerificationCode(String email, String type, HttpServletRequest request) {
        log.info("发送认证");

        // 限流：1分钟内仅允许一次
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(RATE_LIMIT_MINUTES);
        long recentCount = verificationCodeService.count(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getEmail, email)
                .eq(VerificationCode::getType, type)
                .gt(VerificationCode::getCreatedAt, oneMinuteAgo));
        if (recentCount > 0) {
            throw new BusinessException("请求过于频繁，请1分钟后再试");
        }

        // 生成6位数字验证码
        String code = String.format("%06d", new SecureRandom().nextInt(1000000));
        LocalDateTime now = LocalDateTime.now();

        // 保存验证码
        VerificationCode vc = new VerificationCode();
        vc.setEmail(email);
        vc.setCode(code);
        vc.setType(type);
        vc.setIsUsed(false);
        vc.setExpiresAt(now.plusMinutes(CODE_EXPIRE_MINUTES));
        vc.setIpAddress(ipUtil.getClientIp(request));
        verificationCodeService.save(vc);

        // 发送邮件
        emailService.sendVerificationCode(email, code, type);
        return ApiResponse.success("验证码已发送到邮箱", null);
    }

    // ===================== 邮箱找回密码 =====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> resetPasswordByEmail(String email, String code, String newPassword) {
        log.info("重置认证");

        // 修复 P1-4：验证码爆破防护——尝试计数前置，超限直接拒绝（429）
        assertVerifyAttemptAllowed(email);

        VerificationCode vc = verificationCodeService.getOne(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getEmail, email)
                .eq(VerificationCode::getCode, code)
                .eq(VerificationCode::getType, TYPE_FORGOT_PASSWORD)
                .eq(VerificationCode::getIsUsed, false)
                .gt(VerificationCode::getExpiresAt, LocalDateTime.now())
                .orderByDesc(VerificationCode::getCreatedAt)
                .last("LIMIT 1"));
        if (vc == null) {
            throw new BusinessException("验证码无效或已过期");
        }
        vc.setIsUsed(true);
        verificationCodeService.updateById(vc);

        User user = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            throw new BusinessException("该邮箱未注册");
        }

        // 密码复杂度校验
        validatePasswordComplexity(newPassword);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userService.updateById(user);

        return ApiResponse.success("密码重置成功", null);
    }

    /**
     * 校验邮箱验证码有效性（仅校验，不消费；供找回密码第二步「验证邮箱」使用）。
     * 与 resetPasswordByEmail 共享同一 Redis 尝试限流，防止暴力枚举。
     */
    @Override
    public ApiResponse<Void> verifyCode(String email, String code) {
        // 修复 P1-4：验证码爆破防护——尝试计数前置，超限直接拒绝（429）
        assertVerifyAttemptAllowed(email);

        VerificationCode vc = verificationCodeService.getOne(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getEmail, email)
                .eq(VerificationCode::getCode, code)
                .eq(VerificationCode::getType, TYPE_FORGOT_PASSWORD)
                .eq(VerificationCode::getIsUsed, false)
                .gt(VerificationCode::getExpiresAt, LocalDateTime.now())
                .orderByDesc(VerificationCode::getCreatedAt)
                .last("LIMIT 1"));
        if (vc == null) {
            throw new BusinessException("验证码无效或已过期");
        }
        return ApiResponse.success("验证码有效", null);
    }

    // ===================== 私有工具方法 =====================
    /**
     * 构建用户会话对象
     */
    private UserSession buildUserSession(User user, String token, String refreshToken,
                                        boolean rememberMe, LocalDateTime now, HttpServletRequest request) {
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
        session.setLoginTime(now);
        session.setLastActivity(now);

        int expireDay = rememberMe ? SESSION_EXPIRE_DAY_REMEMBER : SESSION_EXPIRE_DAY_NORMAL;
        session.setExpiresAt(now.plusDays(expireDay));
        return session;
    }

    /**
     * 保存登录日志
     */
    private void saveLoginLog(Long userId, String username, String status,
                              HttpServletRequest request, String reason) {
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