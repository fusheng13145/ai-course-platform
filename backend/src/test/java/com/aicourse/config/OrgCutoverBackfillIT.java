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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V16/V17 收口迁移验收（B-12-P-08 WP7/WP8 快照与回填 + WP10 4A 学期内唯一，failsafe IT）。
 *
 * <p>判据（拆包方案 WP7 验收 1、WP10 验收 1/3，在真实 MySQL 8.4 上执行）：</p>
 * <ol>
 *   <li>四类活动记录的 {@code teaching_class_id} 快照列存在（回填口径为 0 空值，空库下即列存在）；</li>
 *   <li>{@code announcements} 表就位（WP8）；</li>
 *   <li>{@code courses.semester_id} NOT NULL 且 {@code uk_courses_semester_code} 生效、
 *       旧 {@code uk_courses_code} 移除；同学期同编号被拒、跨学期同编号放行（4A 行为断言）；</li>
 *   <li>{@code course_enrollments.teaching_class_id} 收紧为 NOT NULL；</li>
 *   <li>重复 migrate 不再执行新版本（幂等）。</li>
 * </ol>
 */
@Testcontainers
class OrgCutoverBackfillIT {

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
    @DisplayName("V16/V17 收口：快照列、公告表、学期内唯一键与收紧项全部就位，且可重复执行")
    void migratesAndEnforcesSemesterScopedCode() throws SQLException {
        Flyway flyway = flyway();
        // 期望值不再写死 14：V18 起迁移数会随批次增长，写死等于让「新增一支迁移」变成红灯。
        // 空库上 pending 就是全部版本（当前 V1..V18），既守住「一次全执行完」，又不因加版本误报。
        int expected = flyway.info().pending().length;
        MigrateResult first = flyway.migrate();
        assertEquals(expected, first.migrationsExecuted,
                "空库应一次性执行全部待应用版本（当前 " + expected + " 个）");

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {

            // 1) 四类活动记录的快照列（WP7）
            for (String table : new String[]{"submissions", "task_completions",
                    "exam_sessions", "grade_items", "discussion_topics"}) {
                assertEquals(1, columnCount(connection, table, "teaching_class_id"),
                        table + " 必须带 teaching_class_id 快照列");
            }
            // 2) 公告表（WP8）
            assertEquals(1, tableCount(connection, "announcements"), "announcements 必须就位");
            // 4a) 收紧生效
            assertEquals("NO", columnNullable(connection, "courses", "semester_id"),
                    "courses.semester_id 应回填后收紧为 NOT NULL");
            assertEquals("NO", columnNullable(connection, "course_enrollments", "teaching_class_id"),
                    "course_enrollments.teaching_class_id 应回填后收紧为 NOT NULL");
            // 3) 4A：唯一键切换
            assertEquals(0, indexCount(connection, "courses", "uk_courses_code"),
                    "全库唯一 uk_courses_code 必须移除");
            assertEquals(1, indexCount(connection, "courses", "uk_courses_semester_code"),
                    "学期内唯一键必须就位");

            // 4A 行为断言：同学期同编号被拒；跨学期同编号放行
            try (Statement statement = connection.createStatement()) {
                // 不写死学期名/id：V14 已种下 '2025-2026-2' 与 '2026-2027-1'，
                // 再插同名会撞 uk_semesters_name（CI #244 实测 Duplicate entry '2025-2026-2'）；
                // 自增 id 也不保证从 1 起。改为造两个本次专属学期并取回真实 id。
                long spring = insertSemester(statement, "IT-SPRING-" + System.nanoTime(), "2025-09-01", "2026-01-31");
                long autumn = insertSemester(statement, "IT-AUTUMN-" + System.nanoTime(), "2026-02-23", "2026-07-05");
                statement.execute("INSERT INTO courses (name, code, semester_id, teacher_id, "
                        + "max_students, status, is_active, created_at) VALUES "
                        + "('计算机网络', 'CS101', " + spring + ", 1, 50, 'ACTIVE', 1, '2025-09-10 00:00:00')");
                // 跨学期同编号：放行
                statement.execute("INSERT INTO courses (name, code, semester_id, teacher_id, "
                        + "max_students, status, is_active, created_at) VALUES "
                        + "('计算机网络（春）', 'CS101', " + autumn + ", 1, 50, 'ACTIVE', 1, '2026-03-01 00:00:00')");
                // 同学期同编号：唯一键拒绝
                assertThrows(SQLException.class, () -> statement.execute(
                        "INSERT INTO courses (name, code, semester_id, teacher_id, "
                                + "max_students, status, is_active, created_at) VALUES "
                                + "('重复编号', 'CS101', " + spring + ", 1, 50, 'ACTIVE', 1, '2025-10-01 00:00:00')"),
                        "同学期内重复编号必须被 uk_courses_semester_code 拒绝");
            }

            // 5) 幂等：再次 migrate 无新版本
            MigrateResult second = flyway().migrate();
            assertEquals(0, second.migrationsExecuted, "重复执行不得重放任何迁移");
        }
    }

    private static int columnCount(Connection connection, String table, String column) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                             + " AND TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static int tableCount(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()"
                             + " AND TABLE_NAME = '" + table + "'")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static String columnNullable(Connection connection, String table, String column) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                             + " AND TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    /** 造一个本次专属学期并取回自增 id（避开 V14 种子名与不保证从 1 起的自增序列）。 */
    private static long insertSemester(Statement statement, String name, String start, String end)
            throws SQLException {
        statement.executeUpdate("INSERT INTO semesters (name, start_date, end_date, status) VALUES "
                + "('" + name + "', '" + start + "', '" + end + "', 'ACTIVE')",
                Statement.RETURN_GENERATED_KEYS);
        try (ResultSet keys = statement.getGeneratedKeys()) {
            assertTrue(keys.next(), "插入学期后应能取回自增 id");
            return keys.getLong(1);
        }
    }

    private static int indexCount(Connection connection, String table, String indexName) throws SQLException {
        // 复合索引在 information_schema.STATISTICS 里是**每列一行**（uk_courses_semester_code 有
        // semester_id／code 两行），COUNT(*) 会读成 2。这里数 DISTINCT INDEX_NAME，
        // 语义是「该索引名存不存在／有几条定义」，与断言里的 1/0 对应。
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS"
                             + " WHERE TABLE_SCHEMA = DATABASE()"
                             + " AND TABLE_NAME = '" + table + "' AND INDEX_NAME = '" + indexName + "'")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
