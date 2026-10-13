package com.aicourse.service.component.sys;

import com.aicourse.common.JsonUtils;
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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统监控协作者：健康检查（DB/Redis）、AI 能力使用统计、AI 异常监控（日志聚合 + LLM 检测）。
 * <p>由 {@code SystemServiceImpl} 门面委托调用。</p>
 */
@Component
public class SysMonitorComponent {

    private static final Logger log = LoggerFactory.getLogger(SysMonitorComponent.class);

    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern(DATETIME_PATTERN);

    private final UserMapper userMapper;
    private final CourseMapper courseMapper;
    private final QuestionMapper questionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final LoginLogMapper loginLogMapper;
    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;
    private final String deepseekApiKey;
    private final LoginLogService loginLogService;
    private final OperationLogService operationLogService;
    private final AIService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SysMonitorComponent(UserMapper userMapper,
                               CourseMapper courseMapper,
                               QuestionMapper questionMapper,
                               KnowledgePointMapper knowledgePointMapper,
                               LoginLogMapper loginLogMapper,
                               DataSource dataSource,
                               RedisTemplate<String, Object> redisTemplate,
                               @Value("${ai.deepseek.api-key:}") String deepseekApiKey,
                               LoginLogService loginLogService,
                               OperationLogService operationLogService,
                               AIService aiService) {
        this.userMapper = userMapper;
        this.courseMapper = courseMapper;
        this.questionMapper = questionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.loginLogMapper = loginLogMapper;
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.deepseekApiKey = deepseekApiKey;
        this.loginLogService = loginLogService;
        this.operationLogService = operationLogService;
        this.aiService = aiService;
    }

    /** 健康检查：数据库与 Redis 连接状态 */
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>(4);
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().format(DATETIME_FORMATTER));

        // 检查数据库连接
        String dbStatus = "DISCONNECTED";
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(3)) {
                dbStatus = "CONNECTED";
            }
        } catch (Exception e) {
            log.warn("数据库健康检查失败: {}", e.getMessage());
        }
        health.put("database", dbStatus);

        // 检查Redis连接
        String redisStatus = "DISCONNECTED";
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            redisStatus = "CONNECTED";
        } catch (Exception e) {
            log.warn("Redis健康检查失败: {}", e.getMessage());
        }
        health.put("redis", redisStatus);

        return health;
    }

    /**
     * AI 能力使用统计。
     *
     * <p><b>2026-10-13 口径修正（如实记录）</b>：本方法此前返回的 {@code totalCalls} 是
     * {@code todayLogins * 3}、{@code costEstimate} 是 {@code todayLogins * 0.02}、
     * {@code avgResponseTime} 是常量 1200——全是"估算"，注释亦自承，属编造数字。
     * 既往因前端零消费而未在演示中露馅，但任何新 AI 统计页都不允许误接该端点，
     * 故<b>直接删去这三个无留痕的估算字段</b>，改为显式声明"调用量/成本需按次留痕"。
     * 真实的逐次调用统计请走 {@code GET /admin/statistics/ai-usage/by-course}
     * （数据源 {@code ai_call_logs}）。</p>
     *
     * <p>保留的字段都是真实可核的：用户/课程/题目/知识点计数、AI 是否为已配置状态。</p>
     */
    public Map<String, Object> getAIUsageStats() {
        Map<String, Object> stats = new HashMap<>();

        // 平台AI能力指标
        long totalUsers = userMapper.selectCount(null);
        long totalCourses = courseMapper.selectCount(null);
        long totalQuestions = questionMapper.selectCount(null);
        long totalKnowledgePoints = knowledgePointMapper.selectCount(null);

        // AI服务状态
        boolean aiConfigured = deepseekApiKey != null && !deepseekApiKey.isBlank();

        // 如实声明：调用量/成本不在本端点采集（避免后续有人把估算值当真值接走）
        stats.put("collectedSince", "2026-10-13");
        stats.put("collectionNote",
                "调用次数/Token/耗时自 2026-10-13 起按次留痕，请见 /admin/statistics/ai-usage/by-course；"
                        + "本端点不再给出任何估算值。");
        stats.put("apiConfigured", aiConfigured);
        stats.put("modelReady", aiConfigured);
        stats.put("knowledgeBaseSize", totalQuestions + totalKnowledgePoints);
        stats.put("supportedCourses", totalCourses);
        stats.put("totalUsers", totalUsers);
        stats.put("status", aiConfigured ? "active" : "api_key_missing");

        return stats;
    }

    /**
     * AI 异常监控：汇总最近登录/操作日志，调用 LLM 检测异常（暴力破解、成绩异动等）。
     * 失败时返回包含降级提示的结果，不抛异常。
     */
    public Map<String, Object> aiMonitor() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<LoginLog> loginLogs = loginLogService.getRecentLogs(50);
            List<OperationLog> operationLogs = operationLogService.list(
                    new LambdaQueryWrapper<OperationLog>()
                            .orderByDesc(OperationLog::getCreatedAt)
                            .last("LIMIT 50"));

            List<Map<String, Object>> loginView = loginLogs.stream().map(l -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("username", l.getUsername());
                m.put("status", l.getLoginStatus());
                m.put("ip", l.getIpAddress());
                m.put("failureReason", l.getFailureReason());
                m.put("time", l.getCreatedAt());
                return m;
            }).collect(Collectors.toList());

            List<Map<String, Object>> opView = operationLogs.stream().map(o -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("username", o.getUsername());
                m.put("module", o.getModule());
                m.put("action", o.getAction());
                m.put("target", o.getTarget());
                m.put("status", o.getStatus());
                m.put("errorMsg", o.getErrorMsg());
                m.put("time", o.getCreatedAt());
                return m;
            }).collect(Collectors.toList());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("loginLogs", loginView);
            payload.put("operationLogs", opView);
            String logsJson = objectMapper.writeValueAsString(payload);

            String aiRaw = aiService.analyzeAnomaliesJson(logsJson);
            String raw = JsonUtils.extractJsonObject(aiRaw);
            List<Map<String, Object>> alerts = new ArrayList<>();
            if (raw != null) {
                JsonNode root = objectMapper.readTree(raw);
                if (root.has("alerts") && root.get("alerts").isArray()) {
                    for (JsonNode a : root.get("alerts")) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        String lvl = a.has("level") ? a.get("level").asText().toUpperCase() : "MEDIUM";
                        if (!"HIGH".equals(lvl) && !"MEDIUM".equals(lvl) && !"LOW".equals(lvl)) lvl = "MEDIUM";
                        m.put("level", lvl);
                        m.put("title", a.has("title") ? a.get("title").asText() : "");
                        m.put("detail", a.has("detail") ? a.get("detail").asText() : "");
                        alerts.add(m);
                    }
                }
            } else {
                result.put("parseError", "AI 未返回有效洞察");
            }

            result.put("alerts", alerts);
            result.put("loginCount", loginView.size());
            result.put("operationCount", opView.size());
            result.put("generatedAt", new Date().toString());
        } catch (Exception e) {
            log.warn("AI 监控分析失败: {}", e.getMessage());
            result.put("alerts", new ArrayList<>());
            result.put("parseError", "AI 分析暂时不可用，请稍后重试");
            result.put("loginCount", 0);
            result.put("operationCount", 0);
        }
        return result;
    }
}
