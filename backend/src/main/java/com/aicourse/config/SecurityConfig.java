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
                .requestMatchers("/auth/**", "/common/**", "/health", "/uploads/**",
                    "/swagger-ui/**", "/api-docs/**", "/mock/**").permitAll()
                // 管理员接口
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // 教师/管理员的写操作
                .requestMatchers(HttpMethod.POST, "/courses/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/courses/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/courses/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/courses/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/exam-papers/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/exam-papers/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/exam-papers/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/questions/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/questions/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/questions/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/tasks/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/tasks/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/tasks/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/knowledge-points/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/knowledge-points/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/knowledge-points/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/chapters/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/chapters/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/chapters/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/resources/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/resources/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/resources/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/classrooms/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/classrooms/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/classrooms/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/enrollments/*/approve").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/enrollments/*/reject").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/submissions/*/grade").hasAnyRole("TEACHER", "ADMIN")
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