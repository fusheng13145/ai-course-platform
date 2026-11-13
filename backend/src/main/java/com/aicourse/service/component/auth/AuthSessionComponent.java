package com.aicourse.service.component.auth;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.LoginLog;
import com.aicourse.entity.User;
import com.aicourse.entity.UserSession;
import com.aicourse.entity.VerificationCode;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.LoginLogService;
import com.aicourse.service.UserService;
import com.aicourse.service.UserSessionService;
import com.aicourse.service.VerificationCodeService;
import com.aicourse.common.ClientInfo;
import com.aicourse.util.JwtUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 认证-会话组件：登录 / 注册 / 刷新令牌 / 退出 / 当前用户 五类凭证与会话生命周期操作。
 * <p>拆分自原 AuthServiceImpl（2026-08-31）。作为门面 {@code AuthServiceImpl} 的内部协作者，
 * 由门面构造器装配。注册路径复用 {@link AuthPasswordComponent#validatePasswordComplexity(String)}
 * 统一密码复杂度收口。事务边界由门面委托方法上的 {@code @Transactional} 统一拦截。</p>
 */
@Slf4j
public class AuthSessionComponent {

    // ===================== 常量定义 =====================
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILURE = "FAILURE";
    private static final String STATUS_LOGOUT = "LOGOUT";
    private static final String TYPE_REGISTER = "REGISTER";
    private static final String DEFAULT_ROLE_STUDENT = RoleConstants.STUDENT;
    private static final int LOCK_MINUTES = 15;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    // P0-1（2026-09-16 上线前全检）：登录失败节流键前缀与窗口。
    // 计数粒度 =「来源 IP + 账户」，达阈值仅封禁该组合，杜绝账户级锁定 DoS。
    private static final String LOGIN_FAIL_KEY_PREFIX = "auth:login:fail:";
    private static final Duration LOGIN_FAIL_WINDOW = Duration.ofMinutes(LOCK_MINUTES);

    /**
     * 登录失败计数「原子自增 + 设置窗口过期」Lua 脚本（复用 RateLimitFilter 既有范式）。
     * <p>原实现用 {@code INCR} 与 {@code EXPIRE} 两条独立命令：若 INCR 成功而 EXPIRE 未执行
     * （异常/中断），该键将永不过期，配合「IP 可伪造」会造成 Redis 键空间无界增长。
     * 此处用脚本把 INCR + EXPIRE 原子化：INCR 后若值为 1（首个计数）才 EXPIRE，返回当前计数。</p>
     */
    private static final String LOGIN_FAIL_INCREMENT_LUA =
            "local current = redis.call('INCR', KEYS[1]) " +
            "if current == 1 then " +
            "    redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "end " +
            "return current";

    private static final DefaultRedisScript<Long> LOGIN_FAIL_SCRIPT = new DefaultRedisScript<>();

    static {
        LOGIN_FAIL_SCRIPT.setScriptText(LOGIN_FAIL_INCREMENT_LUA);
        LOGIN_FAIL_SCRIPT.setResultType(Long.class);
    }
    private static final int SESSION_EXPIRE_DAY_NORMAL = 1;
    private static final int SESSION_EXPIRE_DAY_REMEMBER = 7;

    // ===================== 依赖 =====================
    private final UserService userService;
    private final UserSessionService userSessionService;
    private final VerificationCodeService verificationCodeService;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final LoginLogService loginLogService;
    private final AuthPasswordComponent passwordComponent;
    /** 登录失败节流用 Redis（可空：无 Redis 时降级不计数，与 AuthVerificationComponent 一致）。 */
    private final StringRedisTemplate redisTemplate;

    public AuthSessionComponent(UserService userService, UserSessionService userSessionService,
                                VerificationCodeService verificationCodeService, JwtUtils jwtUtils,
                                PasswordEncoder passwordEncoder,
                                LoginLogService loginLogService, AuthPasswordComponent passwordComponent,
                                StringRedisTemplate redisTemplate) {
        this.userService = userService;
        this.userSessionService = userSessionService;
        this.verificationCodeService = verificationCodeService;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
        this.loginLogService = loginLogService;
        this.passwordComponent = passwordComponent;
        this.redisTemplate = redisTemplate;
    }

    // ===================== 登录 =====================
    public ApiResponse<Map<String, Object>> login(String account, String password,
                                                  Boolean rememberMe, ClientInfo clientInfo) {
        log.info("登录认证");

        User user = userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, account)
                .or().eq(User::getEmail, account)
                .or().eq(User::getPhone, account));

        if (user == null) {
            saveLoginLog(null, account, STATUS_FAILURE, clientInfo, "用户不存在");
            throw new BusinessException("用户名或密码错误");
        }

        if (!user.getIsActive()) {
            saveLoginLog(user.getId(), user.getUsername(), STATUS_FAILURE, clientInfo, "账号已禁用");
            throw new BusinessException("账号已被禁用");
        }

        // 账户级锁定：仅由管理员 / DB 侧设置（登录失败不再自动写入账户锁定，见下方 P0-1 修复）
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            saveLoginLog(user.getId(), user.getUsername(), STATUS_FAILURE, clientInfo, "账号已锁定");
            throw new BusinessException("账号已被锁定，请稍后再试");
        }

        // P0-1 修复（2026-09-16 上线前全检）：登录失败节流改为「来源 IP + 账户」粒度。
        // 原实现失败达阈值即 user.setLockedUntil(...)（**账户级**），任意知道用户名者连发 5 次
        // 错误密码即可锁定该账号（含 admin），构成可被循环利用的账户锁定 DoS。现改为：
        //   · 失败计数存 Redis，键含来源 IP，仅封禁 (IP, 账户) 组合；
        //   · 受害者由自身 IP 登录不受影响，攻击者只会锁住自己的 IP；
        //   · 保留 user.lockedUntil 的**读取**，以继续尊重管理员 / DB 侧设置的锁定。
        assertLoginNotThrottled(clientInfo, account);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            boolean throttled = recordLoginFailure(clientInfo, account);
            saveLoginLog(user.getId(), user.getUsername(), STATUS_FAILURE, clientInfo, "密码错误");
            if (throttled) {
                throw new BusinessException(429, "登录尝试过于频繁，请" + LOCK_MINUTES + "分钟后再试");
            }
            throw new BusinessException("用户名或密码错误");
        }

        // 登录成功：清除该 (IP, 账户) 的失败计数，并重置遗留字段
        LocalDateTime now = LocalDateTime.now();
        clearLoginFailures(clientInfo, account);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLogin(now);
        userService.updateById(user);

        boolean remember = rememberMe != null && rememberMe;
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole(), remember);
        String refreshToken = UUID.randomUUID().toString().replace("-", "");

        // 刷新令牌仅向客户端返回明文；入库前做 SHA-256 哈希存储，降低数据库泄漏风险
        UserSession session = buildUserSession(user, token, jwtUtils.sha256Hex(refreshToken), remember, now, clientInfo);
        userSessionService.save(session);

        saveLoginLog(user.getId(), user.getUsername(), STATUS_SUCCESS, clientInfo, null);

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
    public ApiResponse<Map<String, Object>> register(String email, String username, String password,
                                                     String fullName, String role, String phone,
                                                     String code, ClientInfo clientInfo) {
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
        passwordComponent.validatePasswordComplexity(password);

        // 验证码校验（强制，不可跳过）
        // 安全修复 B1（2026-09-16 上线前全检阻塞项）：原实现为 `if (code != null)` 的**可选**分支——
        // 请求省略 code 即可跳过整段校验，直接建号并下发认证 Cookie，攻击者可零成本批量自建
        // 已认证账号（运行时已实测复现：带 email、不含 code 的 POST /auth/register → 200 且可立即登录）。
        // 现改为「缺失即拒绝」，并与 DTO 层 @NotBlank 构成两道拦截。
        if (code == null || code.isBlank()) {
            throw new BusinessException("注册验证码不能为空");
        }
        // 与找回密码共用同一套 Redis 尝试限流：注册路径此前未挂，等同给了一个
        // 「不限次数枚举验证码」的旁路，这里补齐。
        AuthVerificationComponent.assertVerifyAttemptAllowed(redisTemplate, email);
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
        user.setRegisterIp(clientInfo.getIpAddress());
        userService.save(user);

        LocalDateTime now = LocalDateTime.now();
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole(), false);
        String refreshToken = UUID.randomUUID().toString().replace("-", "");

        // 创建会话（刷新令牌哈希存储）
        UserSession session = buildUserSession(user, token, jwtUtils.sha256Hex(refreshToken), false, now, clientInfo);
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
    public ApiResponse<Void> logout(Long userId, ClientInfo clientInfo) {
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

        saveLoginLog(userId, null, STATUS_LOGOUT, clientInfo, "安全退出");
        return ApiResponse.success("退出成功", null);
    }

    // ===================== 获取当前登录用户信息 =====================
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

    // ===================== 私有工具方法 =====================
    /**
     * 构建用户会话对象
     */
    private UserSession buildUserSession(User user, String token, String refreshToken,
                                         boolean rememberMe, LocalDateTime now, ClientInfo clientInfo) {
        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setSessionToken(token);
        session.setRefreshToken(refreshToken);
        session.setIsActive(true);
        session.setIsCurrent(true);
        session.setIpAddress(clientInfo.getIpAddress());
        session.setDeviceName(clientInfo.getDeviceName());
        session.setBrowser(clientInfo.getBrowser());
        session.setOperatingSystem(clientInfo.getOperatingSystem());
        session.setUserAgent(clientInfo.getUserAgent());
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
                              ClientInfo clientInfo, String reason) {
        LoginLog log = new LoginLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setLoginStatus(status);
        log.setIpAddress(clientInfo.getIpAddress());
        log.setDeviceName(clientInfo.getDeviceName());
        log.setBrowser(clientInfo.getBrowser());
        log.setOperatingSystem(clientInfo.getOperatingSystem());
        log.setUserAgent(clientInfo.getUserAgent());
        log.setFailureReason(reason);
        loginLogService.save(log);
    }

    // ===================== 登录失败节流（P0-1） =====================

    /**
     * 失败计数键：按「来源 IP + 账户」隔离，避免跨 IP 的账户锁定 DoS。
     * <p>同一 IP 同一账户的失败累计到阈值仅影响该组合；受害者由自身 IP 登录不受影响。</p>
     */
    private String loginFailKey(ClientInfo clientInfo, String account) {
        String ip = (clientInfo == null || clientInfo.getIpAddress() == null)
                ? "unknown" : clientInfo.getIpAddress();
        String acct = account == null ? "" : account.trim().toLowerCase();
        return LOGIN_FAIL_KEY_PREFIX + ip + ":" + acct;
    }

    /**
     * 节流前置校验：该 (IP, 账户) 已达失败阈值时直接拒绝（窗口内**含正确密码**亦拒绝，保持锁定语义）。
     * <p>Redis 不可用时降级放行（与 {@link AuthVerificationComponent} 一致），避免缓存故障导致全量登录不可用。</p>
     */
    private void assertLoginNotThrottled(ClientInfo clientInfo, String account) {
        if (redisTemplate == null) {
            return;
        }
        try {
            String value = redisTemplate.opsForValue().get(loginFailKey(clientInfo, account));
            if (value != null && Long.parseLong(value) >= MAX_FAILED_ATTEMPTS) {
                throw new BusinessException(429, "登录尝试过于频繁，请" + LOCK_MINUTES + "分钟后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("登录节流校验降级（Redis 异常）：{}", e.getMessage());
        }
    }

    /**
     * 记录一次登录失败；返回 {@code true} 表示已达阈值（调用方应返回 429）。
     * <p>用 Lua 脚本把 INCR 与 EXPIRE 原子化（首次计数才设窗口 TTL），避免「INCR 成功、EXPIRE 未执行」
     * 导致键永不过期、键空间无界增长。Redis 不可用时返回 {@code false} 降级放行。</p>
     */
    private boolean recordLoginFailure(ClientInfo clientInfo, String account) {
        if (redisTemplate == null) {
            return false;
        }
        try {
            String key = loginFailKey(clientInfo, account);
            Long count = redisTemplate.execute(LOGIN_FAIL_SCRIPT, List.of(key),
                    String.valueOf(LOGIN_FAIL_WINDOW.getSeconds()));
            return count != null && count >= MAX_FAILED_ATTEMPTS;
        } catch (Exception e) {
            log.warn("登录失败计数降级（Redis 异常）：{}", e.getMessage());
            return false;
        }
    }

    /** 登录成功后清除该 (IP, 账户) 的失败计数。 */
    private void clearLoginFailures(ClientInfo clientInfo, String account) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(loginFailKey(clientInfo, account));
        } catch (Exception e) {
            log.warn("登录失败计数清理降级（Redis 异常）：{}", e.getMessage());
        }
    }
}
