package com.aicourse.service;

import com.aicourse.common.ApiCode;
import com.aicourse.exception.BusinessException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 用量配额服务（按认证用户维度的每日预算 + token 成本预算）。
 *
 * <p><b>为什么需要它——把「没人会恶意刷 AI」这个乐观假设变成硬保证：</b>
 * 通用限流按 IP 计数，攻击者可用住宅代理池轮换 IP 绕过，且只拦「请求次数」拦不住「每次的 token 成本」。
 * 本服务改为按 {@code userId} 计数（认证后身份，代理池无法伪造），并与请求体 prompt 长度硬上限配合，
 * 形成对单用户 LLM 成本的<b>数学上界</b>：</p>
 *
 * <pre>
 *   单用户单日最坏成本 ≤ dailyRequestLimit × 单次 prompt 上限 token
 *                     且 ≤ dailyTokenBudget（二者取更严格者先触发熔断）
 * </pre>
 *
 * <p>实现要点：
 * <ul>
 *   <li>Redis 模式：{@code INCR + EXPIRE(到当日 24:00)} 原子计数，集群内一致，攻击者无法轮询节点绕过；</li>
 *   <li>Redis 不可用：透明降级为本地内存计数（进程级），保证「Redis 挂了也不会门户大开」——
 *       宁可退化为单实例配额，也绝不退化为「无配额」；</li>
 *   <li>超额：抛 {@link BusinessException}(429)，由全局异常处理器返回 HTTP 429。</li>
 * </ul>
 */
@Service
@Slf4j
public class AiQuotaService {

    /** 原子计数 + 首次设置到当日 24:00 过期（秒）。返回累计值。 */
    private static final String QUOTA_LUA =
            "local current = redis.call('INCRBY', KEYS[1], ARGV[1]) " +
            "if current == tonumber(ARGV[1]) then " +
            "    redis.call('EXPIRE', KEYS[1], ARGV[2]) " +
            "end " +
            "return current";

    private static final DefaultRedisScript<Long> QUOTA_SCRIPT = new DefaultRedisScript<>();

    static {
        QUOTA_SCRIPT.setScriptText(QUOTA_LUA);
        QUOTA_SCRIPT.setResultType(Long.class);
    }

    @Value("${ai.quota.enabled:true}")
    private boolean enabled;

    /** 单用户每日 AI 调用次数上限。 */
    @Value("${ai.quota.daily-request-limit:100}")
    private int dailyRequestLimit;

    /** 单用户每日 AI token 预算（估算值，prompt+响应字符折算）。 */
    @Value("${ai.quota.daily-token-budget:200000}")
    private long dailyTokenBudget;

    private final StringRedisTemplate redisTemplate;

    /** 本地兜底计数：key = 维度:userId:yyyyMMdd。 */
    private final Map<String, long[]> localCounters = new ConcurrentHashMap<>();
    private volatile LocalDate localCounterDate = LocalDate.now();

    public AiQuotaService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    /**
     * 在进入 LLM 调用<b>之前</b>校验并占用一次请求配额；超额直接熔断。
     *
     * @param userId 认证用户 ID（匿名/未认证不计费，交由上层鉴权拦截）
     * @throws BusinessException 429 当日请求次数超限
     */
    public void assertRequestQuota(Long userId) {
        if (!enabled || userId == null) {
            return;
        }
        long used = increment("aiq:req:" + userId, 1);
        if (used > dailyRequestLimit) {
            throw new BusinessException(ApiCode.TOO_MANY_REQUESTS,
                    "今日 AI 使用次数已达上限（" + dailyRequestLimit + " 次/日），请明日再试或联系管理员提额。");
        }
    }

    /**
     * 记录本次 LLM 调用的估算 token 成本；累计超过每日预算则对<b>后续</b>请求熔断。
     * 采用「先放行本次、超额后拦截下次」的宽松策略，避免因估算误差误杀正在进行的请求。
     *
     * @param userId          认证用户 ID
     * @param estimatedTokens 估算 token 数（可由字符数折算）
     */
    public void recordTokenUsage(Long userId, long estimatedTokens) {
        if (!enabled || userId == null || estimatedTokens <= 0) {
            return;
        }
        increment("aiq:tok:" + userId, estimatedTokens);
    }

