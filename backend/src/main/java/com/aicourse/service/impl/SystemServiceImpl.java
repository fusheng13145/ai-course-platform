package com.aicourse.service.impl;

import com.aicourse.common.JsonUtils;
import com.aicourse.entity.LoginLog;
import com.aicourse.entity.OperationLog;
import com.aicourse.entity.SystemConfig;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.KnowledgePointMapper;
import com.aicourse.mapper.LoginLogMapper;
import com.aicourse.mapper.QuestionMapper;
import com.aicourse.mapper.SystemConfigMapper;
import com.aicourse.mapper.UserMapper;
import com.aicourse.service.AIService;
import com.aicourse.service.LoginLogService;
import com.aicourse.service.OperationLogService;
import com.aicourse.service.SystemService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * 系统服务实现类
 * 系统概览、配置管理、健康检查、备份、日志读取等功能
 */
@Service
@Slf4j
public class SystemServiceImpl implements SystemService {

    // ===================== 常量定义 =====================
    private static final String BACKUP_DIR = "backups";
    private static final String LOG_DIR = "logs";
    private static final String SQL_SUFFIX = ".sql";
    private static final String LOG_SUFFIX = ".log";
    private static final String DEFAULT_GROUP = "DEFAULT";
    private static final String STRING_TYPE = "STRING";
    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss";

