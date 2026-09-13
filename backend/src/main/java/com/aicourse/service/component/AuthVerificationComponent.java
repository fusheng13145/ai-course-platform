package com.aicourse.service.component;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.VerificationCode;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.EmailService;
import com.aicourse.service.VerificationCodeService;
import com.aicourse.common.ClientInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 认证-验证码组件：邮箱验证码发送 / 校验 + 验证码爆破防护滑动计数。
 * <p>拆分自原 AuthServiceImpl（2026-08-31）。作为门面 {@code AuthServiceImpl} 的内部协作者，
 * 由门面构造器按「验证码→密码→会话」无环依赖顺序装配（非 Spring 单例 Bean），
 * 以避免双实例与 Redis 缺失时的应用上下文启动失败。</p>
 */
@Slf4j
public class AuthVerificationComponent {

    // ===================== 常量定义 =====================
    private static final String TYPE_FORGOT_PASSWORD = "FORGOT_PASSWORD";
    private static final int RATE_LIMIT_MINUTES = 1;
    private static final int CODE_EXPIRE_MINUTES = 5;

    // ===================== 验证码爆破防护（P1-4 修复） =====================
    // 验证码为 6 位数字，若不限制尝试次数，攻击者可对已知邮箱暴力枚举重置任意用户密码。
    // 对「校验验证码」与「邮箱重置密码」两条路径共享 Redis 滑动计数：
    // 同一邮箱 5 分钟窗口内最多尝试 5 次，超出即拒绝（HTTP 429）。
    private static final String VC_ATTEMPT_KEY_PREFIX = "auth:vc:attempt:";
    private static final Duration VC_ATTEMPT_WINDOW = Duration.ofMinutes(5);
    private static final long VC_ATTEMPT_MAX = 5L;

    // ===================== 依赖 =====================
    private final VerificationCodeService verificationCodeService;
    private final EmailService emailService;
    /** 验证码尝试限流用 Redis（可空：无 Redis 时降级不计数） */
    private final StringRedisTemplate redisTemplate;

    public AuthVerificationComponent(VerificationCodeService verificationCodeService, EmailService emailService,
                                     StringRedisTemplate redisTemplate) {
        this.verificationCodeService = verificationCodeService;
        this.emailService = emailService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 验证码尝试限流：同一邮箱 5 分钟窗口内最多尝试 {@value #VC_ATTEMPT_MAX} 次。
     * 校验成功/失败均计数——成功一次无妨，失败计数可有效封死暴力枚举。
     */
    public void assertVerifyAttemptAllowed(String email) {
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

    // ===================== 发送邮箱验证码 =====================
    public ApiResponse<Void> sendVerificationCode(String email, String type, ClientInfo clientInfo) {
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
        vc.setIpAddress(clientInfo.getIpAddress());
        verificationCodeService.save(vc);

        // 发送邮件
        emailService.sendVerificationCode(email, code, type);
        return ApiResponse.success("验证码已发送到邮箱", null);
    }

    // ===================== 校验邮箱验证码有效性（仅校验，不消费） =====================
    /**
     * 校验邮箱验证码有效性（仅校验，不消费；供找回密码第二步「验证邮箱」使用）。
     * 与 resetPasswordByEmail 共享同一 Redis 尝试限流，防止暴力枚举。
     */
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
}
