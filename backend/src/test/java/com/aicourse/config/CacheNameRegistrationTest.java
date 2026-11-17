package com.aicourse.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缓存名注册门禁。
 *
 * <p>业务代码里每个 {@code @Cacheable} 的缓存名都必须在 {@link CacheConfig} 显式登记 TTL；
 * 否则会落到默认 30 分钟，读侧和写侧失效节奏对不上，形成「写入后短时间仍读到旧值」的隐性缺陷。
 * 本测试自动发现所有 {@code @Cacheable} 缓存名，并断言它们在 {@code CacheConfig} 中出现，
 * 避免新增缓存时只改业务注解、忘改 TTL。
 */
@DisplayName("缓存名 TTL 注册门禁")
class CacheNameRegistrationTest {

    @Test
    @DisplayName("所有 @Cacheable 缓存名都在 CacheConfig 显式登记")
    void everyCacheableNameIsRegistered() throws IOException {
        Set<String> usedNames = discoverCacheableNames();
        assertThat(usedNames)
                .as("未扫描到任何 @Cacheable 缓存名——扫描逻辑可能已失效，门禁形同虚设")
                .isNotEmpty();

        String configSource = Files.readString(
                locateCacheConfig(), StandardCharsets.UTF_8);

        for (String name : usedNames) {
            assertThat(configSource)
                    .as("缓存名 %s 未在 CacheConfig 显式登记，将落到默认 30 分钟 TTL", name)
                    .contains(".withCacheConfiguration(\"" + name + "\"");
        }
    }

    @Test
    @DisplayName("阳性对照：题库两个实际缓存名必须被扫描到")
    void questionCachesAreDiscovered() {
        Set<String> usedNames = discoverCacheableNames();
        assertThat(usedNames).contains("questionSearch", "questionDetail");
    }

    private Set<String> discoverCacheableNames() {
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        provider.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        Set<String> names = new LinkedHashSet<>();
        for (BeanDefinition bd : provider.findCandidateComponents("com.aicourse")) {
            String className = bd.getBeanClassName();
            if (className == null) {
                continue;
            }
            Class<?> clazz;
            try {
                clazz = Class.forName(className, false, getClass().getClassLoader());
            } catch (Throwable ignored) {
                continue;
            }
            if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }
            for (Method method : clazz.getDeclaredMethods()) {
                Cacheable cacheable = method.getAnnotation(Cacheable.class);
                if (cacheable == null) {
                    continue;
                }
                String[] cacheNames = cacheable.cacheNames();
                if (cacheNames.length == 0) {
                    cacheNames = cacheable.value();
                }
                for (String name : cacheNames) {
                    if (!name.isBlank()) {
                        names.add(name);
                    }
                }
            }
        }
        return names;
    }

    private Path locateCacheConfig() {
        Path direct = Path.of("src/main/java/com/aicourse/config/CacheConfig.java");
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromRepoRoot = Path.of("backend/src/main/java/com/aicourse/config/CacheConfig.java");
        assertThat(fromRepoRoot)
                .as("无法定位 CacheConfig.java（测试工作目录既不是 backend 也不是仓库根）")
                .exists();
        return fromRepoRoot;
    }
}
