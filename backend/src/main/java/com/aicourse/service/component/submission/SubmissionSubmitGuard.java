package com.aicourse.service.component.submission;

import com.aicourse.common.ApiCode;
import com.aicourse.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 作业提交并发去重护栏（Q-02）：同一 (taskId, studentId) 的写操作在任一时刻只允许一个在途。
 *
 * <p><b>为什么需要它：</b>{@code SubmissionWriteComponent} 的三条写路径（正式提交 / 存草稿 / 重交）
 * 都是「先 count/select 判断再 insert」的读改写序列。并发双击或前端重试时，两个事务会同时
 * 读到「尚无提交」，随后各自 insert 占号 —— V24 对 {@code (task_id, student_id, attempt_number)}
 * 的唯一键会直接抛出数据库异常（500），或因草稿/正式行分裂产生重复记录。按 Q-02 裁定，
 * <b>不加数据库唯一索引</b>收敛为应用层互斥，本组件即该互斥的唯一实现点。</p>
 *
 * <p><b>锁的边界必须覆盖事务提交：</b>若在事务方法返回时立即释放锁，第二个请求可能在第一个事务
 * commit 之前就重新读到旧计数，原竞态重新出现。因此 {@link Lease#close()} 不直接释放，
 * 而是登记 {@link TransactionSynchronization#afterCompletion}，把释放推迟到事务真正结束
 * （提交或回滚皆然）；无事务上下文（如单元测试直调组件）时退化为立即释放。</p>
 *
 * <p><b>Redis 优先、进程内兜底：</b>
 * <ul>
 *   <li>Redis 可用：{@code SET key token NX EX} 原子占锁，释放走 compare-and-delete Lua
 *       （杜绝误删他人锁）；token 随机 UUID，避免 A 释放 B 的锁。</li>
 *   <li>Redis 不可用：降级为进程内 {@link ReentrantLock} 互斥 —— 单实例下语义不变，
 *       多实例部署时退化为 best-effort（绝不因 Redis 故障直接放行并发写）。</li>
 * </ul>
 *
 * <p><b>刻意 fail-fast：</b>抢锁不等待，立即返回 409「提交正在处理中」。同一瞬间的第二个请求
 * 几乎必然是双击/重试，而非学生有意发起的第二次尝试；让它排队等待反而会把并发重复
 * 变成一次「合法的第二次提交」，违背去重目的。学生若确实要再次提交，等首个请求返回后
 * 正常走 attemptNumber 递增路径即可。</p>
 */
@Slf4j
@Component
public class SubmissionSubmitGuard {

    /** 锁键前缀，形如 {@code lock:submit:{taskId}:{studentId}}（Q-02 约定）。 */
    private static final String KEY_PREFIX = "lock:submit:";

    /** 锁 TTL：仅为进程崩溃后自愈兜底；正常路径由事务完成后显式释放。 */
    private static final Duration LEASE_TTL = Duration.ofSeconds(15);

    /** compare-and-delete：只有值仍为自己的 token 才删除，避免释放他人持有的锁。 */
    private static final String RELEASE_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>();

    static {
        RELEASE_SCRIPT.setScriptText(RELEASE_LUA);
        RELEASE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    /** Redis 缺失/故障时的进程内锁表：key → 锁对象。 */
    private final Map<String, ReentrantLock> localLocks = new ConcurrentHashMap<>();

    @Autowired
    public SubmissionSubmitGuard(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    /** 直连注入（供单元测试与无 Spring 容器的场景使用；传 null 表示强制走进程内兜底路径）。 */
    public SubmissionSubmitGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 为 (taskId, studentId) 抢占写锁；抢不到立即抛 409。
     *
     * @return 租约，必须在 finally / try-with-resources 中关闭（关闭动作本身会把释放推迟到事务完成）
     */
    public Lease acquire(Long taskId, Long studentId) {
        if (taskId == null || studentId == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "提交锁缺少任务或学生标识");
        }
        String key = KEY_PREFIX + taskId + ":" + studentId;
        if (redisTemplate != null) {
            try {
                String token = UUID.randomUUID().toString();
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, LEASE_TTL);
                if (Boolean.TRUE.equals(acquired)) {
                    return new RedisLease(key, token);
                }
                throw busy();
            } catch (DataAccessException e) {
                log.warn("Redis 提交锁不可用，降级为进程内互斥: key={}", key, e);
            }
        }
        return acquireLocal(key);
    }

    private Lease acquireLocal(String key) {
        ReentrantLock lock = localLocks.computeIfAbsent(key, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw busy();
        }
        return new LocalLease(key, lock);
    }

    private BusinessException busy() {
        return new BusinessException(ApiCode.CONFLICT, "提交正在处理中，请勿重复提交，稍后查看结果");
    }

    /**
     * 把释放动作推迟到事务完成；无事务上下文时立即执行。
     * <p>用 afterCompletion 而非 afterCommit：回滚同样必须释放，否则异常路径会一直占锁到 TTL。</p>
     */
    private static void releaseAfterTransaction(Runnable release) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    release.run();
                }
            });
        } else {
            release.run();
        }
    }

    /** 写锁租约；重复 close 幂等。 */
    public interface Lease extends AutoCloseable {

        @Override
        void close();
    }

    /** Redis 租约：compare-and-delete 释放。 */
    private final class RedisLease implements Lease {

        private final String key;
        private final String token;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private RedisLease(String key, String token) {
            this.key = key;
            this.token = token;
        }

        @Override
        public void close() {
            if (!released.compareAndSet(false, true)) {
                return;
            }
            releaseAfterTransaction(() -> {
                try {
                    redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
                } catch (DataAccessException e) {
                    // 释放失败不改变业务结果：锁会随 TTL 自愈，仅记录
                    log.warn("释放提交锁失败，等待 TTL 自愈: key={}", key, e);
                }
            });
        }
    }

    /** 进程内租约：unlock 后若无等待者则回收锁对象，避免锁表随 (task,student) 组合无限增长。 */
    private final class LocalLease implements Lease {

        private final String key;
        private final ReentrantLock lock;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private LocalLease(String key, ReentrantLock lock) {
            this.key = key;
            this.lock = lock;
        }

        @Override
        public void close() {
            if (!released.compareAndSet(false, true)) {
                return;
            }
            releaseAfterTransaction(() -> {
                synchronized (lock) {
                    lock.unlock();
                    if (!lock.isLocked() && !lock.hasQueuedThreads()) {
                        localLocks.remove(key, lock);
                    }
                }
            });
        }
    }
}
