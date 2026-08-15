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
     * 默认 false：限流以 TCP 对端地址（getRemoteAddr）为准，防止客户端伪造 XFF 头绕过 IP 限流；
     * 仅在部署于受信任反向代理（nginx/网关）之后且代理已覆写该头时才可显式开启。
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
        // 安全默认：不信任客户端可伪造的 X-Forwarded-For，使用 TCP 对端地址
        if (trustProxyHeader) {
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
