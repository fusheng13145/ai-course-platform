package com.aicourse;

import org.junit.jupiter.api.Test;

/**
 * 全上下文集成冒烟测试。
 *
 * <p>继承 {@link AbstractIntegrationTest}，基于 Testcontainers 提供的真实 MySQL + Redis 启动
 * 完整 Spring 应用上下文，验证所有 Bean（含安全、缓存、MyBatis-Plus、Web 层）能够协同装配，
 * 且数据库 schema 与种子数据（index.sql）可成功初始化。
 *
 * <p>命名约定：以 {@code IT} 结尾，由 maven-failsafe-plugin 在 {@code mvn verify} 阶段执行，
 * 由 maven-surefire-plugin 在 {@code mvn test} 阶段排除（本地无需 Docker 即可跑单元测试）。
 */
class ApplicationIT extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // 上下文成功加载即视为通过；若任一 Bean 装配失败或数据源不可用，此处会直接抛出。
    }
}
