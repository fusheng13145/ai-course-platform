package com.aicourse.health;

import com.aicourse.llm.LLMProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthIndicatorCoverageTest {

    // ---------- DatabaseHealthIndicator ----------
    @Mock
    private DataSource dataSource;

    @Test
    void database_up() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery("SELECT 1")).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt(1)).thenReturn(1);

        Health health = new DatabaseHealthIndicator(dataSource).health();
        assertEquals(org.springframework.boot.actuate.health.Status.UP, health.getStatus());
        assertEquals("MySQL", health.getDetails().get("database"));
    }

    @Test
    void database_down_noRows() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery("SELECT 1")).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        Health health = new DatabaseHealthIndicator(dataSource).health();
        assertEquals(org.springframework.boot.actuate.health.Status.DOWN, health.getStatus());
    }

    @Test
    void database_down_exception() throws Exception {
        when(dataSource.getConnection()).thenThrow(new RuntimeException("cannot connect"));

        Health health = new DatabaseHealthIndicator(dataSource).health();
        assertEquals(org.springframework.boot.actuate.health.Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails().get("error"));
    }

    // ---------- RedisHealthIndicator ----------
    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    void redis_up() {
        RedisConnection conn = mock(RedisConnection.class);
        when(redisConnectionFactory.getConnection()).thenReturn(conn);
        when(conn.ping()).thenReturn("PONG");

        Health health = new RedisHealthIndicator(redisConnectionFactory).health();
        assertEquals(org.springframework.boot.actuate.health.Status.UP, health.getStatus());
    }

    @Test
    void redis_down_emptyPing() {
        RedisConnection conn = mock(RedisConnection.class);
        when(redisConnectionFactory.getConnection()).thenReturn(conn);
        when(conn.ping()).thenReturn("");

        Health health = new RedisHealthIndicator(redisConnectionFactory).health();
        assertEquals(org.springframework.boot.actuate.health.Status.DOWN, health.getStatus());
    }

    @Test
    void redis_down_exception() {
        when(redisConnectionFactory.getConnection()).thenThrow(new RuntimeException("redis down"));

        Health health = new RedisHealthIndicator(redisConnectionFactory).health();
        assertEquals(org.springframework.boot.actuate.health.Status.DOWN, health.getStatus());
    }

    // ---------- LlmProviderHealthIndicator ----------
    @Mock
    private LLMProvider llmProvider;

    @Test
    void llm_up_whenConfigured() {
        when(llmProvider.name()).thenReturn("openai");
        when(llmProvider.isConfigured()).thenReturn(true);

        Health health = new LlmProviderHealthIndicator(llmProvider).health();
        assertEquals(org.springframework.boot.actuate.health.Status.UP, health.getStatus());
        assertEquals(Boolean.TRUE, health.getDetails().get("configured"));
    }

    @Test
    void llm_down_whenNotConfigured() {
        when(llmProvider.name()).thenReturn("openai");
        when(llmProvider.isConfigured()).thenReturn(false);

        Health health = new LlmProviderHealthIndicator(llmProvider).health();
        assertEquals(org.springframework.boot.actuate.health.Status.DOWN, health.getStatus());
        assertEquals(Boolean.FALSE, health.getDetails().get("configured"));
    }

    // ---------- PythonServicesHealthIndicator ----------
    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/py/health", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private PythonServicesHealthIndicator build(boolean kg, boolean rag, boolean ft, String kgUrl, String ragUrl, String ftUrl) {
        return new PythonServicesHealthIndicator(
                kg, kgUrl, "/py/health",
                rag, ragUrl, "/py/health",
                ft, ftUrl, "/py/health");
    }

    @Test
    void python_allDisabled_isUp() {
        PythonServicesHealthIndicator ind = build(false, false, false, "", "", "");
        Health health = ind.health();
        assertEquals(org.springframework.boot.actuate.health.Status.UP, health.getStatus());
        assertTrue(health.getDetails().containsKey("knowledge-graph"));
    }

    @Test
    void python_kgUp_isUp() {
        PythonServicesHealthIndicator ind = build(true, false, false,
                "http://localhost:" + port, "", "");
        Health health = ind.health();
        assertEquals(org.springframework.boot.actuate.health.Status.UP, health.getStatus());
    }

    @Test
    void python_kgDown_isDown() {
        PythonServicesHealthIndicator ind = build(true, false, false,
                "http://localhost:" + (port + 1000), "", "");
        Health health = ind.health();
        assertEquals(org.springframework.boot.actuate.health.Status.DOWN, health.getStatus());
    }

    @Test
    void python_mixed_kgUpRagDown_isDown() {
        PythonServicesHealthIndicator ind = build(true, true, false,
                "http://localhost:" + port, "http://localhost:" + (port + 2000), "");
        Health health = ind.health();
        assertEquals(org.springframework.boot.actuate.health.Status.DOWN, health.getStatus());
    }
}
