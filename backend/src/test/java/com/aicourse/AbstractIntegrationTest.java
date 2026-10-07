package com.aicourse;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试基类：启动真实的 MySQL 与 Redis 容器，保证测试环境与生产同构。
 *
 * <p>设计要点：
 * <ul>
 *   <li>容器声明为 {@code static} 单例，整个测试 JVM 复用同一组容器，避免逐类反复拉起（显著缩短耗时）。
 *       手动调用 {@code start()}；不使用 {@code @Container} 自动生命周期，以获得跨测试类的单例语义。</li>
 *   <li>镜像版本与 {@code docker-compose.yml} 的第三方镜像 pin 逐字一致（{@code mysql:8.4.11} /
 *       {@code redis:7.4.11-alpine}），使「测试环境＝编排环境＝生产」成为可核对的等式，
 *       也让本机与 CI 直接复用同一份已存在的镜像，不再额外拉取 {@code mysql:8.0} 这类旁支版本。</li>
 *   <li>MySQL 数据库名固定为 {@code ai_course_platform}，与 {@code index.sql} 中的 {@code USE} 语句一致。</li>
 *   <li>通过 {@link DynamicPropertySource} 将容器随机映射端口注入 Spring 数据源与 Redis 配置，
 *       无需在配置文件中硬编码端口。</li>
 *   <li>Schema 与种子数据由 Spring SQL 初始化机制执行 {@code classpath:index.sql}（见 application-test.yml）。</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4.11"))
                    .withDatabaseName("ai_course_platform")
                    .withUsername("test")
                    .withPassword("test")
                    // 服务端字符集与生产保持一致，确保中文/emoji 与排序规则一致
                    .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4.11-alpine"))
                    .withExposedPorts(6379);

    static {
        // 静态启动一次，JVM 生命周期内复用；Ryuk 会在 JVM 退出时回收容器。
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // 数据源：指向 MySQL 容器（URL 追加与生产一致的连接参数）
        registry.add("spring.datasource.url", () ->
                MYSQL.getJdbcUrl()
                        + "?useSSL=false&allowPublicKeyRetrieval=true"
                        + "&serverTimezone=Asia/Shanghai&characterEncoding=utf8&useUnicode=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        // Redis：指向 Redis 容器映射端口
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
