package com.aicourse.service.component.submission;

import com.aicourse.common.ApiCode;
import com.aicourse.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Q-02 提交并发去重护栏测试。
 *
 * <p>覆盖四个不可省的约束：① 抢不到锁必须 409 而不是等待；② 释放必须推迟到事务结束
 * （否则第二个请求能在首个事务 commit 前读到旧计数，竞态回归）；③ 释放幂等且 compare-and-delete；
 * ④ Redis 缺失/故障时降级进程内互斥而不是放行。</p>
 */
class SubmissionSubmitGuardTest {

    private static final String KEY = "lock:submit:1:11";

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("Redis 已占锁 → 并发第二次 acquire 立即 409（不排队等待）")
    void acquire_whenAlreadyHeld_throws409() {
        SubmissionSubmitGuard guard = guardWith(redisStub());
        ValueOperations<String, String> ops = stubOps(guard);

        when(ops.setIfAbsent(eq(KEY), anyString(), any(Duration.class))).thenReturn(true, false);

        assertDoesNotThrow(() -> guard.acquire(1L, 11L).close());
        BusinessException ex = assertThrows(BusinessException.class, () -> guard.acquire(1L, 11L));
        assertEquals(ApiCode.CONFLICT, ex.getCode());
        assertTrue(ex.getMessage().contains("请勿重复提交"));
    }

    @Test
    @DisplayName("键形状为 lock:submit:{taskId}:{studentId}，不同学生互不影响")
    void acquire_keyIsPerTaskAndStudent() {
        SubmissionSubmitGuard guard = guardWith(redisStub());
        ValueOperations<String, String> ops = stubOps(guard);
        AtomicReference<String> capturedKey = new AtomicReference<>();
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(inv -> {
            capturedKey.set(inv.getArgument(0));
            return true;
        });

        guard.acquire(7L, 21L).close();
        assertEquals("lock:submit:7:21", capturedKey.get());
    }

    @Test
    @DisplayName("close 把释放推迟到事务 afterCompletion（提交前不释放，回滚也释放）")
    void close_defersReleaseUntilTransactionCompletion() {
        SubmissionSubmitGuard guard = guardWith(redisStub());
        ValueOperations<String, String> ops = stubOps(guard);
        when(ops.setIfAbsent(eq(KEY), anyString(), any(Duration.class))).thenReturn(true);
        StringRedisTemplate template = guardRedisTemplate(guard);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        guard.acquire(1L, 11L).close();
        // 关键断言：事务尚未结束，锁必须仍然持有
        verify(template, never()).execute(any(RedisScript.class), anyList(), any());

        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, syncs.size());
        syncs.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(template, times(1)).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    @DisplayName("重复 close 幂等：只释放一次（避免二次删除他人锁）")
    void close_isIdempotent() {
        SubmissionSubmitGuard guard = guardWith(redisStub());
        ValueOperations<String, String> ops = stubOps(guard);
        when(ops.setIfAbsent(eq(KEY), anyString(), any(Duration.class))).thenReturn(true);
        StringRedisTemplate template = guardRedisTemplate(guard);

        SubmissionSubmitGuard.Lease lease = guard.acquire(1L, 11L);
        lease.close();
        lease.close();
        verify(template, times(1)).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    @DisplayName("释放时 Redis 抛异常：不向业务抛出，仅依赖 TTL 自愈")
    void close_whenRedisFailsOnRelease_doesNotThrow() {
        SubmissionSubmitGuard guard = guardWith(redisStub());
        ValueOperations<String, String> ops = stubOps(guard);
        when(ops.setIfAbsent(eq(KEY), anyString(), any(Duration.class))).thenReturn(true);
        StringRedisTemplate template = guardRedisTemplate(guard);
        when(template.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertDoesNotThrow(() -> guard.acquire(1L, 11L).close());
    }

    @Test
    @DisplayName("Redis 故障（占锁即抛 DataAccessException）→ 降级进程内互斥，第二个请求仍被 409 挡住")
    void acquire_whenRedisDown_fallsBackToLocalMutex() throws Exception {
        SubmissionSubmitGuard guard = guardWith(redisStub());
        ValueOperations<String, String> ops = stubOps(guard);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new DataAccessResourceFailureException("redis down"));

        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> secondOutcome = new AtomicReference<>();
        CountDownLatch secondDone = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            try (SubmissionSubmitGuard.Lease ignored = guard.acquire(1L, 11L)) {
                held.countDown();
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        holder.start();
        assertTrue(held.await(2, TimeUnit.SECONDS));

        Thread rival = new Thread(() -> {
            try {
                guard.acquire(1L, 11L).close();
            } catch (Throwable t) {
                secondOutcome.set(t);
            } finally {
                secondDone.countDown();
            }
        });
        // 无事务同步时应立即释放；此处持有方仍在临界区，第二个必须 409
        rival.start();
        assertTrue(secondDone.await(2, TimeUnit.SECONDS), "第二个请求应在不等待的情况下立即返回");
        assertTrue(secondOutcome.get() instanceof BusinessException, "并发重复必须失败而不是放行");
        assertEquals(ApiCode.CONFLICT, ((BusinessException) secondOutcome.get()).getCode());

        release.countDown();
        holder.join(2000);
        assertFalse(holder.isAlive(), "持有方应能正常释放并退出");
    }

    @Test
    @DisplayName("进程内兜底：持有方释放后同一键可再次获得")
    void localLease_afterRelease_reacquirable() {
        SubmissionSubmitGuard guard = guardWith(redisStub());
        ValueOperations<String, String> ops = stubOps(guard);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new DataAccessResourceFailureException("redis down"));

        guard.acquire(1L, 11L).close();
        assertDoesNotThrow(() -> guard.acquire(1L, 11L).close());
    }

    @Test
    @DisplayName("缺 taskId/studentId → 400，绝不静默放行")
    void acquire_withoutIdentifiers_throws400() {
        SubmissionSubmitGuard guard = guardWith(redisStub());
        BusinessException ex = assertThrows(BusinessException.class, () -> guard.acquire(null, 11L));
        assertEquals(ApiCode.BAD_REQUEST, ex.getCode());
    }

    // ==================== 测试脚手架 ====================

    private SubmissionSubmitGuard guardWith(StringRedisTemplate template) {
        return new SubmissionSubmitGuard(template);
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> stubOps(SubmissionSubmitGuard guard) {
        StringRedisTemplate template = guardRedisTemplate(guard);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        return ops;
    }

    private StringRedisTemplate guardRedisTemplate(SubmissionSubmitGuard guard) {
        return (StringRedisTemplate) org.springframework.test.util.ReflectionTestUtils.getField(guard, "redisTemplate");
    }

    private StringRedisTemplate redisStub() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);
        return template;
    }
}
