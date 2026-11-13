package com.aicourse.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V12 组织底座迁移验收（B-12-P-08 WP1 验收项 1，集成测试，由 failsafe 执行）。
 *
 * <p>在空库上直接执行 {@code classpath:db/migration} 全部迁移，验证：</p>
 * <ol>
 *   <li>{@code schools} 恰 1 行（单校根）；</li>
 *   <li>存量 {@code ADMIN} 账号回填 {@code admin_scope='SCHOOL'} 且绑定根学校；</li>
 *   <li>{@code role_permissions} 具备 {@code scope}（NOT NULL DEFAULT 'ALL'）且唯一键已替换；</li>
 *   <li>{@code users} 五个组织列全部存在；</li>
 *   <li>再次 {@code migrate()} 不报错、不重复执行（可重复运行）。</li>
 * </ol>
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrgFoundationMigrationIT {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4.11"))
                    .withDatabaseName("ai_course_platform")
                    .withUsername("test")
                    .withPassword("test")
                    .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    private static Flyway flyway() {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                // 「空库迁移」这条判据必须真的从空库出发：容器在本 JVM 内被两个 @Test 共享，
                // 若另一个用例先跑，版本已被全部应用，再 migrate 就是 0 条（CI #238 实测 was:<0>）。
                .cleanDisabled(false)
                .load();
    }

    @Test
    @Order(1)
    @DisplayName("空库迁移：组织四表、ADMIN 作用域与权限唯一键全部就位，且可重复执行")
    void migratesEmptyDatabase() throws SQLException {
        Flyway flyway = flyway();
        flyway.clean();
        // 期望值不再写死 14：V18 起迁移数随批次增长，空库上 pending 即全部版本。
        int expected = flyway.info().pending().length;
        MigrateResult first = flyway.migrate();
        assertEquals(expected, first.migrationsExecuted,
                "空库应一次性执行全部待应用版本（当前 " + expected + " 个）");

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM schools"),
                    "单校部署下 schools 应有且仅有一行");

            String adminScope = queryString(connection,
                    "SELECT admin_scope FROM users WHERE role = 'ADMIN' LIMIT 1");
            assertEquals("SCHOOL", adminScope, "存量 ADMIN 必须回填为校级");

            assertEquals(1, queryInt(connection,
                    "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND school_id IS NOT NULL"),
                    "存量 ADMIN 必须绑定根学校");

            for (String column : new String[]{"school_id", "college_id", "major_id",
                    "admin_class_id", "admin_scope"}) {
                assertEquals(1, queryInt(connection,
                        "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                                + " AND TABLE_NAME = 'users' AND COLUMN_NAME = '" + column + "'"),
                        "users 缺少 V12 列：" + column);
            }

            assertEquals(1, queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                            + " AND TABLE_NAME = 'role_permissions' AND COLUMN_NAME = 'scope'"
                            + " AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT = 'ALL'"),
                    "role_permissions.scope 必须 NOT NULL DEFAULT 'ALL'");

            assertEquals(1, queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()"
                            + " AND TABLE_NAME = 'role_permissions'"
                            + " AND INDEX_NAME = 'uk_role_permissions_role_scope_key' AND SEQ_IN_INDEX = 1"),
                    "新唯一键 (role, scope, permission_key) 必须存在");
            assertEquals(0, queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()"
                            + " AND TABLE_NAME = 'role_permissions'"
                            + " AND INDEX_NAME = 'uk_role_permissions_role_key'"),
                    "旧唯一键必须已删除");
        }

        MigrateResult second = flyway().migrate();
        assertEquals(0, second.migrationsExecuted, "重复 migrate 不得再次执行任何版本");

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM schools"),
                    "重复执行不得重复插入学校根");
        }
    }

    @Test
    @Order(2)
    @DisplayName("权限目录唯一键：同 role 同 key 不同 scope 可共存，相同三元组二次插入被唯一约束拒绝")
    void permissionScopeUniqueKeyEnforced() throws SQLException {
        flyway().migrate();

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO role_permissions"
                        + " (role, scope, permission_key, permission_name)"
                        + " VALUES ('ADMIN', 'ALL', 'wp2:scope:demo', '作用域演示')");
                statement.executeUpdate("INSERT INTO role_permissions"
                        + " (role, scope, permission_key, permission_name)"
                        + " VALUES ('ADMIN', 'COLLEGE', 'wp2:scope:demo', '作用域演示')");
            }
            assertEquals(2, queryInt(connection,
                    "SELECT COUNT(*) FROM role_permissions WHERE permission_key = 'wp2:scope:demo'"),
                    "同 role 同 key 不同 scope 必须允许共存");

            assertThrows(SQLException.class, () -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO role_permissions"
                            + " (role, scope, permission_key, permission_name)"
                            + " VALUES ('ADMIN', 'COLLEGE', 'wp2:scope:demo', '作用域演示')");
                }
            }, "同一 (role, scope, permission_key) 三元组重复插入必须被唯一键拒绝");
        }
    }

    private static int queryInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next(), "查询无结果：" + sql);
            return resultSet.getInt(1);
        }
    }

    private static String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                return null;
            }
            return resultSet.getString(1);
        }
    }
}