    /**
     * 校验 token 预算是否已耗尽（在请求进入前调用，与 {@link #assertRequestQuota} 并列）。
     *
     * @throws BusinessException 429 当日 token 预算已耗尽
     */
    public void assertTokenBudget(Long userId) {
        if (!enabled || userId == null) {
            return;
        }
        long used = current("aiq:tok:" + userId);
        if (used >= dailyTokenBudget) {
            throw new BusinessException(ApiCode.TOO_MANY_REQUESTS,
                    "今日 AI 用量预算已耗尽，请明日再试或联系管理员提额。");
        }
    }

    /** 估算字符对应 token 数：中英文混合场景保守按 1 token ≈ 1.5 字符折算。 */
    public long estimateTokens(String... texts) {
        long chars = 0;
        if (texts != null) {
            for (String t : texts) {
                if (t != null) {
                    chars += t.length();
                }
            }
        }
        return estimateTokensFromChars(chars);
    }

    /** 直接按字符数折算 token（供拦截器用 Content-Length 估算，避免分配大字符串）。 */
    public long estimateTokensFromChars(long chars) {
        if (chars <= 0) {
            return 0;
        }
        return (long) Math.ceil(chars / 1.5);
    }

    // ==================== 计数实现（Redis 优先，本地兜底） ====================

    private long increment(String logicalKey, long delta) {
        String key = logicalKey + ":" + today();
        if (redisTemplate != null) {
            try {
                Long v = redisTemplate.execute(QUOTA_SCRIPT, List.of(key),
                        String.valueOf(delta), String.valueOf(secondsUntilEndOfDay()));
                if (v != null) {
                    return v;
                }
            } catch (DataAccessException ignored) {
                log.warn("Redis 故障，降级本地计数", ignored);
                // Redis 故障 → 降级本地，绝不因 Redis 不可用而放弃配额
            }
        }
        return incrementLocal(key, delta);
    }

    private long current(String logicalKey) {
        String key = logicalKey + ":" + today();
        if (redisTemplate != null) {
            try {
                String v = redisTemplate.opsForValue().get(key);
                return v == null ? 0 : Long.parseLong(v);
            } catch (DataAccessException ignored) {
                log.warn("Redis 读取失败，降级本地计数", ignored);
                // 降级本地
            }
        }
        long[] c = localCounters.get(key);
        return c == null ? 0 : c[0];
    }

    private long incrementLocal(String key, long delta) {
        rollLocalDateIfNeeded();
        long[] counter = localCounters.compute(key, (k, v) -> {
            if (v == null) {
                return new long[]{delta};
            }
            v[0] += delta;
            return v;
        });
        return counter[0];
    }

    /** 跨天清空本地兜底计数，避免内存无限增长且防止昨日计数误算入今日。 */
    private void rollLocalDateIfNeeded() {
        LocalDate now = LocalDate.now();
        if (!now.equals(localCounterDate)) {
            synchronized (this) {
                if (!now.equals(localCounterDate)) {
                    localCounters.clear();
                    localCounterDate = now;
                }
            }
        }
    }

    private String today() {
        return LocalDate.now().toString().replace("-", "");
    }

    private long secondsUntilEndOfDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfDay = LocalDateTime.of(now.toLocalDate(), LocalTime.MAX);
        long secs = java.time.Duration.between(now, endOfDay).getSeconds() + 1;
        return Math.max(secs, 1);
    }

    // ==================== 供测试注入配置 ====================

    void configureForTest(boolean enabled, int dailyRequestLimit, long dailyTokenBudget) {
        this.enabled = enabled;
        this.dailyRequestLimit = dailyRequestLimit;
        this.dailyTokenBudget = dailyTokenBudget;
    }

    ZoneId zone() {
        return ZoneId.systemDefault();
    }
}
