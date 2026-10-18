package com.aicourse.config;

import com.aicourse.common.RoleConstants;
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
 * 背景（F-02 密钥治理 P1 项）：迁移脚本内置共 170 个种子账号（V1 的 8 个 + V5/V6 的
 * {@code ts01..ts12} / {@code st001..st150}），共用弱口令 {@code "123456"}。
 * 该弱口令无法从脚本移除（已 baseline 的脚本改动即校验和失配），因此必须在运行期收口。
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

    /** 启动日志中列举种子账号名时的上限。 */
    private static final int LOG_NAME_PREVIEW = 10;

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
        // 【关键顺序】先判定、再动手：若判定在改写之后，抛错时账号已被停用，
        // 下次启动因「已无种子弱口令账号」而直接跳过判定 → 系统被永久锁死且无法自救。
        assertAdminRemainsAfter(candidates);
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
                hardened.size(), preview(hardened));
    }

    /**
     * 前置判定：加固后必须仍存在「可登录的管理员」，否则拒绝启动。
     *
     * <p>加固会把仍用种子弱口令的账号<b>一律停用</b>，其中包含内置 {@code admin}。
     * 若部署者未提供 {@code APP_ADMIN_INITIAL_PASSWORD}，则 {@link AdminSeedPasswordInitializer}
     * （{@code @Order(10)}，先于本类执行）不会替换 admin 的口令，加固完成后将<b>不存在任何可用管理员</b>，
     * 且无法再通过管理端自救（无账号可登录）——属静默锁死。</p>
     *
     * <p>故此处宁可 fail-fast 并给出可执行指引，也不让"零管理员"的库上线。
     * 若库中已有其它有效 ADMIN 账号（运维自行创建），判定自然通过。</p>
     */
    private void assertAdminRemainsAfter(List<User> candidates) {
        long adminsToDisable = 0L;
        for (User user : candidates) {
            if (RoleConstants.ADMIN.equals(user.getRole()) && Boolean.TRUE.equals(user.getIsActive())) {
                adminsToDisable++;
            }
        }
        if (adminsToDisable == 0L) {
            // 本次加固不涉及任何管理员账号 → 不存在「加固导致零管理员」的风险，直接放行。
            return;
        }
        long activeAdmins = userService.count(new LambdaQueryWrapper<User>()
                .eq(User::getRole, RoleConstants.ADMIN)
                .eq(User::getIsActive, true));
        if (activeAdmins - adminsToDisable <= 0) {
            throw new IllegalStateException(
                    "[seed] 加固后将不存在可用管理员，已拒绝启动。原因：内置 admin 仍为出厂弱口令，"
                            + "而 APP_ADMIN_INITIAL_PASSWORD 未设置（AdminSeedPasswordInitializer 未替换其口令）；"
                            + "加固会把它一并停用。请在 .env 设置 APP_ADMIN_INITIAL_PASSWORD"
                            + "（8-32 位，含大小写字母、数字与特殊字符）后重启，"
                            + "或先由运维创建另一个有效 ADMIN 账号。");
        }
        log.info("[seed] 加固前校验通过：当前有效管理员 {} 个，本次将停用其中 {} 个，加固后仍有可用管理员。",
                activeAdmins, adminsToDisable);
    }

    /**
     * 载入「仍为种子弱口令」的演示账号（单次查询后内存过滤，避免 N 次往返）。
     * <p>
     * 判定口径是<b>口令哈希本身</b>，不是用户名白名单：V1/V5/V6 的 170 个种子账号共用同一枚
     * BCrypt 串，白名单只覆盖 V1 的 8 个，会把 V5/V6 号段（{@code ts01..ts12} /
     * {@code st001..st150}）留在弱口令上——Flyway 在 prod 也自动执行到 V6，故必须按哈希收敛。
     * 正常注册/改密产生的口令盐值不同，不会被此条件命中。
     */
    private List<User> loadSeedWeakAccounts() {
        List<User> users = userService.list(new LambdaQueryWrapper<User>()
                .eq(User::getPassword, SeedAccounts.SEED_WEAK_BCRYPT));
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
        return preview(names);
    }

    /** 日志只列前若干账号：种子号段可达 170 个，全量打印会淹没启动日志。 */
    private static String preview(List<String> names) {
        if (names.size() <= LOG_NAME_PREVIEW) {
            return String.join(", ", names);
        }
        return String.join(", ", names.subList(0, LOG_NAME_PREVIEW))
                + " 等 " + names.size() + " 个";
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
