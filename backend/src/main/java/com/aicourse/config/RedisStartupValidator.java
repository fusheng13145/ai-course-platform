package com.aicourse.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Redis 启动强校验器（Redis 必选基础设施）。
 *
 * <p>项目缓存（@Cacheable/RedisCacheManager）、IP 限流、AI 配额、验证码、
 * 分布式调度锁均依赖 Redis。此前 Redis 为"可选"：连接惰性建立，Redis 宕机时
 * 应用照常启动，运行期缓存请求静默抛异常，问题暴露滞后且难以定位。</p>
 *
 * <p>本校验器在应用启动完成前主动 PING Redis：</p>
 * <ul>
 *   <li>连接成功 → 记录日志，正常启动；</li>
 *   <li>连接失败 → 按 {@code app.redis.startup-check.max-retries} 重试
 *       （默认 10 次、间隔 3s，容忍 Docker 容器冷启动），全部失败则抛出异常
 *       中止应用启动（fail-fast），避免"半可用"状态上线。</li>
 * </ul>
 *
 * <p>开关：{@code app.redis.required}（默认 true）。
 * 单元测试（service-test profile 已排除 RedisAutoConfiguration）通过
 * {@code app.redis.required=false} 关闭本校验；集成测试由 Testcontainers
 * 提供真实 Redis，保持开启。</p>
 */
@Component
@Order(1)
@ConditionalOnProperty(prefix = "app.redis", name = "required", havingValue = "true", matchIfMissing = true)
public class RedisStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisStartupValidator.class);

    private final RedisConnectionFactory redisConnectionFactory;

    /** 最大重试次数（默认 10 次，容忍 docker compose 中 Redis 容器冷启动） */
    private final int maxRetries;

    /** 重试间隔毫秒（默认 3000ms） */
    private final long retryIntervalMs;

    public RedisStartupValidator(
            RedisConnectionFactory redisConnectionFactory,
            @org.springframework.beans.factory.annotation.Value("${app.redis.startup-check.max-retries:10}") int maxRetries,
            @org.springframework.beans.factory.annotation.Value("${app.redis.startup-check.retry-interval-ms:3000}") long retryIntervalMs) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.maxRetries = maxRetries;
        this.retryIntervalMs = retryIntervalMs;
    }

    @Override
    public void run(ApplicationArguments args) {
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (RedisConnection connection = redisConnectionFactory.getConnection()) {
                String pong = connection.ping();
                log.info("[RedisStartupValidator] Redis 连通性校验通过（PING -> {}，第 {} 次尝试）", pong, attempt);
                return;
            } catch (Exception e) {
                lastError = e;
                log.warn("[RedisStartupValidator] Redis 连接失败（第 {}/{} 次）：{}，{}ms 后重试",
                        attempt, maxRetries, e.getMessage(), retryIntervalMs);
                try {
                    Thread.sleep(retryIntervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Redis 启动校验被中断", ie);
                }
            }
        }
        throw new IllegalStateException(
                "Redis 为本系统必选基础设施（缓存/限流/AI配额/验证码依赖），但在 "
                        + maxRetries + " 次重试后仍无法连接。请先启动 Redis（docker compose up -d redis）"
                        + "或检查 SPRING_DATA_REDIS_HOST/PORT 配置。原始错误: "
                        + (lastError != null ? lastError.getMessage() : "unknown"),
                lastError);
    }
}
