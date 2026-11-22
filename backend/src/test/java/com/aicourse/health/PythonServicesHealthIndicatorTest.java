package com.aicourse.health;

import com.aicourse.integration.ContractPaths;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link PythonServicesHealthIndicator} 直测：enabled 服务 UP/DOWN 的聚合判定与
 * deepgraph-rag 探针分支（未启用不参与判定，启用且 DOWN 才拉低整体）。
 */
class PythonServicesHealthIndicatorTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(ContractPaths.DEEPGRAPH_HEALTH, ex -> respond(ex, 200, "{}"));
        server.start();
    }

    private void respond(com.sun.net.httpserver.HttpExchange ex, int code, String body) throws IOException {
        ex.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private PythonServicesHealthIndicator indicator(boolean deepgraphEnabled, String baseUrl) {
        // kg/rag/ft 全关，只有 deepgraph 参与——聚焦新探针分支
        return new PythonServicesHealthIndicator(
                false, "http://127.0.0.1:1", ContractPaths.KG_HEALTH,
                false, "http://127.0.0.1:1", ContractPaths.RAG_HEALTH,
                false, "http://127.0.0.1:1", ContractPaths.FT_HEALTH,
                deepgraphEnabled, baseUrl, ContractPaths.DEEPGRAPH_HEALTH);
    }

    @Test
    @DisplayName("deepgraph 未启用：不参与健康判定，整体 UP")
    void deepgraphDisabled_notChecked_overallUp() {
        Health health = indicator(false, "http://127.0.0.1:1").health();
        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    @DisplayName("deepgraph 已启用且 UP：整体 UP")
    void deepgraphEnabled_up_overallUp() throws IOException {
        startStub();
        Health health = indicator(true,
                "http://127.0.0.1:" + server.getAddress().getPort()).health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals("UP", ((java.util.Map<?, ?>) health.getDetails().get("deepgraph-rag")).get("status"));
    }

    @Test
    @DisplayName("deepgraph 已启用但 DOWN：整体 DOWN（与 kg/rag/ft 同口径）")
    void deepgraphEnabled_down_overallDown() {
        Health health = indicator(true, "http://127.0.0.1:1").health();
        assertEquals(Status.DOWN, health.getStatus());
    }
}
