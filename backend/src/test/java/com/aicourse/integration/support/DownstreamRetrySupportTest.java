package com.aicourse.integration.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 下游重试策略的契约测试（阶段3 · DUP-10~13 收敛后的行为基线）。
 *
 * <p>这四条判定决定「要不要重试」。它们从三个 HTTP 客户端各自的私有方法收敛到这里后，
 * 任何一条被改宽（例如 500 也当 503 处理）都会让三个端点同时放大故障，所以必须有测试钉住。</p>
 */
class DownstreamRetrySupportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ObjectNode json(String text) throws Exception {
        return (ObjectNode) MAPPER.readTree(text);
    }

    @Test
    @DisplayName("isExplicitFailure：只有 success 为布尔 false 才算失败")
    void explicitFailure_onlyWhenSuccessIsBooleanFalse() throws Exception {
        assertTrue(DownstreamRetrySupport.isExplicitFailure(json("{\"success\":false}")));
        assertFalse(DownstreamRetrySupport.isExplicitFailure(json("{\"success\":true}")));
        // 缺省 success 的历史响应保持兼容：不能当成失败
        assertFalse(DownstreamRetrySupport.isExplicitFailure(json("{\"data\":1}")));
        // success 是字符串 "false" 时不算显式失败（避免把类型错误当成业务失败）
        assertFalse(DownstreamRetrySupport.isExplicitFailure(json("{\"success\":\"false\"}")));
        assertFalse(DownstreamRetrySupport.isExplicitFailure(null));
    }

    @Test
    @DisplayName("isReadTimeout：顶层/被包装的 SocketTimeout 与 HttpTimeout 都识别")
    void readTimeout_recognisesWrappedCauses() {
        assertTrue(DownstreamRetrySupport.isReadTimeout(new SocketTimeoutException("read")));
        assertTrue(DownstreamRetrySupport.isReadTimeout(new RuntimeException(new SocketTimeoutException("wrapped"))));
        assertTrue(DownstreamRetrySupport.isReadTimeout(new IllegalStateException(
                new RuntimeException(new HttpTimeoutException("http timeout")))));
        assertFalse(DownstreamRetrySupport.isReadTimeout(new RuntimeException("other")));
        assertFalse(DownstreamRetrySupport.isReadTimeout(null));
    }

    @Test
    @DisplayName("isConnectFailure：连接被拒（含包装）识别，其他异常不识别")
    void connectFailure_recognisesWrappedCauses() {
        assertTrue(DownstreamRetrySupport.isConnectFailure(new ConnectException("refused")));
        assertTrue(DownstreamRetrySupport.isConnectFailure(
                new RuntimeException(new ConnectException("refused"))));
        assertFalse(DownstreamRetrySupport.isConnectFailure(new SocketTimeoutException("read")));
        assertFalse(DownstreamRetrySupport.isConnectFailure(null));
    }

    @Test
    @DisplayName("isServiceUnavailable：只有 503 算不可用，500 仍走重试")
    void serviceUnavailable_onlyFor503() {
        assertTrue(DownstreamRetrySupport.isServiceUnavailable(
                HttpServerErrorException.create(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "unavailable", null, null, null)));
        assertTrue(DownstreamRetrySupport.isServiceUnavailable(new RuntimeException(
                HttpServerErrorException.create(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "unavailable", null, null, null))));
        assertFalse(DownstreamRetrySupport.isServiceUnavailable(
                HttpServerErrorException.create(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                        "boom", null, null, null)),
                "500 是可恢复失败，必须重试；只有 503 直接降级");
        assertFalse(DownstreamRetrySupport.isServiceUnavailable(null));
    }

    @Test
    @DisplayName("sleep 被中断时立即返回并恢复中断标记（不吞掉中断、不阻塞满时长）")
    void sleep_restoresInterruptFlag() throws Exception {
        AtomicBoolean interruptedAfter = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            Thread.currentThread().interrupt();
            DownstreamRetrySupport.sleep(3);
            interruptedAfter.set(Thread.currentThread().isInterrupted());
            done.countDown();
        });
        worker.start();

        assertTrue(done.await(2, TimeUnit.SECONDS), "被中断的 sleep 必须立即返回，不能睡满 3 秒");
        assertTrue(interruptedAfter.get(), "中断标记必须被恢复，否则上层拿不到中断信号");
        worker.join(2000);
        assertEquals(false, worker.isAlive());
    }
}
