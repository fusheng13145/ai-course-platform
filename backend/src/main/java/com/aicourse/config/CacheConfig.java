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

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

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
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .withCacheConfiguration("courses", config.entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration("knowledgePoints", config.entryTtl(Duration.ofHours(2)))
                .withCacheConfiguration("systemConfig", config.entryTtl(Duration.ofHours(24)))
                .withCacheConfiguration("verificationCode", config.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("analytics", config.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("examPapers", config.entryTtl(Duration.ofMinutes(30)))
                .withCacheConfiguration("rag", config.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration("permissions", config.entryTtl(Duration.ofMinutes(30)))
                .build();
    }
}
