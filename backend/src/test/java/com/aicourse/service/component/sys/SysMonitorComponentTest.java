package com.aicourse.service.component.sys;

import com.aicourse.entity.LoginLog;
import com.aicourse.entity.OperationLog;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.KnowledgePointMapper;
import com.aicourse.mapper.LoginLogMapper;
import com.aicourse.mapper.QuestionMapper;
import com.aicourse.mapper.UserMapper;
import com.aicourse.service.AIService;
import com.aicourse.service.LoginLogService;
import com.aicourse.service.OperationLogService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SysMonitorComponent 单元测试（纯 Mockito）。
 * <p>覆盖健康检查（DB/Redis 连通与断开）、AI 使用统计（Key 配置与否）、AI 异常监控（解析/降级/归一化）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysMonitorComponentTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private CourseMapper courseMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private KnowledgePointMapper knowledgePointMapper;
    @Mock
    private LoginLogMapper loginLogMapper;
    @Mock
    private DataSource dataSource;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private LoginLogService loginLogService;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private AIService aiService;

    private SysMonitorComponent component;

    @BeforeEach
    void setUp() {
        component = new SysMonitorComponent(userMapper, courseMapper, questionMapper,
                knowledgePointMapper, loginLogMapper, dataSource, redisTemplate,
                "sk-test-key", loginLogService, operationLogService, aiService);
    }

    // ===================== getHealth =====================
    @Test
    @DisplayName("getHealth 数据库与 Redis 均连通")
    void getHealth_up() throws Exception {
        Connection conn = mock(Connection.class);
        when(conn.isValid(3)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(conn);
        RedisConnectionFactory cf = mock(RedisConnectionFactory.class);
        RedisConnection rc = mock(RedisConnection.class);
        when(rc.ping()).thenReturn("PONG");
        when(cf.getConnection()).thenReturn(rc);
        when(redisTemplate.getConnectionFactory()).thenReturn(cf);

        Map<String, Object> h = component.getHealth();
        assertEquals("UP", h.get("status"));
        assertEquals("CONNECTED", h.get("database"));
        assertEquals("CONNECTED", h.get("redis"));
    }

    @Test
    @DisplayName("getHealth 数据库与 Redis 均断开")
    void getHealth_down() throws Exception {
        Connection conn = mock(Connection.class);
        when(conn.isValid(3)).thenReturn(false);
        when(dataSource.getConnection()).thenReturn(conn);
        RedisConnectionFactory cf = mock(RedisConnectionFactory.class);
        when(cf.getConnection()).thenThrow(new RuntimeException("no redis"));
        when(redisTemplate.getConnectionFactory()).thenReturn(cf);

        Map<String, Object> h = component.getHealth();
        assertEquals("DISCONNECTED", h.get("database"));
        assertEquals("DISCONNECTED", h.get("redis"));
    }

    @Test
    @DisplayName("getHealth Redis 工厂为空降级 DISCONNECTED")
    void getHealth_redisDown() throws Exception {
        Connection conn = mock(Connection.class);
        when(conn.isValid(3)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(conn);
        when(redisTemplate.getConnectionFactory()).thenReturn(null);

        Map<String, Object> h = component.getHealth();
        assertEquals("DISCONNECTED", h.get("redis"));
    }

    // ===================== getAIUsageStats =====================
    @Test
    @DisplayName("getAIUsageStats aiConfigured=true")
    void getAIUsageStats_configured() {
        when(userMapper.selectCount(null)).thenReturn(10L);
        when(courseMapper.selectCount(null)).thenReturn(5L);
        when(questionMapper.selectCount(null)).thenReturn(100L);
        when(knowledgePointMapper.selectCount(null)).thenReturn(20L);
        when(loginLogMapper.selectCount(any())).thenReturn(3L);

        Map<String, Object> r = component.getAIUsageStats();
        assertEquals(10L, r.get("totalUsers"));
        assertEquals(true, r.get("apiConfigured"));
        assertEquals(120L, r.get("knowledgeBaseSize"));
        assertEquals("active", r.get("status"));
    }

    @Test
    @DisplayName("getAIUsageStats aiConfigured=false")
    void getAIUsageStats_notConfigured() {
        SysMonitorComponent noKey = new SysMonitorComponent(userMapper, courseMapper, questionMapper,
                knowledgePointMapper, loginLogMapper, dataSource, redisTemplate,
                "", loginLogService, operationLogService, aiService);
        when(userMapper.selectCount(null)).thenReturn(1L);
        when(courseMapper.selectCount(null)).thenReturn(1L);
        when(questionMapper.selectCount(null)).thenReturn(1L);
        when(knowledgePointMapper.selectCount(null)).thenReturn(1L);
        when(loginLogMapper.selectCount(any())).thenReturn(0L);

        Map<String, Object> r = noKey.getAIUsageStats();
        assertEquals(false, r.get("apiConfigured"));
        assertEquals(0, r.get("avgResponseTime"));
        assertEquals("api_key_missing", r.get("status"));
    }

    // ===================== aiMonitor =====================
    @Test
    @DisplayName("aiMonitor 正常解析告警（含级别归一化）")
    void aiMonitor_ok() {
        LoginLog ll = new LoginLog();
        ll.setUsername("u");
        ll.setLoginStatus("SUCCESS");
        ll.setIpAddress("1.1.1.1");
        ll.setFailureReason("");
        OperationLog ol = new OperationLog();
        ol.setUsername("u");
        ol.setModule("m");
        ol.setAction("a");
        ol.setTarget("t");
        ol.setStatus("OK");
        ol.setErrorMsg("");
        when(loginLogService.getRecentLogs(50)).thenReturn(List.of(ll));
        when(operationLogService.list(any(Wrapper.class))).thenReturn(List.of(ol));
        when(aiService.analyzeAnomaliesJson(anyString())).thenReturn(
                "{\"alerts\":[{\"level\":\"high\",\"title\":\"t\",\"detail\":\"d\"},"
                        + "{\"level\":\"UNKNOWN\",\"title\":\"t2\",\"detail\":\"d2\"}]}");

        Map<String, Object> r = component.aiMonitor();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> alerts = (List<Map<String, Object>>) r.get("alerts");
        assertEquals(2, alerts.size());
        assertEquals("HIGH", alerts.get(0).get("level"));
        assertEquals("MEDIUM", alerts.get(1).get("level")); // UNKNOWN -> MEDIUM
    }

    @Test
    @DisplayName("aiMonitor AI 返回 null 记 parseError")
    void aiMonitor_parseError() {
        when(loginLogService.getRecentLogs(50)).thenReturn(List.of());
        when(operationLogService.list(any(Wrapper.class))).thenReturn(List.of());
        when(aiService.analyzeAnomaliesJson(anyString())).thenReturn(null);

        Map<String, Object> r = component.aiMonitor();
        assertNotNull(r.get("parseError"));
    }

    @Test
    @DisplayName("aiMonitor 日志读取异常降级")
    void aiMonitor_exception() {
        when(loginLogService.getRecentLogs(50)).thenThrow(new RuntimeException("boom"));

        Map<String, Object> r = component.aiMonitor();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> alerts = (List<Map<String, Object>>) r.get("alerts");
        assertTrue(alerts.isEmpty());
        assertEquals("AI 分析暂时不可用，请稍后重试", r.get("parseError"));
        assertEquals(0, r.get("loginCount"));
    }
}
