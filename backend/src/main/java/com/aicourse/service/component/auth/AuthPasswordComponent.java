package com.aicourse.service.component.auth;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.User;
import com.aicourse.entity.VerificationCode;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.UserService;
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

    public AuthPasswordComponent(UserService userService, VerificationCodeService verificationCodeService,
                                 PasswordEncoder passwordEncoder, AuthVerificationComponent verificationComponent) {
        this.userService = userService;
        this.verificationCodeService = verificationCodeService;
        this.passwordEncoder = passwordEncoder;
        this.verificationComponent = verificationComponent;
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
        return ApiResponse.success("密码重置成功", null);
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
        return ApiResponse.success("密码修改成功", null);
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

        return ApiResponse.success("密码重置成功", null);
    }
}
