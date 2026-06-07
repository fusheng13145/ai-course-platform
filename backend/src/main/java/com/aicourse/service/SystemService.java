package com.aicourse.service;

import java.util.List;
import java.util.Map;

public interface SystemService {
    Map<String, Object> getOverview();
    String getConfig(String key);
    void saveConfig(String key, String value, String group);
    List<Map<String, Object>> getConfigs(String group);
    Map<String, Object> getHealth();
    String createBackup();
    List<Map<String, Object>> getBackups();
    void restoreBackup(String fileName);
    List<Map<String, Object>> getLogFiles();
    String readLogFile(String fileName, int lines, String keyword);
}
