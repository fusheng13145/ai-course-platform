package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.service.CourseService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CourseServiceImpl 缓存失效的真实语义测试（不依赖 Redis/MySQL）。
 *
 * <p>纯 Mockito 单测观测不到 {@code @Caching/@CacheEvict} 的 AOP 拦截，本测试用最小 Spring
 * 上下文装配真实代理，验证 {@code updateCourse} / {@code updateCourseById} 调用后
 * {@code courses(id)} 与 {@code courses(all)} 都被逐出，外部 GET 能读到新值。</p>
 */
class CourseServiceImplCacheEvictionTest {

    @Configuration
    @EnableCaching
    static class CacheSliceConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("courses");
        }

        @Bean
        CourseMapper courseMapper() {
            return mock(CourseMapper.class);
        }

        @Bean
        com.aicourse.mapper.TeachingClassMapper teachingClassMapper() {
            return mock(com.aicourse.mapper.TeachingClassMapper.class);
        }

        @Bean
        CourseServiceImpl courseService(CourseMapper courseMapper,
                                        com.aicourse.mapper.TeachingClassMapper teachingClassMapper) {
            CourseServiceImpl service = new CourseServiceImpl(
                    new com.aicourse.service.component.org.CourseDefaultTeachingClassSync(teachingClassMapper),
                    org.mockito.Mockito.mock(com.aicourse.service.SemesterService.class));
            ReflectionTestUtils.setField(service, "baseMapper", courseMapper);
            return service;
        }
    }

    private AnnotationConfigApplicationContext context;
    private CourseService courseService;
    private CacheManager cacheManager;
    private CourseMapper courseMapper;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(CacheSliceConfig.class);
        courseService = context.getBean(CourseService.class);
        cacheManager = context.getBean(CacheManager.class);
        courseMapper = context.getBean(CourseMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("updateCourse 真实拦截：courses(id) 与 courses(all) 均被逐出，随后 GET 读到新值")
    void updateCourse_evictsBothCaches() {
        Cache courses = cacheManager.getCache("courses");
        Course cached = new Course();
        cached.setId(1L);
        cached.setName("旧名");
        courses.put(1L, cached);
        courses.put("all", List.of(cached));

        Course exist = new Course();
        exist.setId(1L);
        exist.setTeacherId(2L);
        Course updated = new Course();
        updated.setId(1L);
        updated.setName("新名");
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(exist, updated, updated);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);

        Course request = new Course();
        request.setName("新名");
        Course result = courseService.updateCourse(1L, request, 2L, false);

        assertEquals("新名", result.getName());
        assertNull(courses.get(1L), "courses(id) 必须逐出，否则紧随其后的 GET 仍读旧值");
        assertNull(courses.get("all"), "courses(all) 必须逐出，否则列表仍读旧值");
        assertEquals("新名", courseService.getById(1L).getName(),
                "缓存逐出后外部 GET 必须回到数据库读新值");
    }

    @Test
    @DisplayName("updateCourseById 真实拦截：courses(id) 与 courses(all) 均被逐出")
    void updateCourseById_evictsBothCaches() {
        Cache courses = cacheManager.getCache("courses");
        Course cached = new Course();
        cached.setId(2L);
        cached.setName("旧名");
        courses.put(2L, cached);
        courses.put("all", List.of(cached));

        Course updated = new Course();
        updated.setId(2L);
        updated.setName("新名");
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(updated);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);

        Course request = new Course();
        request.setName("新名");
        courseService.updateCourseById(2L, request);

        assertNull(courses.get(2L));
        assertNull(courses.get("all"));
        assertEquals("新名", courseService.getById(2L).getName());
    }
}
