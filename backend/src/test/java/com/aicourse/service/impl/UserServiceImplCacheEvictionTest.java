package com.aicourse.service.impl;

import com.aicourse.entity.User;
import com.aicourse.mapper.CourseEnrollmentMapper;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.UserMapper;
import com.aicourse.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UserServiceImpl 缓存失效的**真实语义**测试（不依赖 Redis/MySQL）。
 *
 * <p>背景：纯 Mockito 单测观测不到 {@code @Caching/@CacheEvict} 的 AOP 拦截，
 * 而项目的集成测试基建（contract-test profile 连共享本地库）在本机不可用。
 * 本测试用最小 Spring 上下文（{@code @EnableCaching} + {@code ConcurrentMapCacheManager}）
 * 装配真实的 {@link UserServiceImpl} 代理，验证：</p>
 * <ol>
 *   <li>SpEL 键 {@code 'profile_' + #entity.id} / {@code #entity.id} 在运行期**能取到参数名**
 *       并正确求值（参数名发现失败会让 updateById 在登录路径上直接抛错，这是本测试要挡住的
 *       最大风险——登录流程会写 lastLogin，走的就是 updateById）；</li>
 *   <li>两个缓存键的**键口径**与 getUserProfile/getUserPermissions 一致，调用后确实被逐出。</li>
 * </ol>
 */
class UserServiceImplCacheEvictionTest {

    @Configuration
    @EnableCaching
    static class CacheSliceConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("users", "permissions");
        }

        @Bean
        UserMapper userMapper() {
            return mock(UserMapper.class);
        }

        @Bean
        CourseMapper courseMapper() {
            return mock(CourseMapper.class);
        }

        @Bean
        CourseEnrollmentMapper courseEnrollmentMapper() {
            return mock(CourseEnrollmentMapper.class);
        }

        @Bean
        UserServiceImpl userService(UserMapper userMapper,
                                    CourseMapper courseMapper,
                                    CourseEnrollmentMapper courseEnrollmentMapper) {
            return new UserServiceImpl(courseMapper, courseEnrollmentMapper);
        }
    }

    private AnnotationConfigApplicationContext context;
    private UserService userService;
    private CacheManager cacheManager;
    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(CacheSliceConfig.class);
        // 缓存拦截器为 UserServiceImpl 生成 JDK 动态代理（实现了 UserService 接口），
        // 代理只暴露接口类型 —— 必须按接口取 bean；baseMapper 等字段注入发生在目标实例上，
        // 由代理委托，无需（也无法对代理）再手工注入。
        userService = context.getBean(UserService.class);
        cacheManager = context.getBean(CacheManager.class);
        userMapper = context.getBean(UserMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("updateById 真实拦截：users(profile_+id) 与 permissions(id) 两类缓存均被逐出")
    void updateById_evictsBothCaches() {
        // 预置两类缓存（键口径分别对应 getUserProfile 与 getUserPermissions）
        Cache users = cacheManager.getCache("users");
        Cache permissions = cacheManager.getCache("permissions");
        assertNotNull(users);
        assertNotNull(permissions);

        User cached = new User();
        cached.setId(4L);
        cached.setRole("ADMIN");
        users.put("profile_4", cached);
        Map<String, Object> permPayload = new HashMap<>();
        permPayload.put("role", "ADMIN");
        permissions.put(4L, permPayload);

        User update = new User();
        update.setId(4L);
        update.setRole("STUDENT");
        when(userMapper.updateById(update)).thenReturn(1);

        userService.updateById(update);

        assertNull(users.get("profile_4"), "users 缓存必须按 'profile_' + id 逐出（否则管理端 30 分钟内读旧角色）");
        assertNull(permissions.get(4L), "permissions 缓存必须按 userId 逐出（否则 /auth/permissions 返回旧角色菜单）");
    }

    @Test
    @DisplayName("updateById 真实拦截：其他用户的缓存不受影响（键按 userId 收敛，不过度逐出）")
    void updateById_doesNotEvictOtherUsers() {
        Cache users = cacheManager.getCache("users");
        User other = new User();
        other.setId(9L);
        users.put("profile_9", other);

        User update = new User();
        update.setId(4L);
        when(userMapper.updateById(update)).thenReturn(1);

        userService.updateById(update);

        assertNotNull(users.get("profile_9"), "仅应逐出目标用户的缓存");
    }
}
