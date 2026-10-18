package com.aicourse.config;

import java.util.List;

/**
 * 种子账号常量 —— 单一事实来源
 * <p>
 * V1 迁移脚本（{@code db/migration/V1__init_schema.sql}，与手动导入的 {@code index.sql} 同步）
 * 内置 8 个种子用户；V5/V6 富种子另建 162 个号段账号（{@code ts01..ts12} / {@code st001..st150}）。
 * 共 170 个账号全部共用同一枚 BCrypt 哈希，其明文为弱口令 {@code "123456"}。
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
     * V1 内置演示账号用户名（与 V1 种子 INSERT 的 username 列一一对应，顺序与脚本一致）。
     * <p>
     * 仅作名单信息保留：{@link DemoAccountHardeningInitializer} 的收敛判定已改为按
     * {@link #SEED_WEAK_BCRYPT} 等值匹配，不再用本名单作筛选条件——白名单会漏掉 V5/V6 的
     * 162 个号段账号（手册附录 D DR-18）。
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
