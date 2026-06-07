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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SystemServiceImpl implements SystemService {

    private final SystemConfigMapper systemConfigMapper;

    public SystemServiceImpl(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    @Override
    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new HashMap<>();
        overview.put("systemTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        overview.put("serviceStatus", "RUNNING");
        return overview;
    }

    @Override
    public String getConfig(String key) {
        SystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, key));
        return config != null ? config.getConfigValue() : null;
    }

    @Override
    public void saveConfig(String key, String value, String group) {
        SystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, key));
        
        if (config != null) {
            config.setConfigValue(value);
            if (group != null) {
                config.setConfigGroup(group);
            }
            config.setUpdatedAt(LocalDateTime.now());
            systemConfigMapper.updateById(config);
        } else {
            config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setConfigGroup(group != null ? group : "DEFAULT");
            config.setConfigType("STRING");
            config.setIsPublic(false);
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            systemConfigMapper.insert(config);
        }
    }

    @Override
    public List<Map<String, Object>> getConfigs(String group) {
        LambdaQueryWrapper<SystemConfig> queryWrapper = new LambdaQueryWrapper<>();
        if (group != null && !group.isEmpty()) {
            queryWrapper.eq(SystemConfig::getConfigGroup, group);
        }
        
        List<SystemConfig> configs = systemConfigMapper.selectList(queryWrapper);
        return configs.stream().map(config -> {
            Map<String, Object> map = new HashMap<>();
            map.put("key", config.getConfigKey());
            map.put("value", config.getConfigValue());
            map.put("group", config.getConfigGroup());
            map.put("type", config.getConfigType());
            map.put("description", config.getDescription());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        health.put("database", "CONNECTED");
        return health;
    }

    @Override
    public String createBackup() {
        String backupDir = "backups";
        File dir = new File(backupDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupFileName = "backup_" + timestamp + ".sql";
        return backupFileName;
    }

    @Override
    public List<Map<String, Object>> getBackups() {
        List<Map<String, Object>> backups = new ArrayList<>();
        File backupDir = new File("backups");
        if (backupDir.exists() && backupDir.isDirectory()) {
            File[] files = backupDir.listFiles((d, name) -> name.endsWith(".sql"));
            if (files != null) {
                for (File file : files) {
                    Map<String, Object> backup = new HashMap<>();
                    backup.put("fileName", file.getName());
                    backup.put("size", file.length());
                    backup.put("lastModified", LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(file.lastModified()),
                            java.time.ZoneId.systemDefault()));
                    backups.add(backup);
                }
            }
        }
        return backups;
    }

    @Override
    public void restoreBackup(String fileName) {
        File backupFile = new File("backups/" + fileName);
        if (!backupFile.exists()) {
            throw new BusinessException("备份文件不存在");
        }
    }

    @Override
    public List<Map<String, Object>> getLogFiles() {
        List<Map<String, Object>> logFiles = new ArrayList<>();
        File logDir = new File("logs");
        if (logDir.exists() && logDir.isDirectory()) {
            File[] files = logDir.listFiles((d, name) -> name.endsWith(".log"));
            if (files != null) {
                for (File file : files) {
                    Map<String, Object> logFile = new HashMap<>();
                    logFile.put("fileName", file.getName());
                    logFile.put("size", file.length());
                    logFile.put("lastModified", LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(file.lastModified()),
                            java.time.ZoneId.systemDefault()));
                    logFiles.add(logFile);
                }
            }
        }
        return logFiles;
    }

    @Override
    public String readLogFile(String fileName, int lines, String keyword) {
        Path path = Paths.get("logs", fileName);
        if (!Files.exists(path)) {
            throw new BusinessException("日志文件不存在");
        }
        
        try (Stream<String> stream = Files.lines(path)) {
            List<String> allLines = stream.collect(Collectors.toList());
            
            if (keyword != null && !keyword.isEmpty()) {
                allLines = allLines.stream()
                        .filter(line -> line.contains(keyword))
                        .collect(Collectors.toList());
            }
            
            int start = Math.max(0, allLines.size() - lines);
            List<String> resultLines = allLines.subList(start, allLines.size());
            return String.join("\n", resultLines);
        } catch (IOException e) {
            throw new BusinessException("读取日志文件失败");
        }
    }
}