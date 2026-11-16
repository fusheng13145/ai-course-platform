package com.aicourse.integration.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * I-1 503 契约化：真实 HTTP 桩返回 503，验证 HttpRagProvider 的行为。
 *
 * <p>与 {@code ContractPathsTest}（纯判定单测）互补——这里走真实的
 * {@code RestClient} → 真实 socket → 真实 503，覆盖「异常类型 + cause 链 + 不重试」这段只有真跑才能验的路径。</p>
 */
class HttpRagProvider503DegradeTest {

    @Test
    void ask_degrades_without_retry_when_engine_answers_503() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        byte[] payload = "{\"code\":503,\"mode\":\"UNAVAILABLE\",\"error\":\"stub\"}"
                .getBytes(StandardCharsets.UTF_8);

        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/api/v1/rag/ask", ex -> {
            hits.incrementAndGet();
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(503, payload.length);
            ex.getResponseBody().write(payload);
            ex.close();
        });
        srv.start();
        try {
            // maxRetries=3：若 503 被误判为可重试，hits 会是 4
            HttpRagProvider provider = new HttpRagProvider(
                    "http://127.0.0.1:" + srv.getAddress().getPort(),
                    2000, 5000, 3, "/api/v1/rag/health", 5);

            RagContract.RagQueryResult result =
                    provider.ask(new RagContract.RagAskRequest("什么是 TCP？", 4178L, 5));

            assertEquals(1, hits.get(), "503 必须只打一次请求：引擎已明确宣告不可用，重试无意义");
            assertEquals("UNAVAILABLE", result.mode(), "应返回降级占位，而不是抛出异常");
        } finally {
            srv.stop(0);
        }
    }
}