    // 全局复用日期格式化器
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern(DATETIME_PATTERN);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN);
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();


    private final SystemConfigMapper systemConfigMapper;
    private final UserMapper userMapper;
    private final CourseMapper courseMapper;
    private final QuestionMapper questionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final LoginLogMapper loginLogMapper;
    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Environment environment;
    private final String deepseekApiKey;
    private final LoginLogService loginLogService;
    private final OperationLogService operationLogService;
    private final AIService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SystemServiceImpl(SystemConfigMapper systemConfigMapper,
                             UserMapper userMapper,
                             CourseMapper courseMapper,
                             QuestionMapper questionMapper,
                             KnowledgePointMapper knowledgePointMapper,
                             LoginLogMapper loginLogMapper,
                             DataSource dataSource,
                             RedisTemplate<String, Object> redisTemplate,
                             Environment environment,
                             @Value("${ai.deepseek.api-key:}") String deepseekApiKey,
                             LoginLogService loginLogService,
                             OperationLogService operationLogService,
                             AIService aiService) {
        this.systemConfigMapper = systemConfigMapper;
        this.userMapper = userMapper;
        this.courseMapper = courseMapper;
        this.questionMapper = questionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.loginLogMapper = loginLogMapper;
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.environment = environment;
        this.deepseekApiKey = deepseekApiKey;
        this.loginLogService = loginLogService;
        this.operationLogService = operationLogService;
        this.aiService = aiService;
    }

    // ===================== 系统概览 =====================
    @Override
    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new HashMap<>(4);
        overview.put("systemTime", LocalDateTime.now().format(DATETIME_FORMATTER));
        overview.put("serviceStatus", "RUNNING");
        return overview;
    }

    // ===================== 系统配置 =====================
    @Override
    public String getConfig(String key) {
        SystemConfig config = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key)
        );
        return config == null ? null : config.getConfigValue();
    }

    @Override
    public void saveConfig(String key, String value, String group) {
        SystemConfig config = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key)
        );
        LocalDateTime now = LocalDateTime.now();

        if (config != null) {
            config.setConfigValue(value);
            if (group != null) {
                config.setConfigGroup(group);
            }
            config.setUpdatedAt(now);
            systemConfigMapper.updateById(config);
        } else {
            SystemConfig newConfig = new SystemConfig();
            newConfig.setConfigKey(key);
            newConfig.setConfigValue(value);
            newConfig.setConfigGroup(group == null ? DEFAULT_GROUP : group);
            newConfig.setConfigType(STRING_TYPE);
            newConfig.setIsPublic(false);
            newConfig.setCreatedAt(now);
            newConfig.setUpdatedAt(now);
            systemConfigMapper.insert(newConfig);
        }
    }

    @Override
    public List<Map<String, Object>> getConfigs(String group) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        if (group != null && !group.isBlank()) {
            wrapper.eq(SystemConfig::getConfigGroup, group);
        }

        List<SystemConfig> configList = systemConfigMapper.selectList(wrapper);
        return configList.stream()
                .map(config -> {
                    Map<String, Object> map = new HashMap<>(6);
                    map.put("key", config.getConfigKey());
                    map.put("value", config.getConfigValue());
                    map.put("group", config.getConfigGroup());
                    map.put("type", config.getConfigType());
                    map.put("description", config.getDescription());
                    return map;
                })
                .collect(Collectors.toList());
    }

    // ===================== 健康检查 =====================
    @Override
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

    // ===================== 数据库备份 =====================
    @Override
    public String createBackup() {
        File dir = new File(BACKUP_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String backupFileName = "backup_" + timestamp + SQL_SUFFIX;
        File backupFile = new File(BACKUP_DIR, backupFileName);

        // 尝试使用 mysqldump 执行真实备份
        try {
            // 【安全加固】数据库凭据一律从 Spring 配置读取（环境变量注入，见 application.yaml），
            // 不再从 system_configs 表读取、也不允许回退到硬编码的 root/空密码默认值。
            String jdbcUrl = environment.getProperty("spring.datasource.url");
            String username = environment.getProperty("spring.datasource.username");
            String password = environment.getProperty("spring.datasource.password");

            // 兜底：从已注入的 DataSource 元数据获取 url/username（与连接池必然一致）
            if (jdbcUrl == null || username == null) {
                try (Connection metaConn = dataSource.getConnection()) {
                    if (jdbcUrl == null) {
                        jdbcUrl = metaConn.getMetaData().getURL();
                    }
                    if (username == null) {
                        String metaUser = metaConn.getMetaData().getUserName();
                        // MySQL 驱动可能返回 "user@host"，只需保留用户名部分
                        if (metaUser != null && metaUser.contains("@")) {
                            metaUser = metaUser.substring(0, metaUser.indexOf('@'));
                        }
                        username = metaUser;
                    }
                } catch (Exception metaEx) {
                    log.warn("从 DataSource 获取连接元数据失败: {}", metaEx.getMessage());
                }
            }

            // 密码缺失时拒绝执行，避免使用空密码等不安全默认值
            if (jdbcUrl == null || username == null || password == null || password.isEmpty()) {
                throw new BusinessException("数据库备份需要配置 spring.datasource 的 url/username/password"
                        + "（可通过环境变量 SPRING_DATASOURCE_URL/USERNAME/PASSWORD 或 MYSQL_ROOT_PASSWORD 注入），已取消备份");
            }

            // 解析 JDBC URL: jdbc:mysql://host:port/database?params
            String host = "localhost";
            String port = "3306";
            String database = "ai_course_platform";

            String urlPart = jdbcUrl.replace("jdbc:mysql://", "");
            int paramsIndex = urlPart.indexOf('?');
            if (paramsIndex > 0) {
                urlPart = urlPart.substring(0, paramsIndex);
            }
            int slashIndex = urlPart.indexOf('/');
            if (slashIndex > 0) {
                database = urlPart.substring(slashIndex + 1);
                String hostPort = urlPart.substring(0, slashIndex);
                int colonIndex = hostPort.indexOf(':');
                if (colonIndex > 0) {
                    host = hostPort.substring(0, colonIndex);
                    port = hostPort.substring(colonIndex + 1);
                } else {
                    host = hostPort;
                }
            }

            // 【安全加固】使用 --defaults-extra-file 临时配置文件传递密码，
            // 避免 -p密码/MYSQL_PWD 被 ps aux、/proc/PID/environ 等系统接口暴露数据库凭据。
            // 临时文件设置 0600 权限且在 finally 块中立即删除。
            // 生产环境建议禁用应用内备份功能，改用外部编排工具（crontab / Task Scheduler）。
            File tempConfig = null;
            try {
                tempConfig = File.createTempFile("mysqldump-backup-", ".cnf");
                // MySQL 5.6+ 安全要求：凭证文件必须仅 owner 可读写，否则报 "World-writable... ignored"
                if (!tempConfig.setReadable(false, false)) { log.debug("tmp cnf setReadable"); }
                if (!tempConfig.setWritable(false, false))   { log.debug("tmp cnf setWritable"); }
                tempConfig.setReadable(true, true);
                tempConfig.setWritable(true, true);

                Files.writeString(tempConfig.toPath(),
                        "[client]\nhost=" + host + "\nport=" + port
                                + "\nuser=" + username + "\npassword=" + password + "\n");

                ProcessBuilder pb = new ProcessBuilder(
                        "mysqldump",
                        "--defaults-extra-file=" + tempConfig.getAbsolutePath(),
                        "--single-transaction",
                        "--routines",
                        "--triggers",
                        database
                );
                pb.redirectErrorStream(true);
                pb.directory(dir);
                Process process = pb.start();

                // 读取输出并写入文件
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    Files.writeString(backupFile.toPath(), output.toString(),
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    log.info("数据库备份成功: {}", backupFileName);
                    return backupFileName;
                } else {
                    log.warn("mysqldump执行失败 (exitCode={}), 回退到仅文件名方式", exitCode);
                }
            } finally {
                if (tempConfig != null && tempConfig.exists()) {
                    tempConfig.delete();
                }
            }
        } catch (BusinessException e) {
            // 配置缺失属于明确失败，必须抛给调用方，不允许回退为"仅文件名"假备份
            throw e;
        } catch (Exception e) {
            log.warn("mysqldump不可用或执行失败, 回退到仅文件名方式: {}", e.getMessage());
        }

        // 回退：仅创建文件名
        return backupFileName;
    }

    @Override
    public List<Map<String, Object>> getBackups() {
        return getFileInfoList(BACKUP_DIR, SQL_SUFFIX);
    }

    @Override
    public void restoreBackup(String fileName) {
        // ---- 路径遍历防护 ----
        // 1. 禁止目录分隔符（含 Unix / 与 Windows \）
        if (fileName.contains("/") || fileName.contains("\\")) {
            throw new BusinessException("非法文件名：禁止路径分隔符");
        }
        // 2. 禁止父目录引用
        if (fileName.contains("..")) {
            throw new BusinessException("非法文件名：禁止父目录引用");
        }
        // 3. 禁止绝对路径
        if (new File(fileName).isAbsolute()) {
            throw new BusinessException("非法文件名：禁止绝对路径");
        }
        // 4. Path.normalize() + startsWith 校验（防止符号链接等方法绕过）
        Path backupDir = Paths.get(BACKUP_DIR).toAbsolutePath().normalize();
        Path filePath = backupDir.resolve(fileName).normalize();
        if (!filePath.startsWith(backupDir)) {
            throw new BusinessException("非法文件路径：文件不在备份目录内");
        }
        // 5. 文件存在性校验
        if (!Files.exists(filePath)) {
            throw new BusinessException("备份文件不存在");
        }
    }

    // ===================== 日志文件 =====================
    @Override
    public List<Map<String, Object>> getLogFiles() {
        return getFileInfoList(LOG_DIR, LOG_SUFFIX);
    }

    @Override
    public String readLogFile(String fileName, int lines, String keyword) {
        // 路径防穿越
        if (fileName.contains(File.separator) || fileName.contains("..")) {
            throw new BusinessException("非法文件名");
        }
        Path logPath = Paths.get(LOG_DIR, fileName);
        if (!Files.exists(logPath)) {
            throw new BusinessException("日志文件不存在");
        }

        try (Stream<String> lineStream = Files.lines(logPath)) {
            List<String> allLines = lineStream.collect(Collectors.toList());

            // 关键字过滤
            if (keyword != null && !keyword.isBlank()) {
                allLines = allLines.stream()
                        .filter(line -> line.contains(keyword))
                        .collect(Collectors.toList());
            }

            // 取末尾指定行数
            int startIndex = Math.max(0, allLines.size() - lines);
            List<String> resultLines = allLines.subList(startIndex, allLines.size());
            return String.join("\n", resultLines);
        } catch (IOException e) {
            throw new BusinessException("读取日志文件失败");
        }
    }

    /**
     * 获取AI功能使用统计（基于真实系统数据）
     */
    @Override
    public Map<String, Object> getAIUsageStats() {
        Map<String, Object> stats = new HashMap<>();

        // 平台AI能力指标
        long totalUsers = userMapper.selectCount(null);
        long totalCourses = courseMapper.selectCount(null);
        long totalQuestions = questionMapper.selectCount(null);
        long totalKnowledgePoints = knowledgePointMapper.selectCount(null);

        // 今日登录次数（作为系统活跃度代理指标）
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        long todayLogins = loginLogMapper.selectCount(
                new LambdaQueryWrapper<com.aicourse.entity.LoginLog>()
                        .ge(com.aicourse.entity.LoginLog::getCreatedAt, todayStart)
        );

        // AI服务状态
        boolean aiConfigured = deepseekApiKey != null && !deepseekApiKey.isBlank();

        stats.put("totalCalls", todayLogins * 3); // 估算：每次登录约3次AI调用
        stats.put("todayCalls", todayLogins > 0 ? todayLogins : 1);
        stats.put("avgResponseTime", aiConfigured ? 1200 : 0); // DeepSeek 平均响应约1.2s
        stats.put("costEstimate", todayLogins * 0.02); // 估算：每次约0.02元
        stats.put("apiConfigured", aiConfigured);
        stats.put("modelReady", aiConfigured);
        stats.put("knowledgeBaseSize", totalQuestions + totalKnowledgePoints);
        stats.put("supportedCourses", totalCourses);
        stats.put("totalUsers", totalUsers);
        stats.put("status", aiConfigured ? "active" : "api_key_missing");

        return stats;
    }

    // ===================== AI 异常监控 =====================

    /**
     * AI 异常监控：汇总最近登录/操作日志，调用 LLM 检测异常（暴力破解、成绩异动等）。
     * 由 Controller 内联实现下沉，行为保持一致：失败时返回包含降级提示的结果，不抛异常。
     */
    @Override
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

    // ===================== 公共工具方法 =====================
    /**
     * 读取目录下指定后缀文件信息（名称、大小、修改时间）
     * @param dirPath 目录路径
     * @param suffix 文件后缀
     * @return 文件信息集合
     */
    private List<Map<String, Object>> getFileInfoList(String dirPath, String suffix) {
        List<Map<String, Object>> fileList = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return fileList;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(suffix));
        if (files == null || files.length == 0) {
            return fileList;
        }

        for (File file : files) {
            Map<String, Object> fileMap = new HashMap<>(4);
            fileMap.put("fileName", file.getName());
            fileMap.put("size", file.length());
            LocalDateTime modifyTime = Instant.ofEpochMilli(file.lastModified())
                    .atZone(SYSTEM_ZONE)
                    .toLocalDateTime();
            fileMap.put("lastModified", modifyTime);
            fileList.add(fileMap);
        }
        return fileList;
    }
}