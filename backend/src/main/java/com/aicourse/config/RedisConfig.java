package com.aicourse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.aicourse.config.RedisObjectMapperFactory;

/**
 * Redis 序列化配置
 * 统一 Redis Key/Value 序列化规则，避免乱码、对象序列化异常
 */
@Configuration
public class RedisConfig {

    /**
     * 自定义 RedisTemplate（JSON 值序列化，用于缓存对象）。
     * @param factory Redis 连接工厂
     * @return RedisTemplate
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(factory);

        // Key、HashKey 使用字符串序列化
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setHashKeySerializer(stringSerializer);

        // Value、HashValue 使用 JSON 序列化，支持对象自动序列化
        // 使用带 JavaTimeModule 的 ObjectMapper，避免缓存实体（含 LocalDateTime 字段）序列化失败
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(RedisObjectMapperFactory.create());
        redisTemplate.setValueSerializer(jsonSerializer);
        redisTemplate.setHashValueSerializer(jsonSerializer);

        // 初始化配置
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    /**
     * 字符串专用 RedisTemplate（String 值序列化）。
     * 适用于限流、计数、会话黑名单等仅需存储字符串值的场景，避免 JSON 序列化开销。
     *
     * @param factory Redis 连接工厂
     * @return StringRedisTemplate
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        template.afterPropertiesSet();
        return template;
    }
}