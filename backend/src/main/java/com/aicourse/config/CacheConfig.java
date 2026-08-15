package com.aicourse.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Redis 缓存管理器配置
 * <p>
 * 统一管理各缓存名称的序列化方式与 TTL（JSON 序列化 + JavaTime 支持，避免 LocalDateTime 字段序列化失败）。
 * 业务层 {@code @Cacheable} 使用的缓存名均在此显式注册，未注册者走默认 30 分钟 TTL。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /**
     * 构建 Redis 缓存管理器，按缓存名配置差异化 TTL 与 JSON 值序列化。
     *
     * @param factory Redis 连接工厂
     * @return 缓存管理器
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        // 使用带 JavaTimeModule 的 ObjectMapper，避免缓存实体（含 LocalDateTime 字段）时序列化失败
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(RedisObjectMapperFactory.create());
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        // 关键：每个缓存的自定义 TTL 必须基于已配置好 JSON 序列化器的 config 派生，
        // 而不能重新调用 RedisCacheConfiguration.defaultCacheConfig()（其默认值序列化器是
        // JdkSerializationRedisSerializer，要求实体实现 Serializable，否则抛 "Cannot serialize"）。
        // 用 config.entryTtl(...) 可保留 JSON 序列化器，仅覆盖过期时间。
        RedisCacheManager redisCacheManager = RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .withCacheConfiguration("courses", config.entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration("knowledgePoints", config.entryTtl(Duration.ofHours(2)))
                .withCacheConfiguration("systemConfig", config.entryTtl(Duration.ofHours(24)))
                .withCacheConfiguration("verificationCode", config.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("analytics", config.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("examPapers", config.entryTtl(Duration.ofMinutes(30)))
                .withCacheConfiguration("rag", config.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration("permissions", config.entryTtl(Duration.ofMinutes(30)))
                // 以下缓存名此前在服务层已使用但未显式注册（走默认 30min），现统一显式声明 TTL
                .withCacheConfiguration("users", config.entryTtl(Duration.ofMinutes(30)))
                .withCacheConfiguration("questions", config.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration("proctor-config", config.entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration("rag-retrieve", config.entryTtl(Duration.ofMinutes(10)))
                .build();
        log.info("[CacheConfig] Redis 缓存管理器初始化完成，已为 courses/knowledgePoints/systemConfig 等缓存名注册差异化 TTL");
        return redisCacheManager;
    }
}
