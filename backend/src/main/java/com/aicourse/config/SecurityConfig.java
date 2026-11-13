package com.aicourse.config;

import com.aicourse.config.CorsProperties;
import com.aicourse.security.JwtAuthenticationEntryPoint;
import com.aicourse.security.JwtRequestFilter;
import com.aicourse.security.RestAccessDeniedHandler;
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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import com.aicourse.common.RoleConstants;
import com.aicourse.util.TrustedClientIpResolver;

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
    private final RestAccessDeniedHandler restAccessDeniedHandler;
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
     * Actuator Basic 鉴权监控账号（供 Prometheus 抓取 /actuator/prometheus）。默认 monitor。
     * 口令留空则 fail-closed：拒绝一切抓取请求，启用 monitor profile 时必须在 .env 中设置。
     */
    @Value("${app.actuator.basic.user:monitor}")
    private String actuatorBasicUser;

    /** Actuator Basic 鉴权监控口令。默认空（fail-closed）。 */
    @Value("${app.actuator.basic.password:}")
    private String actuatorBasicPassword;

    /**
     * CORS 本地开发兜底开关。默认 true：当 {@code cors.allowed-origins} 未配置时回退到
     * {@code http://localhost:5173}（开发联调便利）。生产环境务必在 profile 中置为 false，
     * 使未显式配置 origins 时回落为空（拒绝一切跨域），避免带凭证的 localhost 兜底泄漏到生产。
     */
    @Value("${app.cors.dev-fallback:true}")
    private boolean corsDevFallback;

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * 注入JWT过滤器、认证异常处理器
     * @param jwtRequestFilter JWT请求拦截过滤器
     * @param jwtAuthenticationEntryPoint 未认证/令牌异常统一处理器
     */
    public SecurityConfig(JwtRequestFilter jwtRequestFilter,
                          JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                          RestAccessDeniedHandler restAccessDeniedHandler,
                          CorsProperties corsProperties,
                          @org.springframework.beans.factory.annotation.Value("${app.swagger.enabled:false}") boolean swaggerEnabled,
                          @org.springframework.beans.factory.annotation.Value("${app.actuator.secured:true}") boolean actuatorSecured,
                          @org.springframework.beans.factory.annotation.Value("${app.security.require-https:true}") boolean requireHttps,
                          @org.springframework.beans.factory.annotation.Value("${app.cors.dev-fallback:true}") boolean corsDevFallback) {
        this.jwtRequestFilter = jwtRequestFilter;
        this.jwtAuthEntryPoint = jwtAuthenticationEntryPoint;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
        this.corsProperties = corsProperties;
        this.swaggerEnabled = swaggerEnabled;
        this.actuatorSecured = actuatorSecured;
        this.requireHttps = requireHttps;
        this.corsDevFallback = corsDevFallback;
    }

    /**
     * 安全决策用的客户端 IP 单一真源（限流键与登录失败节流共用）。
     * <p>刻意用 {@code @Bean} 而非类级 {@code @Component}：{@code RateLimitFilter}（{@code Filter}）
     * 会被 {@code @WebMvcTest} 切片扫描，而 util 包的 {@code @Component} 不会——组件注册时切片上下文
     * 必然缺 bean 而无法启动。{@code @Configuration} 在切片中照常加载，故此处注册可同时覆盖整机与切片。</p>
     */
    @Bean
    public TrustedClientIpResolver trustedClientIpResolver() {
        return new TrustedClientIpResolver();
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
     * Actuator 专属安全过滤链（优先级高于主链）。
     * <p>actuator 端点位于 {@code /api/actuator/**}（context-path=/api）。本链通过 {@link OrRequestMatcher}
     * 同时匹配带/不带 {@code /api} 前缀，保证无论 Spring Security 如何归一化 context-path 都能正确拦截。</p>
     * <p>采用独立的 HTTP Basic 鉴权，与业务 JWT 链路完全隔离：</p>
     * <ul>
     *   <li>health 始终公开（供探针探活）；info / metrics 仅在测试（actuatorSecured=false）公开，" +
     *       "生产（actuatorSecured=true）一并收敛以减少暴露面；</li>
     *   <li>prometheus 抓取端点：生产（actuatorSecured=true）要求 Basic Auth 且具备 ACTUATOR 角色，
     *       凭据来自 {@code APP_ACTUATOR_BASIC_USER/PASSWORD} 环境变量；</li>
     *   <li>其余 actuator 端点一律拒绝（最小化暴露面）。</li>
     * </ul>
     * <p>该设计使 Prometheus 的 {@code basic_auth} 真正可用——原 JWT 无状态架构下 Prometheus 无法换取
     * ADMIN 角色，导致 {@code /actuator/prometheus} 在生产（actuatorSecured=true）时实际不可抓取。</p>
     */
    @Bean
    @Order(0)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http,
                                                  PasswordEncoder passwordEncoder) throws Exception {
        AuthenticationManager actuatorAuthManager = buildActuatorAuthManager(passwordEncoder);

        http
                .securityMatcher(new OrRequestMatcher(
                        PathPatternRequestMatcher.withDefaults().matcher("/actuator/**"),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/actuator/**")))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationManager(actuatorAuthManager)
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> {
                    // health 始终公开（K8s/Docker 探针探活所需）
                    auth.requestMatchers(
                                    "/actuator/health", "/actuator/health/**",
                                    "/api/actuator/health", "/api/actuator/health/**")
                            .permitAll();
                    if (actuatorSecured) {
                        // 生产：info/metrics 一并收敛（仅探活所需 health 公开），减少信息暴露面
                        auth.requestMatchers(
                                        "/actuator/info", "/api/actuator/info",
                                        "/actuator/metrics", "/actuator/metrics/**",
                                        "/api/actuator/metrics", "/api/actuator/metrics/**")
                                .denyAll();
                        auth.requestMatchers("/actuator/prometheus", "/api/actuator/prometheus")
                                .hasRole("ACTUATOR");
                        auth.requestMatchers("/actuator/**", "/api/actuator/**").denyAll();
                    } else {
                        // 测试（actuatorSecured=false）：放开 info/metrics 便于契约断言
                        auth.requestMatchers("/actuator/info", "/api/actuator/info").permitAll();
                        auth.requestMatchers(
                                        "/actuator/metrics", "/actuator/metrics/**",
                                        "/api/actuator/metrics", "/api/actuator/metrics/**")
                                .permitAll();
                        auth.requestMatchers("/actuator/**", "/api/actuator/**").permitAll();
                    }
                });
        return http.build();
    }

    /**
     * 构建仅供 Actuator Basic 鉴权使用的 AuthenticationManager（与业务 JWT 认证管理器隔离）。
     * 凭据取自环境变量；若未配置口令则注册空用户表（fail-closed：任何 Basic 凭据均 401）。
     */
    private AuthenticationManager buildActuatorAuthManager(PasswordEncoder passwordEncoder) {
        InMemoryUserDetailsManager userDetailsManager = new InMemoryUserDetailsManager();
        if (actuatorBasicUser != null && !actuatorBasicUser.isBlank()
                && actuatorBasicPassword != null && !actuatorBasicPassword.isBlank()) {
            // 防御：{@code PasswordEncoder} 在 {@code @WebMvcTest} 切片中通常是 Mockito mock，
            // 其 {@code encode(...)} 返回 null，直接交给 {@code UserBuilder.password(...)}
            // 会抛 IllegalArgumentException("password cannot be null")，
            // 使整个应用上下文启动失败（实测：注入 APP_ACTUATOR_BASIC_PASSWORD 后切片用例全错）。
            // 空编码结果一律视为「凭据不可用」，退回 fail-closed（不注册监控账号）。
            String encoded;
            try {
                encoded = passwordEncoder.encode(actuatorBasicPassword);
            } catch (IllegalArgumentException e) {
                // BCrypt 对 null 或长度超过 72 字符的口令抛 IllegalArgumentException；
                // 视为凭据不可用，退回 fail-closed（不注册监控账号），避免上下文启动失败。
                log.error("Actuator Basic 口令编码失败（超过 BCrypt 72 字符上限或为空）：{}，" +
                                " 本次不注册监控账号，/actuator/prometheus 保持拒绝所有抓取（fail-closed）。",
                        e.getMessage());
                encoded = null;
            }
            if (encoded == null || encoded.isBlank()) {
                log.warn("PasswordEncoder 未能编码 Actuator Basic 口令（encode 返回空），"
                        + " 本次不注册监控账号，/actuator/prometheus 保持拒绝所有抓取（fail-closed）。");
            } else {
                userDetailsManager.createUser(User.withUsername(actuatorBasicUser)
                        .password(encoded)
                        .roles("ACTUATOR")
                        .build());
            }
        } else {
            log.warn("未配置 Actuator Basic 凭据（APP_ACTUATOR_BASIC_USER/PASSWORD），"
                    + " /actuator/prometheus 将拒绝所有抓取请求（fail-closed）。"
                    + " 启用 monitor profile 时请务必在 .env 中设置该监控账号与强口令。");
        }
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsManager);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * 安全过滤主链路配置
     * 1. 禁用CSRF、配置跨域  2. 无状态会话  3. 全局异常处理
     * 4. 接口路由权限控制    5. 前置JWT校验过滤器
     */
    @Bean
    @Order(1)
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
                // CSRF 防护由自定义 DoubleSubmitCsrfFilter（Servlet Filter 层，@Component 注册，
                // 读取 app.security.csrf.enabled 默认 true）承担——双提交 Cookie + 引导端点豁免，
                // 见 security/DoubleSubmitCsrfFilter 及其专项测试。
                // Spring Security 层必须保持关闭，否则双份 CSRF 校验相互冲突。
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
                // 配置认证失败统一异常处理：
                // - authenticationEntryPoint：未认证 / 令牌失效 → 401（若上下文已是已认证主体则返回 403）
                // - accessDeniedHandler：已认证但权限不足 → 403 + 标准业务信封（原缺此配置，返回无响应体 403）
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                // 接口权限规则配置
                .authorizeHttpRequests(auth -> {
                    // 公开接口：无需登录即可访问
                    auth.requestMatchers(publicPaths().toArray(new String[0])).permitAll();

                    // 管理员专属接口
                    auth.requestMatchers("/admin/**").hasRole(RoleConstants.ADMIN);

                    // 可观测性端点（health/info/metrics/prometheus）已交由独立的 actuatorFilterChain 处理，
                    // 详见本类 actuatorFilterChain(@Order(0))，与业务 JWT 链路隔离、支持 Prometheus Basic Auth。

                    // 学生可用的 AI 交互端点（智能问答 / 纠错 / 润色）：任意已登录角色均可调用，
                    // 不受下方「资源写操作限教师/管理员」规则约束，否则学生无法使用 AI 助教与笔记润色。
                    auth.requestMatchers(HttpMethod.POST,
                                    "/ai/qa/ask", "/ai/qa/continue",
                                    "/ai/essay/correct", "/ai/essay/polish")
                            .authenticated();

                    // 学习路径规划：只读图查询（本地 BFS 或 KG 引擎多跳，不写库），
                    // 学生端「学习路径」依赖该端点；不适用下方「资源写操作限教师/管理员」规则。
                    // 其余 /knowledge/** 的 POST（知识点/关系写入、graph 摄取）仍受写保护。
                    auth.requestMatchers(HttpMethod.POST, "/knowledge/path/plan")
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
                // 修复 F-01：取消 /uploads/** 免认证。静态资源此前 permitAll，任意匿名用户
                // 凭 UUID 文件名即可直链下载（含教学资源），绕过 ResourceController.canAccessResource
                // 的对象级 ACL。现 /uploads/** 需登录后方可访问；教学资源下载应统一走
                // 受认证且做 ACL 的 /resources/download/{id}（匿名 401，已登录用户仍需通过对象级鉴权）。
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
            if (corsDevFallback) {
                // 仅本地开发便利：未配置 origins 时回退 localhost。生产须置 app.cors.dev-fallback=false
                // 并在 cors.allowed-origins 显式列出受信前端域名（不含 localhost），避免带凭证跨域泄漏。
                allowedOrigins = List.of("http://localhost:5173");
            } else {
                log.warn("cors.allowed-origins 未配置且 app.cors.dev-fallback=false，"
                        + " 将拒绝一切跨域请求（fail-closed）。生产环境请在配置中显式设置受信前端域名。");
                allowedOrigins = List.of();
            }
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