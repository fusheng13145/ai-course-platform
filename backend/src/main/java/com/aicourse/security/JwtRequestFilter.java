package com.aicourse.security;

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
import java.util.List;

/**
 * JWT 请求拦截过滤器
 * 每请求执行一次，解析并校验 Token，构建认证信息存入安全上下文
 */
@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserSessionService userSessionService;

    public JwtRequestFilter(JwtUtils jwtUtils, UserSessionService userSessionService) {
        this.jwtUtils = jwtUtils;
        this.userSessionService = userSessionService;
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

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        // 优先从 Authorization 头提取 Token；缺省时回退到 HttpOnly Cookie（ai_course_token），
        // 使前端在 withCredentials 下无需手动携带令牌，同时兼容非浏览器/接口工具经头部传参。
        String token = extractToken(request);

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
            String role = jwtUtils.getRoleFromToken(token);

            // 封装角色权限（Spring Security 角色需前缀 ROLE_）
            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            // 构建认证对象，主体存入 userId，凭证存入 username
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, username, authorities);
            // 写入安全上下文，后续接口可直接获取登录用户信息
            SecurityContextHolder.getContext().setAuthentication(authentication);
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