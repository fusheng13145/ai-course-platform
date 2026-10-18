package com.aicourse.service.impl;

import com.aicourse.service.AiCallLogService;
import com.aicourse.service.SystemService;
import com.aicourse.service.component.sys.SysBackupComponent;
import com.aicourse.service.component.sys.SysConfigComponent;
import com.aicourse.service.component.sys.SysLogComponent;
import com.aicourse.service.component.sys.SysMonitorComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SystemServiceImpl 门面委托测试（纯 Mockito）。
 * <p>验证门面对 4 个协作者组件的委托转发与返回值透传；组件内部逻辑由各组件单测覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class SystemServiceImplTest {

    @Mock
    private SysConfigComponent configComponent;
    @Mock
    private SysBackupComponent backupComponent;
    @Mock
    private SysLogComponent logComponent;
    @Mock
    private SysMonitorComponent monitorComponent;
    @Mock
    private AiCallLogService aiCallLogService;

    private SystemService service;

    @BeforeEach
    void setUp() {
        service = new SystemServiceImpl(configComponent, backupComponent, logComponent,
                monitorComponent, aiCallLogService);
    }

    // ===================== 概览/配置 =====================
    @Test
    @DisplayName("getOverview 委托配置组件")
    void getOverview() {
        when(configComponent.getOverview()).thenReturn(Map.of("serviceStatus", "RUNNING"));
        assertEquals("RUNNING", service.getOverview().get("serviceStatus"));
        verify(configComponent).getOverview();
    }

    @Test
    @DisplayName("getConfig 委托配置组件")
    void getConfig() {
        when(configComponent.getConfig("k")).thenReturn("v");
        assertEquals("v", service.getConfig("k"));
    }

    @Test
    @DisplayName("saveConfig 委托配置组件")
    void saveConfig() {
        service.saveConfig("k", "v", "g");
        verify(configComponent).saveConfig("k", "v", "g");
    }

    @Test
    @DisplayName("getConfigs 委托配置组件")
    void getConfigs() {
        when(configComponent.getConfigs("g")).thenReturn(List.of(Map.of("key", "k")));
        assertEquals(1, service.getConfigs("g").size());
    }

    // ===================== 健康检查 / 备份 / 日志 / AI =====================
    @Test
    @DisplayName("getHealth 委托监控组件")
    void getHealth() {
        when(monitorComponent.getHealth()).thenReturn(Map.of("status", "UP"));
        assertEquals("UP", service.getHealth().get("status"));
    }

    @Test
    @DisplayName("createBackup 委托备份组件")
    void createBackup() {
        when(backupComponent.createBackup()).thenReturn("backup_20260829.sql");
        assertEquals("backup_20260829.sql", service.createBackup());
    }

    @Test
    @DisplayName("getBackups/restoreBackup 委托备份组件")
    void backupDelegation() {
        service.getBackups();
        service.restoreBackup("x.sql");
        verify(backupComponent).getBackups();
        verify(backupComponent).restoreBackup("x.sql");
    }

    @Test
    @DisplayName("getLogFiles/readLogFile 委托日志组件")
    void logDelegation() {
        when(logComponent.readLogFile("a.log", 10, "k")).thenReturn("content");
        service.getLogFiles();
        assertEquals("content", service.readLogFile("a.log", 10, "k"));
        verify(logComponent).getLogFiles();
    }

    @Test
    @DisplayName("getAIUsageStats/aiMonitor 委托监控组件")
    void aiDelegation() {
        when(monitorComponent.getAIUsageStats()).thenReturn(Map.of("status", "active"));
        when(monitorComponent.aiMonitor()).thenReturn(Map.of("alerts", List.of()));
        assertEquals("active", service.getAIUsageStats().get("status"));
        assertNotNull(service.aiMonitor().get("alerts"));
        verify(monitorComponent).aiMonitor();
    }

    @Test
    @DisplayName("getAIUsageByCourse 委托 AI 调用日志服务（跨课程 AI 统计）")
    void aiUsageByCourseDelegation() {
        when(aiCallLogService.statistics(30)).thenReturn(Map.of("totalCalls", 7L));
        assertEquals(7L, service.getAIUsageByCourse(30).get("totalCalls"));
        verify(aiCallLogService).statistics(30);
    }
}
