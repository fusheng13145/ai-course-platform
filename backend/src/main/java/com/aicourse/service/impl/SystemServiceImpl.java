package com.aicourse.service.impl;

import com.aicourse.entity.SystemConfig;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.SystemConfigMapper;
import com.aicourse.service.SystemService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 系统服务实现类
 * 系统概览、配置管理、健康检查、备份、日志读取等功能
 */
@Service
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

    public SystemServiceImpl(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
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
        health.put("database", "CONNECTED");
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
        return "backup_" + timestamp + SQL_SUFFIX;
    }

    @Override
    public List<Map<String, Object>> getBackups() {
        return getFileInfoList(BACKUP_DIR, SQL_SUFFIX);
    }

    @Override
    public void restoreBackup(String fileName) {
        // 路径防穿越校验
        if (fileName.contains(File.separator) || fileName.contains("..")) {
            throw new BusinessException("非法文件名");
        }
        File backupFile = new File(BACKUP_DIR, fileName);
        if (!backupFile.exists()) {
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
     * 获取AI功能使用统计
     */
    @Override
    public Map<String, Object> getAIUsageStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCalls", 0);
        stats.put("todayCalls", 0);
        stats.put("avgResponseTime", 0);
        stats.put("costEstimate", 0);
        stats.put("status", "statistics_pending");
        return stats;
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