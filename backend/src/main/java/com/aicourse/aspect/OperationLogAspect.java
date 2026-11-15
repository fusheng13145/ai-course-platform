package com.aicourse.aspect;

import com.aicourse.entity.OperationLog;
import com.aicourse.service.OperationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 操作日志AOP切面
 * 拦截所有Controller方法调用, 记录POST/PUT/DELETE操作审计日志
 */
@Aspect
@Component
@Slf4j
public class OperationLogAspect {

    private final OperationLogService operationLogService;
    /**
     * 审计 IP 单一来源（DR-66 收口口径）：与限流键/登录节流共用同一
     * {@link TrustedClientIpResolver}，由 {@code app.security.trust-proxy-header} 单点决定
     * 是否信任反代头。审计切面此前自读 {@code X-Forwarded-For} 最左段——直连形态下该头
     * 客户端可控，审计记录可被伪造头污染（2026-11-14 审计发现 N4）。
     */
    private final com.aicourse.util.TrustedClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OperationLogAspect(OperationLogService operationLogService,
                              com.aicourse.util.TrustedClientIpResolver clientIpResolver) {
        this.operationLogService = operationLogService;
        this.clientIpResolver = clientIpResolver;
    }

    @Around("execution(* com.aicourse.controller..*.*(..))")
    public Object aroundController(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = getCurrentRequest();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String httpMethod = request != null ? request.getMethod() : "UNKNOWN";

        // 只记录写操作
        if (!"POST".equalsIgnoreCase(httpMethod)
                && !"PUT".equalsIgnoreCase(httpMethod)
                && !"DELETE".equalsIgnoreCase(httpMethod)
                && !"PATCH".equalsIgnoreCase(httpMethod)) {
            return joinPoint.proceed();
        }

        long startTime = System.currentTimeMillis();
        OperationLog log = new OperationLog();
        log.setMethod(httpMethod);
        log.setTarget(joinPoint.getSignature().toShortString());

        try {
            // 获取当前用户
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Long) {
                log.setUserId((Long) auth.getPrincipal());
                log.setUsername(auth.getName());
            }

            // 获取IP和UA
            if (request != null) {
                log.setIp(getClientIp(request));
                log.setUserAgent(request.getHeader("User-Agent"));
            }

            // 记录请求参数（修复 P1-5：登录/改密等接口的明文密码、验证码、令牌不得落入审计日志，
            // 序列化后对敏感字段统一打码，防止日志库泄露即凭据泄露）
            Object[] args = joinPoint.getArgs();
            try {
                String params = objectMapper.writeValueAsString(args);
                params = maskSensitiveFields(params);
                if (params.length() > 1000) params = params.substring(0, 1000) + "...";
                log.setParams(params);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(OperationLogAspect.class).warn("序列化操作日志参数失败", e);
            }

            // 解析模块名
            String className = joinPoint.getTarget().getClass().getSimpleName();
            log.setModule(className.replace("Controller", ""));

            // 解析操作类型
            log.setAction(resolveAction(httpMethod, signature.getName()));

            // 执行目标方法
            Object result = joinPoint.proceed();

            log.setStatus("SUCCESS");
            log.setDurationMs(System.currentTimeMillis() - startTime);
            operationLogService.save(log);

            return result;
        } catch (Throwable e) {
            log.setStatus("FAILED");
            log.setErrorMsg(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "未知错误");
            log.setDurationMs(System.currentTimeMillis() - startTime);
            operationLogService.save(log);
            throw e;
        }
    }

    /**
     * 敏感字段脱敏（修复 P1-5）：对 JSON 文本中已知敏感字段的值替换为 ***。
     * 兼容嵌套 DTO（登录/注册/改密/重置/验证码/令牌等请求体），值含转义引号亦可正确处理。
     */
    private static final String[] SENSITIVE_KEYS = {
            "password", "oldPassword", "newPassword", "confirmPassword",
            "code", "authorization", "accessToken", "refreshToken",
            "token", "secret", "apiKey", "accessKey"
    };

    private String maskSensitiveFields(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        String masked = json;
        for (String key : SENSITIVE_KEYS) {
            masked = masked.replaceAll(
                    "(?i)(\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"",
                    "$1\"***\"");
        }
        return masked;
    }

    private HttpServletRequest getCurrentRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .orElse(null);
    }

    private String getClientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }

    private String resolveAction(String httpMethod, String methodName) {
        String action = methodName;
        if (action.startsWith("create") || action.startsWith("add") || action.startsWith("register") || action.startsWith("enroll")) return "CREATE";
        if (action.startsWith("update") || action.startsWith("edit") || action.startsWith("modify") || action.startsWith("change")) return "UPDATE";
        if (action.startsWith("delete") || action.startsWith("remove") || action.startsWith("drop")) return "DELETE";
        if (action.startsWith("publish") || action.startsWith("toggle") || action.startsWith("approve") || action.startsWith("reject")) return "STATE_CHANGE";
        if (action.startsWith("import")) return "IMPORT";
        if (action.startsWith("export")) return "EXPORT";
        if (action.startsWith("submit") || action.startsWith("autoCompose") || action.startsWith("reset")) return "EXECUTE";
        if (action.startsWith("send")) return "SEND";
        if ("POST".equalsIgnoreCase(httpMethod)) return "CREATE";
        if ("PUT".equalsIgnoreCase(httpMethod)) return "UPDATE";
        if ("DELETE".equalsIgnoreCase(httpMethod)) return "DELETE";
        return "UNKNOWN";
    }
}
