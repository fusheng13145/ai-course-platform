package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 认证/授权失败统一响应处理器。
 *
 * <p><b>修复记录（2026-09-14，P2）</b>：本类原先无差别返回 <b>401「未授权，请先登录」</b>。
 * 但 Spring Security 的 {@code ExceptionTranslationFilter} 在「URL 级授权规则（如
 * {@code POST /courses/** → hasAnyRole(TEACHER,ADMIN)}）拒绝一个<b>已登录</b>用户」时，
 * 同样会走 EntryPoint 而不是 AccessDeniedHandler，于是「已登录但权限不足」被误报为「未登录」——
 * 实测：学生调用管理员接口 / 教师接口均得 401；而 {@code /student/**} 规则拒绝教师却得 403，
 * 同一系统两套口径，前端 403 页面永远不可达。</p>
 *
 * <p>现按「上下文中是否存在已认证主体」区分语义：
 * <ul>
 *   <li>已认证（非匿名）→ <b>403 权限不足</b>；</li>
 *   <li>未认证 / 匿名 / 令牌失效 → <b>401 未授权</b>。</li>
 * </ul>
 * </p>
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        // ApiResponse.timestamp 为 LocalDateTime，需注册 JavaTime 模块方可序列化，
        // 否则未认证响应序列化时会抛出 InvalidDefinitionException，导致 401 体为空/报错。
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isAuthenticated(authentication)) {
            log.warn("[JwtAuth] 已认证但权限不足，按 403 返回：uri={}, user={}, roles={}, msg={}",
                    request.getRequestURI(), authentication.getName(),
                    rolesOf(authentication), authException.getMessage());
            write(response, HttpServletResponse.SC_FORBIDDEN,
                    ApiCode.FORBIDDEN, "权限不足，禁止访问");
            return;
        }
        log.warn("[JwtAuth] 未认证/令牌无效，返回 401：uri={}, msg={}",
                request.getRequestURI(), authException.getMessage());
        write(response, HttpServletResponse.SC_UNAUTHORIZED,
                ApiCode.UNAUTHORIZED, "未授权，请先登录");
    }

    /** 上下文主体是否为「已认证且非匿名」。 */
    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String rolesOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
    }

    private void write(HttpServletResponse response, int status, int code, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(status);
        ApiResponse<Object> result = ApiResponse.error(code, message);
        OBJECT_MAPPER.writeValue(response.getOutputStream(), result);
    }
}
