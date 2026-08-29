package com.aicourse.config;

import com.aicourse.entity.Notification;
import com.aicourse.entity.User;
import com.aicourse.security.AiQuotaInterceptor;
import com.aicourse.security.JwtAuthenticationEntryPoint;
import com.aicourse.security.JwtRequestFilter;
import com.aicourse.service.UserService;
import com.aicourse.service.impl.MinioFileStorageServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.JacksonProperties;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配置类（config 包）集中覆盖测试：直接实例化每个 @Configuration / @Component 配置类并调用其
 * @Bean / 生命周期方法，注入 mocked 协作者，从而覆盖方法体。
 * 涵盖：AsyncMvcConfig、CacheConfig、CorsProperties、JacksonConfig、LLMProviderConfig、
 * MyBatisConfig、MyBatisPlusConfig、MyBatisSlowSqlInterceptor、MyMetaObjectHandler、RedisConfig、
 * RedisObjectMapperFactory、RedisStartupValidator、SchedulerLockConfig、SchemaMigration、
 * SecurityConfig（passwordEncoder / corsConfigurationSource / authenticationManager）、
 * UploadResourceConfig、MinioConfig、AdminSeedPasswordInitializer。
 * 注：SecurityConfig.filterChain / actuatorFilterChain 依赖 HttpSecurity，无法脱离 Spring 上下文
 * 单独实例化，故仅覆盖其无 HttpSecurity 依赖的方法（详见报告）。
 */
@ExtendWith(MockitoExtension.class)
class ConfigCoverageTest {

    // ===================== AsyncMvcConfig =====================
    @Mock
    private AiQuotaInterceptor aiQuotaInterceptor;

    @Test
    void asyncMvcConfig_allMethods() {
        AsyncMvcConfig cfg = new AsyncMvcConfig(aiQuotaInterceptor);
        org.springframework.web.servlet.config.annotation.InterceptorRegistry registry = mock(InterceptorRegistry.class);
        org.springframework.web.servlet.config.annotation.InterceptorRegistration reg = mock(org.springframework.web.servlet.config.annotation.InterceptorRegistration.class);
        lenient().when(registry.addInterceptor(any())).thenReturn(reg);
        cfg.addInterceptors(registry);

        assertNotNull(cfg.mvcAsyncExecutor());

        AsyncSupportConfigurer configurer = mock(AsyncSupportConfigurer.class);
        lenient().when(configurer.setTaskExecutor(any())).thenReturn(configurer);
        lenient().when(configurer.setDefaultTimeout(any(Long.class))).thenReturn(configurer);
        cfg.configureAsyncSupport(configurer);
        verify(configurer).setDefaultTimeout(120_000L);
    }

    // ===================== CacheConfig =====================
    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    void cacheConfig_cacheManager() {
        CacheManager cm = new CacheConfig().cacheManager(redisConnectionFactory);
        assertNotNull(cm);
    }

    // ===================== CorsProperties =====================
    @Test
    void corsProperties_initAndAccessors() {
        CorsProperties p = new CorsProperties();
        p.setAllowedOrigins(List.of("https://a.com"));
        assertEquals(List.of("https://a.com"), p.getAllowedOrigins());
        p.init(); // @PostConstruct 日志
    }

    // ===================== JacksonConfig =====================
    @Test
    void jacksonConfig_customizer() {
        JacksonConfig cfg = new JacksonConfig();
        JacksonProperties props = new JacksonProperties();
        props.setDateFormat("yyyy-MM-dd");
        props.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        ReflectionTestUtils.setField(cfg, "jacksonProperties", props);

        org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer customizer = cfg.jacksonCustomizer();
        org.springframework.http.converter.json.Jackson2ObjectMapperBuilder builder =
                mock(org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.class);
        lenient().when(builder.modules(any(List.class))).thenReturn(builder);
        lenient().when(builder.featuresToDisable(any())).thenReturn(builder);
        lenient().when(builder.dateFormat(any())).thenReturn(builder);
        lenient().when(builder.timeZone(any(java.util.TimeZone.class))).thenReturn(builder);
        customizer.customize(builder);
        verify(builder).dateFormat(any());
    }

