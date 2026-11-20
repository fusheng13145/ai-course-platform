package com.aicourse.config;

import com.aicourse.security.JwtAuthenticationEntryPoint;
import com.aicourse.security.JwtRequestFilter;
import com.aicourse.security.RestAccessDeniedHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * CORS 口径矩阵（S-02）。
 *
 * <p>钉住三件事：① base 配置的 localhost 白名单在生产 profile 下被顶层 {@code cors:} 清空；
 * ② 空列表 + {@code app.cors.dev-fallback=false} 时真正 fail-closed，而不是回落到 base 的 9 条；
 * ③ 显式白名单与 {@code allowCredentials(true)} 的组合不变。</p>
 */
@DisplayName("CORS 配置矩阵")
class CorsConfigurationTest {

    private SecurityConfig securityConfig(List<String> origins, boolean devFallback) {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(origins);
        return new SecurityConfig(
                mock(JwtRequestFilter.class),
                mock(JwtAuthenticationEntryPoint.class),
                mock(RestAccessDeniedHandler.class),
                properties,
                false,
                true,
                false,
                devFallback);
    }

    private CorsConfiguration corsOf(SecurityConfig config) {
        CorsConfigurationSource source = config.corsConfigurationSource();
        return source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/courses"));
    }

    @Test
    @DisplayName("显式白名单原样生效，凭证与常用方法保持")
    void explicitOriginsArePreserved() {
        CorsConfiguration cors = corsOf(securityConfig(List.of("https://app.example.com"), false));

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("https://app.example.com");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
        assertThat(cors.getAllowedHeaders()).contains("X-XSRF-TOKEN");
        assertThat(cors.checkOrigin("https://evil.example.com")).isNull();
    }

    @Test
    @DisplayName("未配置 + dev-fallback=true：只回退 localhost:5173")
    void emptyOriginsFallbackToLocalhost() {
        CorsConfiguration cors = corsOf(securityConfig(List.of(), true));

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:5173");
        assertThat(cors.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
        assertThat(cors.checkOrigin("https://evil.example.com")).isNull();
    }

    @Test
    @DisplayName("未配置 + dev-fallback=false：fail-closed，拒绝一切跨域")
    void emptyOriginsFailClosedWhenFallbackDisabled() {
        CorsConfiguration cors = corsOf(securityConfig(List.of(), false));

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).isEmpty();
        assertThat(cors.checkOrigin("http://localhost:5173")).isNull();
    }

    private List<String> indexedOrigins(PropertySource<?> source) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (int i = 0; ; i++) {
            Object value = source.getProperty("cors.allowed-origins[" + i + "]");
            if (value == null) {
                return values;
            }
            values.add(String.valueOf(value));
        }
    }

    @Test
    @DisplayName("base 配置有 9 条本地白名单，prod 顶层 cors 将其清空")
    void prodProfileClearsBaseWhitelist() throws Exception {
        List<PropertySource<?>> base = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        List<PropertySource<?>> prod = new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"));

        List<String> baseOrigins = indexedOrigins(base.get(0));
        List<String> prodOrigins = indexedOrigins(prod.get(0));

        assertThat(baseOrigins).as("base 应保留 9 条本地开发白名单").hasSize(9);
        assertThat(prodOrigins).as("prod 必须显式清空 base 白名单").isEmpty();
    }
}
