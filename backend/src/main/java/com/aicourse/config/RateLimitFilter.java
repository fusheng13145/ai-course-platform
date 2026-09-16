package com.aicourse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流过滤器：优先使用 Redis 实现集群级滑动窗口限流，Redis 不可用时透明降级为本地内存限流。
 * <p>
 * 设计目标：
 * 1) 登录接口按 IP 限流，与 {@code AuthServiceImpl} 的账号级锁定互补，缓解爆破/脚本刷登录。
 * 2) AI 接口按 IP 限流，作为大模型调用成本上限的代理防护，避免单 IP 高频打爆额度。
 * 3) 验证码接口限流，防止邮件/短信轰炸。
 * 4) <b>集群一致性</b>：Redis 模式下所有实例共享同一计数窗口，攻击者无法通过轮询节点绕过。
 * <p>
 * 阈值全部配置化：{@code ratelimit.login.*} / {@code ratelimit.ai.*} / {@code ratelimit.verify-code.*}。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 原子化滑动窗口限流 Lua 脚本：
     * 1) 对当前时间窗口内的 key 执行 INCR；
     * 2) 若 key 是首次创建，则设置过期时间（窗口长度 + 1 秒缓冲）；
     * 3) 返回当前窗口内请求数。
     * 使用 Lua 保证 INCR + EXPIRE 的原子性，避免并发窗口重置导致计数错误。
     */
    private static final String SLIDE_WINDOW_LUA =
            "local current = redis.call('INCR', KEYS[1]) " +
            "if current == 1 then " +
            "    redis.call('EXPIRE', KEYS[1], ARGV[2]) " +
            "end " +
            "return current";

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();

    static {
        RATE_LIMIT_SCRIPT.setScriptText(SLIDE_WINDOW_LUA);
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    @Value("${ratelimit.enabled:true}")
    private boolean enabled;

    @Value("${ratelimit.redis.enabled:true}")
    private boolean redisEnabled;

    /**
     * 是否信任反向代理传递的 X-Forwarded-For 头。
     * <p>默认 false：忽略客户端自带的 XFF，以 {@code getRemoteAddr()} 为准。</p>
     * <p><b>⚠ 前提条件（2026-09-16 上线前全检核实）</b>：该「安全默认」成立的前提是<b>边缘代理以
     * 覆写语义</b>设置 {@code X-Forwarded-For}（本仓库 {@code frontend/nginx.conf} 已改用
     * {@code $remote_addr}）。原因是 {@code application.yaml} 配置了
     * {@code server.forward-headers-strategy: framework}，Spring 的 {@code ForwardedHeaderFilter} 会以
     * XFF 的<b>最左端</b>覆写 {@code getRemoteAddr()}，故 {@code getRemoteAddr()} <b>并不等于 TCP 对端</b>。
     * 一旦代理改为追加语义（{@code $proxy_add_x_forwarded_for}），最左端即为客户端可控值，
     * 本限流与 {@code trustProxyHeader=true} 时的 XFF 取值<b>都会被逐请求换头绕过</b>。</p>
     * <p>修改代理配置前必读 {@link com.aicourse.util.TrustedClientIpResolver} 的类注释。</p>
     */
    @Value("${ratelimit.trust-proxy-header:false}")
    private boolean trustProxyHeader;

    @Value("${ratelimit.login.limit:10}")
    private int loginLimit;

    @Value("${ratelimit.login.window-seconds:60}")
    private int loginWindowSeconds;

    @Value("${ratelimit.ai.limit:20}")
    private int aiLimit;

    @Value("${ratelimit.ai.window-seconds:60}")
    private int aiWindowSeconds;

    @Value("${ratelimit.verify-code.limit:5}")
    private int verifyLimit;

    @Value("${ratelimit.verify-code.window-seconds:60}")
    private int verifyWindowSeconds;

    /**
     * 注册接口按 IP 限流（安全修复 B1，2026-09-16 上线前全检）：
     * 注册此前可零成本批量自建账号，除「验证码强制」外再加一道 IP 维度总量约束。
     * 窗口取 3600 秒——注册是低频动作，按小时计更贴合真实使用，避免误伤共享出口 IP。
     */
    @Value("${ratelimit.register.limit:20}")
    private int registerLimit;

    @Value("${ratelimit.register.window-seconds:3600}")
    private int registerWindowSeconds;

    /** Redis 集群限流器；当 redisEnabled=false 或 Redis 不可用时回退到内存桶。 */
    private final StringRedisTemplate redisTemplate;

    /** 本地内存兜底桶（Redis 不可用或显式关闭时使用）。 */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * 使用 {@link ObjectProvider} 注入 StringRedisTemplate，使过滤器在缺少 Redis 依赖的
     * 测试切片（如 {@code @WebMvcTest}）中仍可启动，此时自动降级为纯本地内存限流。
     */
    public RateLimitFilter(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }
        // 预检请求与静态资源不做计数，避免误伤 CORS 预检
        if ("OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String clientIp = resolveClientIp(request);

        Rule rule = resolveRule(path, clientIp);
        if (rule != null && !tryAcquire(rule)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(OBJECT_MAPPER.writeValueAsString(Map.of(
                    "code", 429,
                    "message", "请求过于频繁，请稍后再试。"
            )));
            return;
        }
        chain.doFilter(request, response);
    }

    private Rule resolveRule(String path, String clientIp) {
        if (path.startsWith("/api/auth/login")) {
            return new Rule("login", clientIp, loginLimit, loginWindowSeconds);
        } else if (path.startsWith("/api/auth/register")) {
            // 安全修复 B1：注册端点补 IP 维度限流（与「验证码强制」配合，堵住批量自建账号）
            return new Rule("register", clientIp, registerLimit, registerWindowSeconds);
        } else if (path.startsWith("/api/auth/send-code")
                || path.startsWith("/api/auth/verify-code")
                || path.startsWith("/api/auth/reset-password")
                || path.startsWith("/api/auth/reset-password-by-email")) {
            // 安全修复 #24：四类未认证端点此前不在限流规则内，攻击者可在无凭据情况下脚本化
            // 向大量邮箱发信（单邮箱 1min 冷却无法阻挡跨邮箱轰炸，亦会被 SMTP 封禁）。
            // 复用 verify 桶（5 次/60s）做 IP 维度总量上限，阻断邮件轰炸。
            return new Rule("verify", clientIp, verifyLimit, verifyWindowSeconds);
        } else if (path.startsWith("/api/ai/")) {
            return new Rule("ai", clientIp, aiLimit, aiWindowSeconds);
        } else if (path.startsWith("/api/common/verify-code")) {
            return new Rule("verify", clientIp, verifyLimit, verifyWindowSeconds);
        }
        return null;
    }

    /**
     * 获取限流结果。优先 Redis 集群滑动窗口；Redis 异常时降级为本地固定窗口，保证可用性。
     */
    private boolean tryAcquire(Rule rule) {
        if (redisEnabled && redisTemplate != null) {
            try {
                return tryAcquireRedis(rule);
            } catch (Exception e) {
                // Redis 故障时降级本地限流，避免 Redis 不可用导致全站拒绝服务
                log.warn("[RateLimitFilter] Redis 限流执行异常，降级本地内存限流（bucket={}）: {}",
                        rule.bucketKey, e.getMessage());
                return tryAcquireLocal(rule);
            }
        }
        return tryAcquireLocal(rule);
    }

    private boolean tryAcquireRedis(Rule rule) {
        long nowSec = System.currentTimeMillis() / 1000;
        long windowStart = nowSec / rule.windowSeconds * rule.windowSeconds;
        String key = "ratelimit:" + rule.bucketKey + ":" + windowStart;
        // 过期时间留 1 秒缓冲，避免窗口边界瞬间 key 过期导致计数丢失
        Long count = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(rule.windowSeconds + 1));
        return count != null && count <= rule.limit;
    }

    /**
     * 本地固定窗口计数：窗口内计数未超阈值则放行并 +1；窗口过期则重置。
     * 使用 ConcurrentHashMap.compute 保证单 key 的原子性。
     */
    private boolean tryAcquireLocal(Rule rule) {
        long nowSec = System.currentTimeMillis() / 1000;
        Bucket bucket = buckets.compute(rule.bucketKey, (k, b) -> {
            if (b == null || nowSec - b.windowStart >= rule.windowSeconds) {
                return new Bucket(nowSec, 1, rule.windowSeconds);
            }
            b.count++;
            return b;
        });
        // 惰性清理：控制内存增长
        if (buckets.size() > 4096) {
            buckets.entrySet().removeIf(e -> nowSec - e.getValue().windowStart >= e.getValue().windowSeconds);
        }
        return bucket.count <= rule.limit;
    }

    private String resolveClientIp(HttpServletRequest request) {
        // 安全默认：不信任客户端可伪造的 X-Forwarded-For，使用 getRemoteAddr()。
        // 注意：framework 策略下 getRemoteAddr() 已被 ForwardedHeaderFilter 以 XFF 最左端覆写，
        // 故此处安全性的真正前提是「边缘代理以覆写语义设置 XFF」，而非「读到的一定是 TCP 对端」；
        // 详见 trustProxyHeader 字段注释与 TrustedClientIpResolver 类注释。
        if (trustProxyHeader) {
            // 取 XFF 最左段：仅在边缘代理覆写（$remote_addr）时安全；追加语义下该段由客户端控制。
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private static final class Bucket {
        long windowStart;
        int count;
        final int windowSeconds;

        Bucket(long windowStart, int count, int windowSeconds) {
            this.windowStart = windowStart;
            this.count = count;
            this.windowSeconds = windowSeconds;
        }
    }

    private static final class Rule {
        final String bucketKey;
        final int limit;
        final int windowSeconds;

        Rule(String type, String clientIp, int limit, int windowSeconds) {
            this.bucketKey = type + ":" + clientIp;
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }
    }
}