    // ===================== LLMProviderConfig =====================
    @Mock
    private com.aicourse.llm.RoutingLLMProvider routingLLMProvider;

    @Test
    void llmProviderConfig_bean() {
        LLMProviderConfig cfg = new LLMProviderConfig();
        ReflectionTestUtils.setField(cfg, "provider", "deepseek");
        assertEquals(routingLLMProvider, cfg.llmProvider(routingLLMProvider));
    }

    // ===================== MyBatisConfig =====================
    @Test
    void myBatisConfig_constructs() {
        assertNotNull(new MyBatisConfig());
    }

    // ===================== MyBatisPlusConfig =====================
    @Test
    void myBatisPlusConfig_interceptor() {
        assertNotNull(new MyBatisPlusConfig().mybatisPlusInterceptor());
    }

    // ===================== MyBatisSlowSqlInterceptor =====================
    @Test
    void slowSql_thresholdZeroReturnsEarly() throws Throwable {
        MyBatisSlowSqlInterceptor interceptor = new MyBatisSlowSqlInterceptor();
        ReflectionTestUtils.setField(interceptor, "slowSqlThresholdMs", 0L);
        Invocation inv = mock(Invocation.class);
        when(inv.proceed()).thenReturn("result");
        assertEquals("result", interceptor.intercept(inv));
    }

    @Test
    void slowSql_fastQueryNoWarn() throws Throwable {
        MyBatisSlowSqlInterceptor interceptor = new MyBatisSlowSqlInterceptor();
        ReflectionTestUtils.setField(interceptor, "slowSqlThresholdMs", 1L);
        Invocation inv = mock(Invocation.class);
        when(inv.proceed()).thenReturn("result"); // 很快，< 1ms
        assertEquals("result", interceptor.intercept(inv));
    }

    @Test
    void slowSql_slowQueryWarns() throws Throwable {
        MyBatisSlowSqlInterceptor interceptor = new MyBatisSlowSqlInterceptor();
        ReflectionTestUtils.setField(interceptor, "slowSqlThresholdMs", 1L);
        Invocation inv = mock(Invocation.class);
        when(inv.proceed()).thenAnswer(i -> {
            Thread.sleep(10);
            return "result";
        });
        interceptor.intercept(inv); // 触发 warn（SQL 描述解析失败被 catch）
    }

    @Test
    void slowSql_pluginAndProperties() {
        MyBatisSlowSqlInterceptor interceptor = new MyBatisSlowSqlInterceptor();
        Object wrapped = interceptor.plugin(mock(Executor.class));
        assertNotNull(wrapped);
        interceptor.setProperties(new Properties());
    }

    // ===================== MyMetaObjectHandler =====================
    public static class Holder {
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime v) { createdAt = v; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
    }

