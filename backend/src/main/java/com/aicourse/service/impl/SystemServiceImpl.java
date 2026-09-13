package com.aicourse.service.impl;

import com.aicourse.service.SystemService;
import com.aicourse.service.component.sys.SysBackupComponent;
import com.aicourse.service.component.sys.SysConfigComponent;
import com.aicourse.service.component.sys.SysLogComponent;
import com.aicourse.service.component.sys.SysMonitorComponent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 系统服务门面：概览/配置/健康检查/备份/日志/AI 监控职责委托给对应协作者组件。
 * <p>拆分自原 576 行单类（2026-08-29），组件位于 service.component 包，各组件只持有自己需要的依赖。</p>
 */
@Service
public class SystemServiceImpl implements SystemService {

    private final SysConfigComponent configComponent;
    private final SysBackupComponent backupComponent;
    private final SysLogComponent logComponent;
    private final SysMonitorComponent monitorComponent;

    public SystemServiceImpl(SysConfigComponent configComponent,
                             SysBackupComponent backupComponent,
                             SysLogComponent logComponent,
                             SysMonitorComponent monitorComponent) {
        this.configComponent = configComponent;
        this.backupComponent = backupComponent;
        this.logComponent = logComponent;
        this.monitorComponent = monitorComponent;
    }

    // ===================== 系统概览与配置 =====================
    @Override
    public Map<String, Object> getOverview() {
        return configComponent.getOverview();
    }

    @Override
    public String getConfig(String key) {
        return configComponent.getConfig(key);
    }

    @Override
    public void saveConfig(String key, String value, String group) {
        configComponent.saveConfig(key, value, group);
    }

    @Override
    public List<Map<String, Object>> getConfigs(String group) {
        return configComponent.getConfigs(group);
    }

    // ===================== 健康检查 =====================
    @Override
    public Map<String, Object> getHealth() {
        return monitorComponent.getHealth();
    }

    // ===================== 数据库备份 =====================
    @Override
    public String createBackup() {
        return backupComponent.createBackup();
    }

    @Override
    public List<Map<String, Object>> getBackups() {
        return backupComponent.getBackups();
    }

    @Override
    public void restoreBackup(String fileName) {
        backupComponent.restoreBackup(fileName);
    }

    // ===================== 日志文件 =====================
    @Override
    public List<Map<String, Object>> getLogFiles() {
        return logComponent.getLogFiles();
    }

    @Override
    public String readLogFile(String fileName, int lines, String keyword) {
        return logComponent.readLogFile(fileName, lines, keyword);
    }

    // ===================== AI 统计与监控 =====================
    @Override
    public Map<String, Object> getAIUsageStats() {
        return monitorComponent.getAIUsageStats();
    }

    @Override
    public Map<String, Object> aiMonitor() {
        return monitorComponent.aiMonitor();
    }
}
