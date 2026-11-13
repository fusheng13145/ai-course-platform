package com.aicourse.security;

import com.aicourse.common.AdminScope;
import com.aicourse.entity.User;
import com.aicourse.service.UserService;
import com.aicourse.service.UserSessionService;
import com.aicourse.util.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JWT 请求拦截过滤器
 * 每请求执行一次，解析并校验 Token，构建认证信息存入安全上下文
 */
@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtRequestFilter.class);

    private final JwtUtils jwtUtils;
    private final UserSessionService userSessionService;
    private final UserService userService;

    public JwtRequestFilter(JwtUtils jwtUtils, UserSessionService userSessionService, UserService userService) {
        this.jwtUtils = jwtUtils;
        this.userSessionService = userSessionService;
        this.userService = userService;
    }

    /**
     * 必须参与异步派发：AIController 的方法统一返回 Callable，由 MVC 异步线程池执行。
     * 若此处返回 true（OncePerRequestFilter 默认值），则异步派发时本过滤器被跳过，
     * SecurityContext（ThreadLocal）无法传播到异步线程，导致 /api/ai/** 在二次鉴权时被判定为未登录（401）。
     * 返回 false 可确保异步派发时重新解析并写入认证信息。
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /**
     * 每个请求解析并校验 JWT，认证成功后写入 SecurityContext；任何解析异常均记录 ERROR 并放行
     * （随后由匿名上下文落入 anyRequest().authenticated() 统一返回 401，不阻塞后续过滤器）。
     *
     * <p>【修复 2026-10-16】authorities 一律以**用户表的现行角色**为准，不再使用令牌里的
     * {@code role} claim（登录时快照）。令牌只证明身份（userId），授权属性必须实时：</p>
     * <ul>
     *   <li>管理端修改角色后，已签发令牌在有效期内仍携带旧角色。若按 claim 构造 authorities，
     *       {@code /auth/permissions} 与前端菜单按 DB 新角色渲染、REST 鉴权按令牌旧角色拒绝，
     *       出现「UI 是管理台、API 全 403」的分裂态（线上实测：teacher 账号提权为 ADMIN 后，
     *       旧会话访问 /admin/statistics/** 全部 403）；</li>
     *   <li>降权场景更是安全缺口：被降级的管理员在令牌过期前仍持有原角色的接口权限。</li>
     * </ul>
     * <p>开销：仅在「令牌有效且会话活跃」的已认证请求上多一次 users 主键查询
     * （与 isSessionActive 的既有 DB 兜底同量级）；用户名仍取令牌 claim（非授权属性）。</p>
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        // 优先从 Authorization 头提取 Token；缺省时回退到 HttpOnly Cookie（ai_course_token），
        // 使前端在 withCredentials 下无需手动携带令牌，同时兼容非浏览器/接口工具经头部传参。
        String token = extractToken(request);

        try {
            // Token 非空且签名校验通过，再校验会话是否仍有效（支持注销/强制下线吊销）
            if (token != null && jwtUtils.validateToken(token)) {
                // 二次校验：令牌对应的会话必须处于活跃且未过期状态。
                // 用户主动登出或管理员强制下线会把会话 isActive 置 false，
                // 此时即便令牌本身未过期，也视为失效，拒绝建立认证上下文，
                // 请求将落入 anyRequest().authenticated() 被统一拒绝（401）。
                if (!userSessionService.isSessionActive(token)) {
                    chain.doFilter(request, response);
                    return;
                }

                Long userId = jwtUtils.getUserIdFromToken(token);
                String username = jwtUtils.getUsernameFromToken(token);

                // 授权属性以用户表现行状态为准（见类注释）：用户被删除或禁用时
                // 不建立认证上下文，与 /auth/refresh 的「用户不存在或已禁用」口径一致。
                User user = userService.getById(userId);
                if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
                    log.warn("[JwtRequestFilter] 用户不存在或已禁用，拒绝建立认证上下文: userId={}", userId);
                    chain.doFilter(request, response);
                    return;
                }
                String role = user.getRole();

                // 封装角色权限（Spring Security 角色需前缀 ROLE_）
                List<SimpleGrantedAuthority> authorities = new ArrayList<>(2);
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                // V12（B-12/P-08 WP1）：ADMIN 按数据库现行 admin_scope 附加作用域 authority。
                // 不写入令牌、不重签 —— scope 每请求从 users 表解析，存量会话不受影响，
                // 管理员改作用域后下一次请求立即生效。
                String scopeAuthority = AdminScope.authorityOf(role, user.getAdminScope());
                if (scopeAuthority != null) {
                    authorities.add(new SimpleGrantedAuthority(scopeAuthority));
                }
                // 构建认证对象，主体存入 userId，凭证存入 username
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, username, authorities);
                // 写入安全上下文，后续接口可直接获取登录用户信息
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.error("[JwtRequestFilter] 解析/校验令牌异常（本次按未认证处理）: {}", e.getMessage(), e);
        }

        // 放行请求
        chain.doFilter(request, response);
    }

    /**
     * 提取令牌：优先 Authorization: Bearer 头；缺失时回退到 HttpOnly Cookie。
     */
    private String extractToken(HttpServletRequest request) {
        String fromHeader = jwtUtils.extractTokenFromHeader(request.getHeader("Authorization"));
        if (fromHeader != null) {
            return fromHeader;
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (JwtUtils.ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