    @Test
    void metaObjectHandler_insertAndUpdateFill() {
        MyMetaObjectHandler handler = new MyMetaObjectHandler();
        // 自动填充需基于已注册 TableInfo 的实体（Holder 不是实体，strictFill 会因 tableInfo 为 null 抛 NPE）
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), Notification.class);
        ObjectFactory of = new DefaultObjectFactory();
        ObjectWrapperFactory owf = new DefaultObjectWrapperFactory();
        org.apache.ibatis.reflection.DefaultReflectorFactory rf = new org.apache.ibatis.reflection.DefaultReflectorFactory();
        Notification notification = new Notification();
        MetaObject mo = MetaObject.forObject(notification, of, owf, rf);
        handler.insertFill(mo);
        handler.updateFill(mo);
        // Notification 仅有 createdAt / readAt 字段：createdAt 应被 insertFill 填充；
        // updatedAt 字段不存在，strictInsertFill / strictUpdateFill 按 MyBatis-Plus strict 语义静默跳过（不抛异常、不填充）。
        assertNotNull(notification.getCreatedAt());
        assertNull(notification.getReadAt());
    }

    // ===================== RedisConfig =====================
    @Test
    void redisConfig_templates() {
        RedisConfig cfg = new RedisConfig();
        assertNotNull(cfg.redisTemplate(redisConnectionFactory));
        assertNotNull(cfg.stringRedisTemplate(redisConnectionFactory));
    }

    // ===================== RedisObjectMapperFactory =====================
    @Test
    void redisObjectMapperFactory_create() {
        ObjectMapper mapper = RedisObjectMapperFactory.create();
        assertNotNull(mapper);
    }

    // ===================== RedisStartupValidator =====================
    @Test
    void redisStartupValidator_success() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection conn = mock(RedisConnection.class);
        when(factory.getConnection()).thenReturn(conn);
        when(conn.ping()).thenReturn("PONG");
        new RedisStartupValidator(factory, 3, 0L).run(null);
    }

    @Test
    void redisStartupValidator_retryThenSuccess() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection conn = mock(RedisConnection.class);
        when(factory.getConnection())
                .thenThrow(new RuntimeException("down"))
                .thenReturn(conn);
        when(conn.ping()).thenReturn("PONG");
        new RedisStartupValidator(factory, 3, 0L).run(null);
    }

    @Test
    void redisStartupValidator_allFail_throws() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(new RuntimeException("down"));
        assertThrows(IllegalStateException.class,
                () -> new RedisStartupValidator(factory, 2, 0L).run(null));
    }

    // ===================== SchedulerLockConfig =====================
    @Test
    void schedulerLockConfig_lockProvider() {
        assertNotNull(new SchedulerLockConfig().lockProvider(redisConnectionFactory));
    }

    // ===================== SchemaMigration =====================
    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    void schemaMigration_normal() {
        // 首个列 cnt==0（执行 ALTER），其余 cnt>0（跳过），覆盖两分支
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenReturn(0, 1, 1, 1, 1, 1);
        new SchemaMigration(jdbcTemplate).migrate();
        verify(jdbcTemplate).execute(anyString());
    }

    @Test
    void schemaMigration_queryErrorIsSwallowed() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenThrow(new RuntimeException("boom"));
        // 不应抛出
        new SchemaMigration(jdbcTemplate).migrate();
    }

    // ===================== SecurityConfig =====================
    @Mock
    private JwtRequestFilter jwtRequestFilter;
    @Mock
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Test
    void securityConfig_passwordEncoder() {
        SecurityConfig cfg = new SecurityConfig(jwtRequestFilter, jwtAuthenticationEntryPoint,
                new CorsProperties(), false, true, true);
        assertNotNull(cfg.passwordEncoder());
    }

    @Test
    void securityConfig_corsConfigurationSource() {
        CorsProperties cors = new CorsProperties();
        cors.setAllowedOrigins(List.of("https://x.com"));
        SecurityConfig cfg = new SecurityConfig(jwtRequestFilter, jwtAuthenticationEntryPoint,
                cors, false, true, true);
        assertNotNull(cfg.corsConfigurationSource());
    }

    @Test
    void securityConfig_authenticationManager() throws Exception {
        SecurityConfig cfg = new SecurityConfig(jwtRequestFilter, jwtAuthenticationEntryPoint,
                new CorsProperties(), false, true, true);
        AuthenticationConfiguration authConfig = mock(AuthenticationConfiguration.class);
        when(authConfig.getAuthenticationManager()).thenReturn(mock(AuthenticationManager.class));
        assertNotNull(cfg.authenticationManager(authConfig));
    }

    // ===================== UploadResourceConfig =====================
    @Test
    void uploadResourceConfig_registerHandlers() {
        UploadResourceConfig cfg = new UploadResourceConfig();
        // @Value 字段在脱离 Spring 上下文的纯实例化下为 null，需手动注入避免 Paths.get(null) NPE
        ReflectionTestUtils.setField(cfg, "uploadDir", "uploads/");
        ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
        org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration reg =
                mock(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration.class);
        lenient().when(registry.addResourceHandler(anyString())).thenReturn(reg);
        lenient().when(reg.addResourceLocations(anyString())).thenReturn(reg);
        lenient().when(reg.setCachePeriod(any(Integer.class))).thenReturn(reg);
        cfg.addResourceHandlers(registry);
        verify(registry).addResourceHandler("/uploads/**");
    }

    // ===================== MinioConfig =====================
    @Test
    void minioConfig_gettersSetters() {
        MinioConfig cfg = new MinioConfig();
        cfg.setEndpoint("http://localhost:9000");
        cfg.setAccessKey("ak");
        cfg.setSecretKey("sk");
        cfg.setBucket("b");
        assertEquals("http://localhost:9000", cfg.getEndpoint());
        assertEquals("ak", cfg.getAccessKey());
        assertEquals("sk", cfg.getSecretKey());
        assertEquals("b", cfg.getBucket());
    }

    @Test
    void minioConfig_minioClientFailureWrapsRuntimeException() {
        // 指向不可达端口，bucketExists 立即连接失败 -> catch 后抛 RuntimeException
        MinioConfig cfg = new MinioConfig();
        cfg.setEndpoint("http://127.0.0.1:1");
        cfg.setBucket("b");
        assertThrows(RuntimeException.class, cfg::minioClient);
    }

    // ===================== AdminSeedPasswordInitializer =====================
    @Mock
    private UserService userService;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void adminSeed_blankPassword_returnsEarly() {
        AdminSeedPasswordInitializer init = new AdminSeedPasswordInitializer(userService, passwordEncoder);
        ReflectionTestUtils.setField(init, "initialPassword", "");
        init.run(null); // 不应抛
    }

    @Test
    void adminSeed_invalidPassword_throws() {
        AdminSeedPasswordInitializer init = new AdminSeedPasswordInitializer(userService, passwordEncoder);
        ReflectionTestUtils.setField(init, "initialPassword", "weak"); // 不满足复杂度
        assertThrows(RuntimeException.class, () -> init.run(null));
    }

    @Test
    void adminSeed_adminNotFound() {
        AdminSeedPasswordInitializer init = new AdminSeedPasswordInitializer(userService, passwordEncoder);
        ReflectionTestUtils.setField(init, "initialPassword", "Valid!123");
        when(userService.getOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class), eq(false)))
                .thenReturn(null);
        init.run(null); // warn，返回
    }

    @Test
    void adminSeed_seedHashReplaced() {
        AdminSeedPasswordInitializer init = new AdminSeedPasswordInitializer(userService, passwordEncoder);
        ReflectionTestUtils.setField(init, "initialPassword", "Valid!123");
        User admin = new User();
        admin.setPassword("$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq");
        when(userService.getOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class), eq(false)))
                .thenReturn(admin);
        when(passwordEncoder.encode("Valid!123")).thenReturn("encoded-hash");
        init.run(null);
        assertEquals("encoded-hash", admin.getPassword());
        verify(userService).updateById(admin);
    }

    @Test
    void adminSeed_notSeedHash_keepsCurrent() {
        AdminSeedPasswordInitializer init = new AdminSeedPasswordInitializer(userService, passwordEncoder);
        ReflectionTestUtils.setField(init, "initialPassword", "Valid!123");
        User admin = new User();
        admin.setPassword("already-changed-hash");
        when(userService.getOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class), eq(false)))
                .thenReturn(admin);
        init.run(null); // 不应调用 updateById
        verify(userService, org.mockito.Mockito.never()).updateById(any());
    }
}
