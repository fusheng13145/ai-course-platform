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
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtRequestFilter jwtRequestFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    public SecurityConfig(JwtRequestFilter jwtRequestFilter,
                          JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.jwtRequestFilter = jwtRequestFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
            .authorizeHttpRequests(auth -> auth
                // 公开接口
                .requestMatchers("/auth/**", "/common/captcha", "/common/verify-code",
                    "/common/verification-code", "/common/upload",
                    "/common/feature-roadmap", "/health", "/uploads/**",
                    "/swagger-ui/**", "/api-docs/**", "/mock/**").permitAll()
                // 管理员接口
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/users/**").hasRole("ADMIN")
                // 教师接口
                .requestMatchers("/teacher/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers("/exam-papers/**", "/questions/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers("/tasks/**", "/submissions/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers("/knowledge-points/**").hasAnyRole("TEACHER", "ADMIN")
                // 学生接口
                .requestMatchers("/student/**").hasAnyRole("STUDENT", "TEACHER", "ADMIN")
                .requestMatchers("/enrollments/**", "/learning-paths/**").hasAnyRole("STUDENT", "TEACHER", "ADMIN")
                .requestMatchers("/wrong-questions/**", "/video-records/**").hasAnyRole("STUDENT", "TEACHER", "ADMIN")
                // 其他接口需要认证
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:5174",
            "http://127.0.0.1:5173", "http://127.0.0.1:5174"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}