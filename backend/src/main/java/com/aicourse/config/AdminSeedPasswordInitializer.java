package com.aicourse.config;

import com.aicourse.entity.User;
import com.aicourse.service.UserService;
import com.aicourse.util.PasswordPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 种子 admin 账号口令初始化器
 * <p>
 * 修复：{@code index.sql} 写死 admin 账号的固定 BCrypt 哈希（弱口令），
 * 生产环境一旦执行种子脚本即产生"已知口令"管理员账户。
 * <p>
 * 启用方式：设置环境变量/配置项 {@code APP_ADMIN_INITIAL_PASSWORD}（或
 * {@code app.admin.initial-password}）。启动时：
 * <ol>
 *   <li>未配置 → 直接跳过（测试上下文等不受影响）；</li>
 *   <li>admin 密码仍为种子默认哈希 → 重置为配置值并打告警日志；</li>
 *   <li>admin 密码已非默认（管理员已改密）→ 保持现状，绝不覆盖。</li>
 * </ol>
 * 初始密码同样必须满足 {@link PasswordPolicy} 复杂度要求，否则启动失败（fail-fast）。
 */
@Component
public class AdminSeedPasswordInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedPasswordInitializer.class);

    /** index.sql 中 admin 账号的种子默认哈希（弱口令，必须被替换） */
    private static final String SEED_ADMIN_HASH =
            "$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq";

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.initial-password:}")
    private String initialPassword;

    public AdminSeedPasswordInitializer(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (initialPassword == null || initialPassword.isBlank()) {
            return;
        }
        // fail-fast：初始密码必须满足统一复杂度策略，避免弱口令以"配置"名义落地
        try {
            PasswordPolicy.validate(initialPassword);
        } catch (RuntimeException e) {
            log.error("[seed] APP_ADMIN_INITIAL_PASSWORD 不满足密码复杂度要求，拒绝启动：{}", e.getMessage());
            throw e;
        }

        User admin = userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "admin")
                .last("LIMIT 1"), false);
        if (admin == null) {
            log.warn("[seed] 未找到 admin 账号，跳过初始密码注入");
            return;
        }
        if (!SEED_ADMIN_HASH.equals(admin.getPassword())) {
            log.info("[seed] admin 密码已非种子默认值，保持现状（不覆盖管理员已修改的口令）");
            return;
        }
        admin.setPassword(passwordEncoder.encode(initialPassword));
        userService.updateById(admin);
        log.warn("[seed] admin 种子默认口令已替换为 APP_ADMIN_INITIAL_PASSWORD 配置值，"
                + "请妥善保管并在首次登录后尽快修改");
    }
}
