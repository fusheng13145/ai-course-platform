package com.aicourse.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * 自定义 Redis 健康指示器（显式实现 {@link HealthIndicator}）。
 *
 * <p>Spring Boot 默认的 {@code RedisHealthContributor} 已被关闭
 * （{@code management.health.redis.enabled=false}），这里改为显式验证 Redis 连通性：
 * 通过连接工厂获取连接并发送 {@code PING}，返回 UP/DOWN 与健康详情。</p>
 */
@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            if (pong != null && !pong.isEmpty()) {
                return Health.up()
                        .withDetail("redis", pong)
                        .withDetail("status", "reachable")
                        .build();
            }
            return Health.down()
                    .withDetail("redis", "empty ping response")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("message", e.getMessage())
                    .build();
        }
    }
}
