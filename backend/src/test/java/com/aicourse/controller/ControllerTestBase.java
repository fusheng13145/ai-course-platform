package com.aicourse.controller;

import com.aicourse.common.AdminScope;
import com.aicourse.config.CorsProperties;
import com.aicourse.config.SecurityConfig;
import com.aicourse.security.JwtAuthenticationEntryPoint;
import com.aicourse.security.JwtRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * 控制器接口测试共享基类。
 *
 * <p>本项目的控制器普遍以 {@code (Long) auth.getPrincipal()} 取出当前用户 ID，鉴权由
 * {@code SecurityConfig} + {@code JwtRequestFilter} 处理。为使 {@code @WebMvcTest} 切片测试
 * 能干净启动并验证鉴权，本类统一处理两件事：
 * <ol>
 *     <li>以 {@code @MockitoBean} 满足 {@code SecurityConfig} 的构造依赖
 *         （{@code JwtRequestFilter} / {@code JwtAuthenticationEntryPoint} / {@code CorsProperties}），
 *         避免上下文因缺少这些 bean 而启动失败；</li>
 *     <li>提供 {@link #withUser(Long, String...)} 向 {@code SecurityContext} 注入以 Long 为 principal 的
 *         已认证主体（模拟 JWT 过滤器解析后的用户），并用 securityContext post-processor 附加到请求；
 *         未认证请求由 {@code jwtAuthenticationEntryPoint} 的 stub 返回 401，从而可断言鉴权拦截。</li>
 * </ol>
 *
 * <p>子类只需声明 {@code @WebMvcTest(XxxController.class)} 并 {@code @MockitoBean} 自己的 Service 依赖即可。
 *
 * <p>测试环境覆盖：
 * <ul>
 *     <li>{@code server.servlet.context-path} 在 {@code @WebMvcTest} 中会生效，导致裸路径（如 {@code /auth/login}）
 *         与 SecurityConfig 中 {@code /auth/**} 的 permitAll 规则不匹配而被拦截；此处清空 context-path，
 *         使测试请求可直接使用控制器声明的相对路径。</li>
 *     <li>{@code PasswordEncoder} 由 SecurityConfig 定义，但在 web 切片中可能因配置加载顺序而缺失，
 *         故以 {@code @MockitoBean} 兜底，保证安全链路可正常装配。</li>
 * </ul>
 */
@TestPropertySource(properties = {
        "server.servlet.context-path=",
        // 切片测试用 MockMvc 走明文 http：关闭 HTTPS 强制与 Secure Cookie，
        // 否则 SecurityConfig 的 requiresChannel().requiresSecure() 会把请求 302 重定向至 https，
        // 使所有控制器断言（期望 200/401/403）失败。生产默认仍为安全值（true）。
        "app.security.require-https=false",
        "app.cookie.secure=false",
        // 切片测试用 MockMvc，无前端 axios 回填 X-XSRF-TOKEN；关闭 CSRF 双提交以免写请求 403。
        // CSRF 逻辑由 DoubleSubmitCsrfFilterTest 专测覆盖。
        "app.security.csrf.enabled=false"
})
@AutoConfigureMockMvc
// RestAccessDeniedHandler 为真实 bean（非 mock）：它直接写出 403 + 业务信封，
// 用于覆盖「已认证但权限不足」分支；若当作 mock 则默认不写响应，403 断言会退化为 200。
@Import({SecurityConfig.class, com.aicourse.security.RestAccessDeniedHandler.class})
public abstract class ControllerTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected JwtRequestFilter jwtRequestFilter;
    @MockitoBean
    protected JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    @MockitoBean
    protected CorsProperties corsProperties;
    @MockitoBean
    protected PasswordEncoder passwordEncoder;
    // AsyncMvcConfig（WebMvcConfigurer）会被 @WebMvcTest 切片加载，其构造依赖 AI 配额拦截器。
    // 切片不创建业务 @Component，故以 @MockitoBean 兜底满足装配；preHandle 默认 stub 为放行，
    // 使 AI 配额逻辑与控制器切片测试解耦（配额本身由 AiQuotaServiceTest 专测覆盖）。
    @MockitoBean
    protected com.aicourse.security.AiQuotaInterceptor aiQuotaInterceptor;

    @BeforeEach
    void stubAiQuotaInterceptorPassthrough() throws Exception {
        // 拦截器 mock 的 preHandle 默认返回 false 会中断请求链，必须显式放行。
        org.mockito.Mockito.when(aiQuotaInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @BeforeEach
    void stubUnauthenticatedEntryPoint() {
        // commence(...) 声明抛出 IOException/ServletException；Mockito 的 doAnswer 形式不会真正调用真实方法，
        // 但编译器仍要求处理受检异常，故在此处兜底捕获（运行时不会触发）。
        try {
            doAnswer(inv -> {
                // AuthenticationEntryPoint.commence(HttpServletRequest, HttpServletResponse, AuthenticationException)
                // 索引 1 才是响应对象，务必避免误用索引 2（即异常参数）导致类型转换失败。
                HttpServletResponse response = inv.getArgument(1);
                try {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                } catch (Exception ignored) {
                    // 测试中响应已提交或不可写时忽略
                }
                return null;
            }).when(jwtAuthenticationEntryPoint).commence(any(), any(), any());
        } catch (Exception ignored) {
            // Mockito 不会真正调用 commence，此处仅为满足编译器对受检异常的要求
        }

        // JwtRequestFilter 在真实链路中负责从 JWT 解析并写入认证主体。测试里我们用 withUser(...) 直接
        // 注入主体，因此让该过滤器 mock 透传请求（必须调用 chain.doFilter），否则请求会在该过滤器处被丢弃，
        // 导致鉴权过滤器与控制器都拿不到请求，出现 200/401/403 行为不一致的问题。
        try {
            doAnswer(inv -> {
                FilterChain chain = inv.getArgument(2);
                chain.doFilter(inv.getArgument(0), inv.getArgument(1));
                return null;
            }).when(jwtRequestFilter).doFilter(any(), any(), any());
        } catch (Exception ignored) {
            // Mockito 不会真正调用 doFilter，此处仅为满足编译器对受检异常的要求
        }
    }

    /**
     * 构造携带 Long userId principal 的已认证上下文（模拟 JWT 过滤器解析后的主体）。
     *
     * @param userId 用户 ID（对应控制器中 {@code (Long) auth.getPrincipal()}）
     * @param roles  角色名（不含 ROLE_ 前缀，方法会自动补全），如 "ADMIN"、"TEACHER"、"STUDENT"
     */
    protected static RequestPostProcessor withUser(Long userId, String... roles) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, authorities));
        return SecurityMockMvcRequestPostProcessors.securityContext(context);
    }

    /**
     * 构造携带任意 authority 的已认证上下文（用于管理员作用域等非 ROLE_ 前缀的授权）。
     *
     * @param userId      用户 ID
     * @param authorities 完整 authority 字符串，如 {@code "ROLE_ADMIN"}、{@code "SCOPE_ADMIN_COLLEGE"}
     */
    protected static RequestPostProcessor withAuthorities(Long userId, String... authorities) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, granted));
        return SecurityMockMvcRequestPostProcessors.securityContext(context);
    }

    /** 校级管理员（ROLE_ADMIN + SCOPE_ADMIN_SCHOOL）。 */
    protected static RequestPostProcessor withSchoolAdmin(Long userId) {
        return withAuthorities(userId, "ROLE_ADMIN", AdminScope.AUTHORITY_SCHOOL);
    }

    /** 院系管理员（ROLE_ADMIN + SCOPE_ADMIN_COLLEGE）。 */
    protected static RequestPostProcessor withCollegeAdmin(Long userId) {
        return withAuthorities(userId, "ROLE_ADMIN", AdminScope.AUTHORITY_COLLEGE);
    }

    /**
     * 在请求构造器上附加认证信息（便捷写法）。
     */
    protected MockHttpServletRequestBuilder withUser(MockHttpServletRequestBuilder builder, Long userId, String... roles) {
        return builder.with(withUser(userId, roles));
    }
}
