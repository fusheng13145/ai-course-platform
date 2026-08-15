package com.aicourse.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ShedLock 分布式调度锁配置。
 * 多实例部署时，{@code @SchedulerLock} 标注的定时任务（如考试超时收卷）通过 Redis 抢占锁，
 * 保证同一时刻只有一个节点执行，杜绝重复扫描 / 重复收卷。
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
public class SchedulerLockConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulerLockConfig.class);

    /**
     * 基于 Redis 的分布式锁提供者：多实例部署下保证同一定时任务仅一个节点执行。
     *
     * @param connectionFactory Redis 连接工厂
     * @return ShedLock 锁提供者
     */
    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        log.info("[SchedulerLockConfig] 初始化基于 Redis 的 ShedLock 分布式调度锁");
        return new RedisLockProvider(connectionFactory);
    }
}
