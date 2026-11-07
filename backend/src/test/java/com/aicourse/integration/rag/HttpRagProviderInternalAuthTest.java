package com.aicourse.integration.rag;

import com.aicourse.integration.ContractPaths;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 服务间内部认证（X-Internal-Key）头部注入测试（2026-11-07 验收整改批）。
 * <p>
 * 用 JDK 内置 HttpServer 捕获真实出站请求头，验证：
 * <ul>
 *   <li>internalApiKey 非空 → 每次 Java→Python 请求都附带 {@code X-Internal-Key}（含健康检查）；</li>
 *   <li>internalApiKey 为空（兼容旧签名）→ 不附带该头（本机裸跑/既有契约测试不受影响）。</li>
 * </ul>
 */
class HttpRagProviderInternalAuthTest {

    private HttpServer server;
    private final AtomicReference<String> capturedKey = new AtomicReference<>("");

    @BeforeEach
    void startCaptureServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", (HttpExchange exchange) -> {
            capturedKey.set(exchange.getRequestHeaders().getFirst(HttpRagProvider.INTERNAL_KEY_HEADER));
            byte[] body = "{\"ok\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    @DisplayName("internalApiKey 非空 - 健康检查请求附带 X-Internal-Key")
    void sendsHeaderWhenConfigured() {
        HttpRagProvider provider = new HttpRagProvider(baseUrl(), 2, 2, 0,
                ContractPaths.RAG_HEALTH, 5, "test-internal-key");
        assertTrue(provider.healthCheck().healthy());
        assertEquals("test-internal-key", capturedKey.get());
    }

    @Test
    @DisplayName("internalApiKey 为空 - 不附带 X-Internal-Key（向后兼容）")
    void omitsHeaderWhenBlank() {
        HttpRagProvider provider = new HttpRagProvider(baseUrl(), 2, 2, 0,
                ContractPaths.RAG_HEALTH, 5, "");
        assertTrue(provider.healthCheck().healthy());
        assertNull(capturedKey.get());
    }

    @Test
    @DisplayName("兼容旧签名 - 不附带 X-Internal-Key")
    void legacySignatureStillWorks() {
        HttpRagProvider provider = new HttpRagProvider(baseUrl(), 2, 2, 0,
                ContractPaths.RAG_HEALTH, 5);
        assertTrue(provider.healthCheck().healthy());
        assertNull(capturedKey.get());
    }
}
