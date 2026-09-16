package com.aicourse.service.component.auth;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.User;
import com.aicourse.entity.VerificationCode;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.UserService;
import com.aicourse.service.UserSessionService;
import com.aicourse.service.VerificationCodeService;
import com.aicourse.util.PasswordPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

/**
 * 认证-密码组件：手机号重置 / 已知原密码修改 / 邮箱重置 三类密码操作 + 密码复杂度统一收口。
 * <p>拆分自原 AuthServiceImpl（2026-08-31）。作为门面 {@code AuthServiceImpl} 的内部协作者，
 * 由门面构造器装配。邮箱重置路径复用 {@link AuthVerificationComponent#assertVerifyAttemptAllowed(String)}
 * 共享验证码爆破防护；密码复杂度统一收口到 {@link PasswordPolicy}，杜绝弱规则旁路。</p>
 *
 * <p>【修复 #10b】三条密码变更路径（改密 / 手机重置 / 邮箱重置）在写库成功之后，
 * 一律全量吊销该用户的所有活跃会话。否则「窃得会话 → 受害者改密止损」这一最关键的自救动作
 * 形同虚设：JWT 验签天然无状态，不改会话表/缓存的话窃得的令牌一直可用。
 * 代价是发起改密的那一条会话也一并失效（下一请求 401），故成功文案统一改为「请重新登录」。</p>
 */
@Slf4j
public class AuthPasswordComponent {

    // ===================== 常量定义 =====================
    private static final String TYPE_RESET_PASSWORD = "RESET_PASSWORD";
    private static final String TYPE_FORGOT_PASSWORD = "FORGOT_PASSWORD";

    // ===================== 依赖 =====================
    private final UserService userService;
    private final VerificationCodeService verificationCodeService;
    private final PasswordEncoder passwordEncoder;
    private final AuthVerificationComponent verificationComponent;
    /** 修复 #10b：密码变更后用于全量吊销该用户的活跃会话。 */
    private final UserSessionService userSessionService;

    /**
     * @param userService            用户读写
     * @param verificationCodeService 验证码读写
     * @param passwordEncoder        密码加密器
     * @param verificationComponent  验证码爆破防护组件
     * @param userSessionService     会话服务（改密后吊销会话，修复 #10b）
     */
    public AuthPasswordComponent(UserService userService, VerificationCodeService verificationCodeService,
                                 PasswordEncoder passwordEncoder, AuthVerificationComponent verificationComponent,
                                 UserSessionService userSessionService) {
        this.userService = userService;
        this.verificationCodeService = verificationCodeService;
        this.passwordEncoder = passwordEncoder;
        this.verificationComponent = verificationComponent;
        this.userSessionService = userSessionService;
    }

    /**
     * 修复 #10b：密码写库成功后吊销该用户全部活跃会话。
     *
     * <p>顺序必须是「先改密、后吊销」：若先吊销，会话失效与密码更新之间的竞态窗口内
     * 一旦改密失败回滚，用户会被无故踢下线且密码仍是旧的。</p>
     *
     * <p>吊销失败只记日志、不影响改密结果 —— 密码本身已改成功，业务主流程应当成功返回；
     * 但必须显式暴露在日志里，便于排查「改密后会话仍存活」的异常。</p>
     *
     * @param userId 用户ID
     */
    private void revokeSessionsAfterPasswordChange(Long userId) {
        if (userSessionService == null || userId == null) {
            return;
        }
        try {
            int revoked = userSessionService.revokeAllActiveSessions(userId);
            log.info("[auth] 密码已变更，吊销该用户全部活跃会话: userId={}, revoked={}", userId, revoked);
        } catch (Exception e) {
            log.error("[auth] 密码已变更，但吊销会话失败（需人工核查）: userId={}", userId, e);
        }
    }

    // ===================== 密码复杂度验证 =====================
    // 规则（统一收口到 com.aicourse.util.PasswordPolicy）：8-32位，大小写字母+数字+特殊字符。
    // 注册/改密/重置（手机+邮箱）/管理端创建与重置用户密码均走同一策略。
    public void validatePasswordComplexity(String password) {
        PasswordPolicy.validate(password);
    }

    // ===================== 手机号找回密码 =====================
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
        // 修复 #10b：先落库改密，再吊销全部会话（含窃得者手里的那一条）
        revokeSessionsAfterPasswordChange(user.getId());
        return ApiResponse.success("密码已重置，请重新登录", null);
    }

    // ===================== 修改密码（已知原密码） =====================
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
        // 修复 #10b：先落库改密，再吊销全部会话 —— 当前这条会话同样失效，
        // 因此返回文案必须提示用户重新登录（下一请求即为 401）。
        revokeSessionsAfterPasswordChange(user.getId());
        return ApiResponse.success("密码已修改，请重新登录", null);
    }

    // ===================== 邮箱找回密码 =====================
    public ApiResponse<Void> resetPasswordByEmail(String email, String code, String newPassword) {
        log.info("重置认证");

        // 修复 P1-4：验证码爆破防护——尝试计数前置，超限直接拒绝（429）
        verificationComponent.assertVerifyAttemptAllowed(email);

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
        // 修复 #10b：先落库改密，再吊销全部会话（含窃得者手里的那一条），当前会话同样失效
        revokeSessionsAfterPasswordChange(user.getId());
        return ApiResponse.success("密码已重置，请重新登录", null);
    }
}
