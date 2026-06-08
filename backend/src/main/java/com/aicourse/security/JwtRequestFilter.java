package com.aicourse.security;

import com.aicourse.util.JwtUtils;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    public JwtRequestFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain chain) throws ServletException, IOException {
        // 从请求头提取 Token
        String authHeader = request.getHeader("Authorization");
        String token = jwtUtils.extractTokenFromHeader(authHeader);

        // Token 非空且校验通过，则构建认证信息
        if (token != null && jwtUtils.validateToken(token)) {
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
}