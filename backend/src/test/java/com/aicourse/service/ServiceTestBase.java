package com.aicourse.service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Service 单元测试基类。
 *
 * <p>使用 H2 内存数据库提供最小化的 Spring 上下文，确保 MyBatis-Plus ServiceImpl
 * 能正常初始化（无需 Docker/Testcontainers）。所有 Mapper 由子类通过
 * {@code @MockitoBean} 替换为 Mock，不对 H2 执行任何实际查询。
 *
 * <p>子类只需声明 {@code @MockitoBean} 替换 Mapper 及协作者，并用
 * {@code @Autowired} 注入被测 Service 即可。
 *
 * <p>与 {@link com.aicourse.AbstractIntegrationTest} 的区别：
 * <ul>
 *     <li>AbstractIntegrationTest：使用 Testcontainers 启动真实 MySQL/Redis，用于集成测试</li>
 *     <li>ServiceTestBase：使用 H2 内存库，无需 Docker，仅用于 Service 单元测试</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("service-test")
@TestPropertySource(properties = {
        "server.servlet.context-path=",
        "spring.web.resources.add-mappings=false"
})
public abstract class ServiceTestBase {

    /**
     * Mock Redis 连接工厂，使 RedisConfig、CacheConfig、SchedulerLockConfig 等
     * 依赖 Redis 的 {@code @Configuration} 类可以正常创建 Bean（实际不操作 Redis）。
     */
    @MockitoBean
    protected RedisConnectionFactory redisConnectionFactory;

    /**
     * 提供简单内存 CacheManager 替代 RedisCacheManager（超驰 CacheConfig 中定义的 bean）。
     * 避免测试中依赖 Redis 序列化 / 反序列化的复杂逻辑。
     */
    @TestConfiguration
    static class SimpleCacheConfig {
        @Bean
        @Primary
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }
}
