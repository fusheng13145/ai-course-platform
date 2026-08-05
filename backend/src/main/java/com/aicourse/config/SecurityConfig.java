package com.aicourse.config;

import com.aicourse.config.CorsProperties;
import com.aicourse.security.JwtAuthenticationEntryPoint;
import com.aicourse.security.JwtRequestFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;
import com.aicourse.common.RoleConstants;

/**
 * Spring Security 安全全局配置
 * 整合 JWT 认证、跨域处理、会话策略、接口权限管控、认证异常捕获
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtRequestFilter jwtRequestFilter;
    private final JwtAuthenticationEntryPoint jwtAuthEntryPoint;
    private final CorsProperties corsProperties;

    /** Swagger/OpenAPI 文档是否公开，默认关闭，避免生产环境暴露完整 API 文档。 */
    private final boolean swaggerEnabled;

    /**
     * Actuator 端点是否启用安全管控，默认开启（生产）。
     * 测试环境（application-test.yml）置为 false，关闭 prometheus 等端点鉴权，便于断言。
     */
    private final boolean actuatorSecured;

    /**
     * 是否在 Security 层强制 HTTPS 通道（http 请求跳转 https）。默认 true（安全默认值）。
     * 本地 HTTP 联调由 local profile 置 false。
     */
    private final boolean requireHttps;

    /**
     * 注入JWT过滤器、认证异常处理器
     * @param jwtRequestFilter JWT请求拦截过滤器
     * @param jwtAuthenticationEntryPoint 未认证/令牌异常统一处理器
     */
    public SecurityConfig(JwtRequestFilter jwtRequestFilter,
                          JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                          CorsProperties corsProperties,
                          @org.springframework.beans.factory.annotation.Value("${app.swagger.enabled:false}") boolean swaggerEnabled,
                          @org.springframework.beans.factory.annotation.Value("${app.actuator.secured:true}") boolean actuatorSecured,
                          @org.springframework.beans.factory.annotation.Value("${app.security.require-https:true}") boolean requireHttps) {
        this.jwtRequestFilter = jwtRequestFilter;
        this.jwtAuthEntryPoint = jwtAuthenticationEntryPoint;
        this.corsProperties = corsProperties;
        this.swaggerEnabled = swaggerEnabled;
        this.actuatorSecured = actuatorSecured;
        this.requireHttps = requireHttps;
    }

    /**
     * 密码加密器
     * 采用 BCrypt 算法进行密码哈希加密
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器
     * 提供账号密码登录认证能力
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 安全过滤主链路配置
     * 1. 禁用CSRF、配置跨域  2. 无状态会话  3. 全局异常处理
     * 4. 接口路由权限控制    5. 前置JWT校验过滤器
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 定义通用资源路径
        List<String> commonResourcePaths = List.of(
                "/courses/**",
                "/exam-papers/**",
                "/questions/**",
                "/tasks/**",
                "/chapters/**",
                "/resources/**",
                "/classrooms/**",
                "/teacher/**",
                "/ai/**",
                "/knowledge/**"
        );

        // 强制 HTTPS 通道（安全默认值，可由 app.security.require-https 关闭以便本地 HTTP 联调）：
        // 明文 http 请求将被 302 重定向至 https，与 HSTS 头形成传输层纵深防御，
        // 把「令牌一定走加密通道」从运维口头约定变成 Security 层强制保证。
        if (requireHttps) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }

        http
                // 关闭CSRF防护：JWT基于Token认证，无服务端会话，无需开启
                .csrf(AbstractHttpConfigurer::disable)
                // 安全响应头加固：HSTS、防 MIME 嗅探、禁止被 iframe 嵌入、Referrer 不泄露、CSP
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data: blob:; " +
                                "font-src 'self' data:; style-src 'self' 'unsafe-inline'; " +
                                "script-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'")))
                // 加载跨域配置
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 设置会话策略：无状态，不创建和使用Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 配置认证失败统一异常处理
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
                // 接口权限规则配置
                .authorizeHttpRequests(auth -> {
                    // 公开接口：无需登录即可访问
                    auth.requestMatchers(publicPaths().toArray(new String[0])).permitAll();

                    // 管理员专属接口
                    auth.requestMatchers("/admin/**").hasRole(RoleConstants.ADMIN);

                    // ============ 可观测性端点（Actuator，context-path /api 下为 /actuator/**）============
                    // health / info / metrics 公开（供探活与基础监控）
                    auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    auth.requestMatchers("/actuator/info").permitAll();
                    auth.requestMatchers("/actuator/metrics", "/actuator/metrics/**").permitAll();
                    if (actuatorSecured) {
                        // 生产：prometheus 抓取端点仅限 ADMIN；其余 actuator 端点一律拒绝
                        auth.requestMatchers("/actuator/prometheus").hasRole(RoleConstants.ADMIN);
                        auth.requestMatchers("/actuator/**").denyAll();
                    } else {
                        // 测试/本地：放开 actuator 鉴权，便于集成测试断言
                        auth.requestMatchers("/actuator/prometheus").permitAll();
                        auth.requestMatchers("/actuator/**").permitAll();
                    }

                    // 学生可用的 AI 交互端点（智能问答 / 纠错 / 润色）：任意已登录角色均可调用，
                    // 不受下方「资源写操作限教师/管理员」规则约束，否则学生无法使用 AI 助教与笔记润色。
                    auth.requestMatchers(HttpMethod.POST,
                                    "/ai/qa/ask", "/ai/qa/continue",
                                    "/ai/essay/correct", "/ai/essay/polish")
                            .authenticated();

                    // 课程/试卷/题目等资源：POST、PUT、DELETE、PATCH 仅教师、管理员可操作
                    auth.requestMatchers(HttpMethod.POST, commonResourcePaths.toArray(new String[0]))
                            .hasAnyRole(RoleConstants.TEACHER, RoleConstants.ADMIN);
                    auth.requestMatchers(HttpMethod.PUT, commonResourcePaths.toArray(new String[0]))
                            .hasAnyRole(RoleConstants.TEACHER, RoleConstants.ADMIN);
                    auth.requestMatchers(HttpMethod.DELETE, commonResourcePaths.toArray(new String[0]))
                            .hasAnyRole(RoleConstants.TEACHER, RoleConstants.ADMIN);
                    auth.requestMatchers(HttpMethod.PATCH, "/courses/**")
                            .hasAnyRole(RoleConstants.TEACHER, RoleConstants.ADMIN);

                    // 选课审批、作业评分接口：仅教师、管理员可操作
                    auth.requestMatchers(
                                    HttpMethod.POST,
                                    "/enrollments/*/approve",
                                    "/enrollments/*/reject",
                                    "/submissions/*/grade"
                            ).hasAnyRole(RoleConstants.TEACHER, RoleConstants.ADMIN);

                    // 其余所有接口，必须完成登录认证
                    auth.anyRequest().authenticated();
                })
                // 在账号密码认证过滤器之前，执行JWT令牌校验
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 计算公开（免认证）路径集合。
     * Swagger / OpenAPI 文档端点仅在 app.swagger.enabled=true 时公开，
     * 默认（生产）不公开，避免完整 API 文档被未授权访问。
     */
    private List<String> publicPaths() {
        List<String> paths = new ArrayList<>(List.of(
                "/auth/**",
                "/common/verify-code",
                "/common/feature-roadmap",
                "/uploads/**",
                "/ws",
                "/ws/**"));
        if (swaggerEnabled) {
            paths.add("/swagger-ui/**");
            paths.add("/api-docs.html");
            paths.add("/api-docs/**");
            paths.add("/v3/api-docs/**");
        }
        return paths;
    }

    /**
     * 跨域全局配置
     * 适配前端Vite开发服务，允许跨域请求、自定义请求头与凭证携带
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        // 允许的前端域名白名单改为配置驱动（cors.allowed-origins），多环境免改代码
        List<String> allowedOrigins = corsProperties.getAllowedOrigins();
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("http://localhost:5173");
        }
        corsConfig.setAllowedOrigins(allowedOrigins);
        // 允许的请求方式
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        // 放行必要请求头（收窄自 "*"，降低被滥用风险）
        corsConfig.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept",
                "Origin", "X-Requested-With", "Cache-Control"));
        // 允许前端携带Cookie、Token等认证凭证
        corsConfig.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return source;
    }
}