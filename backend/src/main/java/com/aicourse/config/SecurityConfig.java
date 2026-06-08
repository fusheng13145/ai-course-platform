package com.aicourse.config;

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
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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

    /**
     * 注入JWT过滤器、认证异常处理器
     * @param jwtRequestFilter JWT请求拦截过滤器
     * @param jwtAuthenticationEntryPoint 未认证/令牌异常统一处理器
     */
    public SecurityConfig(JwtRequestFilter jwtRequestFilter,
                          JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.jwtRequestFilter = jwtRequestFilter;
        this.jwtAuthEntryPoint = jwtAuthenticationEntryPoint;
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
                "/knowledge-points/**",
                "/chapters/**",
                "/resources/**",
                "/classrooms/**"
        );

        http
                // 关闭CSRF防护：JWT基于Token认证，无服务端会话，无需开启
                .csrf(AbstractHttpConfigurer::disable)
                // 加载跨域配置
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 设置会话策略：无状态，不创建和使用Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 配置认证失败统一异常处理
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
                // 接口权限规则配置
                .authorizeHttpRequests(auth -> auth
                        // 公开接口：无需登录即可访问
                        .requestMatchers(
                                "/auth/**",
                                "/common/**",
                                "/health",
                                "/uploads/**",
                                "/swagger-ui/**",
                                "/api-docs/**",
                                "/mock/**"
                        ).permitAll()

                        // 管理员专属接口
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // 课程/试卷/题目等资源：POST、PUT、DELETE、PATCH 仅教师、管理员可操作
                        .requestMatchers(HttpMethod.POST, commonResourcePaths.toArray(new String[0]))
                        .hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, commonResourcePaths.toArray(new String[0]))
                        .hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, commonResourcePaths.toArray(new String[0]))
                        .hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/courses/**")
                        .hasAnyRole("TEACHER", "ADMIN")

                        // 选课审批、作业评分接口：仅教师、管理员可操作
                        .requestMatchers(
                                HttpMethod.POST,
                                "/enrollments/*/approve",
                                "/enrollments/*/reject",
                                "/submissions/*/grade"
                        ).hasAnyRole("TEACHER", "ADMIN")

                        // 其余所有接口，必须完成登录认证
                        .anyRequest().authenticated()
                )
                // 在账号密码认证过滤器之前，执行JWT令牌校验
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 跨域全局配置
     * 适配前端Vite开发服务，允许跨域请求、自定义请求头与凭证携带
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        // 允许的前端访问域名
        corsConfig.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:5174",
                "http://localhost:5175",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:5174",
                "http://127.0.0.1:5175"
        ));
        // 允许的请求方式
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        // 放行所有请求头
        corsConfig.setAllowedHeaders(List.of("*"));
        // 允许前端携带Cookie、Token等认证凭证
        corsConfig.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return source;
    }
}