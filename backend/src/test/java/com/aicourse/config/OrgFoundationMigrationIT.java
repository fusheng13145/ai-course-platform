package com.aicourse.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
                .load();
    }

    @Test
    @DisplayName("空库迁移：组织四表、ADMIN 作用域与权限唯一键全部就位，且可重复执行")
    void migratesEmptyDatabase() throws SQLException {
        MigrateResult first = flyway().migrate();
        assertEquals(9, first.migrationsExecuted, "V1-V4、V8-V12 共 9 个版本应全部执行");

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
