package com.aicourse.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 自定义 MySQL 健康指示器（显式实现 {@link HealthIndicator}）。
 *
 * <p>Spring Boot 默认的 {@code DataSourceHealthIndicator} 已被关闭
 * （{@code management.health.db.enabled=false}），这里改为显式验证数据源连通性：
 * 通过数据源获取连接并执行 {@code SELECT 1}，返回 UP/DOWN 与健康详情。</p>
 *
 * <p>健康检查详情（URL/账号等敏感信息）不会在此处输出，避免泄露。</p>
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 1")) {

            if (rs.next() && rs.getInt(1) == 1) {
                return Health.up()
                        .withDetail("database", "MySQL")
                        .withDetail("validationQuery", "SELECT 1")
                        .withDetail("status", "reachable")
                        .build();
            }
            return Health.down()
                    .withDetail("database", "MySQL")
                    .withDetail("status", "validation query returned no rows")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("database", "MySQL")
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("message", e.getMessage())
                    .build();
        }
    }
}
