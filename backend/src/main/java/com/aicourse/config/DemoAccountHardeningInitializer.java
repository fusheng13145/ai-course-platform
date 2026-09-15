package com.aicourse.config;

import com.aicourse.entity.User;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 演示（种子）账号「生产加固」初始化器
 * <p>
 * 背景（F-02 密钥治理 P1 项）：V1 迁移内置 8 个演示账号，共用弱口令 {@code "123456"}。
 * 该弱口令无法从 V1 移除（脚本已 baseline，改动即校验和失配），因此必须在运行期收口。
 * <p>
 * 开关：{@code app.demo-accounts.enabled}（环境变量 {@code DEMO_ACCOUNTS_ENABLED}）。
 * <ul>
 *   <li><b>true（默认）</b>：保留演示账号，本地开发/测试/演示不受影响；若仍检测到种子弱口令，
 *       启动打 WARN 提示（不阻断）。</li>
 *   <li><b>false（生产必设）</b>：把「仍为种子弱口令」的演示账号 <em>停用</em>（{@code is_active=0}）
 *       并重置为<b>不可知</b>随机口令（随机明文仅用于生成哈希后立即丢弃，不落日志、不返回）。</li>
 * </ul>
 * 三条安全铁律：
 * <ol>
 *   <li><b>绝不覆盖已改密账号</b>：仅当口令仍等于 {@link SeedAccounts#SEED_WEAK_BCRYPT} 时才动手，
 *       管理员已重置过的账号保持原样（同名真实用户不会被误伤）。</li>
 *   <li><b>幂等</b>：重复启动不会重复改写（首次改写后口令已非种子哈希，自动跳过）。</li>
 *   <li><b>fail-fast</b>：{@code enabled=true} 与 {@code prod} profile 同时出现即拒绝启动，
 *       杜绝「忘记关开关」把演示弱口令带上生产（与项目「默认安全、开发显式放宽」一致）。</li>
 * </ol>
 */
@Component
@org.springframework.core.annotation.Order(20)
public class DemoAccountHardeningInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountHardeningInitializer.class);

    /** 生产姿态 profile：该 profile 下禁止演示账号以种子弱口令存活。 */
    private static final String PROD_PROFILE = "prod";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    /** 演示账号开关：true=保留（默认）；false=加固（生产）。 */
    @Value("${app.demo-accounts.enabled:true}")
    private boolean demoAccountsEnabled;

    public DemoAccountHardeningInitializer(UserService userService,
                                           PasswordEncoder passwordEncoder,
                                           Environment environment) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demoAccountsEnabled) {
            guardAgainstProd();
            warnIfSeedWeakPasswordRemains();
            return;
        }
        harden();
    }

    /**
     * 生产姿态下不允许开启演示账号：宁可拒绝启动，也不让弱口令静默上线。
     */
    private void guardAgainstProd() {
        for (String profile : environment.getActiveProfiles()) {
            if (PROD_PROFILE.equalsIgnoreCase(profile)) {
                throw new IllegalStateException(
                        "[seed] 配置冲突：app.demo-accounts.enabled=true 与 prod profile 不允许共存。"
                                + "生产环境必须设置 DEMO_ACCOUNTS_ENABLED=false"
                                + "（启动时自动停用演示账号并重置为不可知口令）。已拒绝启动。");
            }
        }
    }

    /**
     * 开关为 true 时的提醒：把「仍有种子弱口令存活」这一事实显式打到日志，
     * 避免部署者因默认值而误以为已经收敛。
     */
    private void warnIfSeedWeakPasswordRemains() {
        List<User> weak = loadSeedWeakAccounts();
        if (!weak.isEmpty()) {
            log.warn("[seed] 检测到 {} 个演示账号仍使用出厂弱口令(123456)：{}。"
                            + "本地开发可忽略；生产部署请设置 DEMO_ACCOUNTS_ENABLED=false 自动停用并改密，"
                            + "或通过管理端/APP_ADMIN_INITIAL_PASSWORD 重置口令。",
                    weak.size(), usernamesOf(weak));
        }
    }

    /**
     * 加固：停用仍为种子弱口令的演示账号，并把口令替换为不可知随机值。
     */
    private void harden() {
        List<User> candidates = loadSeedWeakAccounts();
        if (candidates.isEmpty()) {
            log.info("[seed] 演示账号加固：无待处理账号（已改密账号一律不覆盖）。");
            return;
        }
        List<String> hardened = new ArrayList<>(candidates.size());
        for (User user : candidates) {
            user.setPassword(passwordEncoder.encode(randomUnknownPassword()));
            user.setIsActive(false);
            userService.updateById(user);
            hardened.add(user.getUsername());
        }
        log.warn("[seed] 演示账号加固完成：{} 个账号已停用并重置为不可知随机口令（{}）。"
                        + "如需在受控环境恢复演示账号，请在非生产环境设置 DEMO_ACCOUNTS_ENABLED=true "
                        + "并重新初始化种子数据。",
                hardened.size(), String.join(", ", hardened));
    }

    /**
     * 载入「仍为种子弱口令」的演示账号（单次查询后内存过滤，避免 N 次往返）。
     */
    private List<User> loadSeedWeakAccounts() {
        List<User> users = userService.list(new LambdaQueryWrapper<User>()
                .in(User::getUsername, SeedAccounts.DEMO_USERNAMES));
        List<User> weak = new ArrayList<>();
        if (users == null) {
            return weak;
        }
        for (User user : users) {
            if (user != null && SeedAccounts.SEED_WEAK_BCRYPT.equals(user.getPassword())) {
                weak.add(user);
            }
        }
        return weak;
    }

    private static String usernamesOf(List<User> users) {
        List<String> names = new ArrayList<>(users.size());
        for (User user : users) {
            names.add(user.getUsername());
        }
        return String.join(", ", names);
    }

    /**
     * 生成一枚「不可知」口令明文：仅用于即时生成 BCrypt 哈希，随即被 GC 回收，
     * 既不落日志也不返回，确保加固后的账号无法被猜到或用种子口令登录。
     */
    private static String randomUnknownPassword() {
        byte[] buffer = new byte[32];
        RANDOM.nextBytes(buffer);
        return Base64.getEncoder().encodeToString(buffer);
    }
}
