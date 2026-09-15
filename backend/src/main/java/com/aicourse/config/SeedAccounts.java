package com.aicourse.config;

import java.util.List;

/**
 * 种子账号常量 —— 单一事实来源
 * <p>
 * V1 迁移脚本（{@code db/migration/V1__init_schema.sql}，与手动导入的 {@code index.sql} 同步）
 * 内置 8 个种子用户，全部共用同一枚 BCrypt 哈希，其明文为弱口令 {@code "123456"}。
 * 该哈希在项目中被多处需要（口令替换、加固校验），集中于此避免字符串散落多处后失配。
 * <p>
 * 【为何不能改 V1】{@code V1__init_schema.sql} 已 baseline 入库，修改会使 Flyway 校验和失配而拒绝启动。
 * 因此「种子弱口令」只能靠运行期加固（见 {@link DemoAccountHardeningInitializer}）或后续迁移收敛。
 */
public final class SeedAccounts {

    private SeedAccounts() {
    }

    /**
     * 全部种子用户共用的 BCrypt 哈希（明文 {@code "123456"}）。
     * <p>
     * 该值本身并非密钥，仅用于「是否仍为出厂弱口令」的等值判定；明文弱口令才是不安全项。
     */
    public static final String SEED_WEAK_BCRYPT =
            "$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq";

    /**
     * 种子演示账号用户名（与 V1 种子 INSERT 的 username 列一一对应，顺序与脚本一致）。
     * <p>
     * 按用户名而非邮箱识别，便于「同名但已改密的真实用户」被安全跳过。
     */
    public static final List<String> DEMO_USERNAMES = List.of(
            "admin",
            "teacher",
            "teacher2",
            "student",
            "student2",
            "student3",
            "student4",
            "student5");
}
