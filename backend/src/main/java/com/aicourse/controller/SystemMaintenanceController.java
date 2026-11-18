package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.SystemService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * 系统维护接口（S-01 从 {@code SystemAdminController} 拆出）。
 *
 * <p>覆盖配置、日志、健康检查、备份/恢复与 AI 监控；所有 URL 与类级
 * {@code @PreAuthorize("hasRole('ADMIN')")} 保持原样，仅做代码归属拆分。</p>
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "系统管理", description = "系统配置、日志、健康检查、备份恢复与 AI 监控")
public class SystemMaintenanceController {

    private final SystemService systemService;
    private final AdminScopeGuard adminScopeGuard;

    public SystemMaintenanceController(SystemService systemService,
                                       AdminScopeGuard adminScopeGuard) {
        this.systemService = systemService;
        this.adminScopeGuard = adminScopeGuard;
    }

    // ==================== 系统配置（4个接口） ====================

    /**
     * 获取系统配置
     */
    @GetMapping("/admin/configs")
    public ApiResponse<List<Map<String, Object>>> getConfigs(
            @RequestParam(required = false) String group) {
        List<Map<String, Object>> configList = systemService.getConfigs(group);
        return ApiResponse.success(configList);
    }

    /**
     * 更新系统配置
     */
    @PutMapping("/admin/configs/{configKey}")
    public ApiResponse<Void> updateConfig(@PathVariable String configKey,
                                           @Valid @RequestBody Map<String, String> body) {
        adminScopeGuard.assertSchoolOnly();
        String value = body.get("value");
        String group = body.get("group");
        systemService.saveConfig(configKey, value, group);
        return ApiResponse.success("配置更新成功", null);
    }

    /**
     * 获取系统日志
     */
    @GetMapping("/admin/logs")
    public ApiResponse<List<Map<String, Object>>> getLogs() {
        adminScopeGuard.assertSchoolOnly();
        List<Map<String, Object>> logFileList = systemService.getLogFiles();
        return ApiResponse.success(logFileList);
    }

    /**
     * 获取系统健康状态
     */
    @GetMapping("/admin/health")
    public ApiResponse<Map<String, Object>> getHealth() {
        adminScopeGuard.assertSchoolOnly();
        Map<String, Object> healthInfo = systemService.getHealth();
        return ApiResponse.success(healthInfo);
    }

    // ==================== 数据备份 ====================

    /**
     * 数据备份
     */
    @PostMapping("/admin/backup")
    public ApiResponse<String> createBackup() {
        adminScopeGuard.assertSchoolOnly();
        String backupFileName = systemService.createBackup();
        return ApiResponse.success("备份成功", backupFileName);
    }

    // ==================== 保留原有兼容接口 ====================

    /**
     * 获取系统概览统计数据（原接口，保留兼容）
     */
    @GetMapping("/admin/system/overview")
    public ApiResponse<Map<String, Object>> overview() {
        adminScopeGuard.assertSchoolOnly();
        Map<String, Object> overviewData = systemService.getOverview();
        return ApiResponse.success(overviewData);
    }

    /**
     * 查询系统配置项（原接口，保留兼容）
     */
    @GetMapping("/admin/system/configs")
    public ApiResponse<List<Map<String, Object>>> systemConfigs(
            @RequestParam(required = false) String group) {
        return getConfigs(group);
    }

    /**
     * 保存/更新系统配置（原接口，保留兼容）
     */
    @PostMapping("/admin/system/configs")
    public ApiResponse<Void> saveConfig(@Valid @RequestBody Map<String, String> body) {
        adminScopeGuard.assertSchoolOnly();
        String key = body.get("key");
        String value = body.get("value");
        String group = body.get("group");
        systemService.saveConfig(key, value, group);
        return ApiResponse.success("保存成功", null);
    }

    /**
     * 检测系统运行状态（原接口，保留兼容）
     */
    @GetMapping("/admin/system/health")
    public ApiResponse<Map<String, Object>> systemHealth() {
        return getHealth();
    }

    /**
     * 查询所有备份文件列表（原接口，保留兼容）
     */
    @GetMapping("/admin/backup")
    public ApiResponse<List<Map<String, Object>>> getBackups() {
        adminScopeGuard.assertSchoolOnly();
        List<Map<String, Object>> backupList = systemService.getBackups();
        return ApiResponse.success(backupList);
    }

    /**
     * 从指定备份文件恢复数据（2026-11-14 P0-2B 真实现，替换原「仅校验不执行」假实现）。
     * <p>目标库必须显式指定：隔离演练库须带 {@code aicourse_restore_}/{@code restore_} 前缀
     * （与 scripts/restore-mysql.sh 同护栏）；恢复当前应用库须显式传 {@code targetDb=<当前库名>}
     * 且 {@code confirmCurrentDb=true}（破坏性动作，请于维护窗口执行）。缺省 {@code targetDb}
     * 直接 400 拒绝，不存在隐式恢复当前库的路径。返回可核验读数：目标库/表数/耗时。</p>
     */
    @PostMapping("/admin/system/backup/{fileName}/restore")
    public ApiResponse<Map<String, Object>> restoreBackup(@PathVariable String fileName,
                                                          @RequestParam(required = false) String targetDb,
                                                          @RequestParam(required = false, defaultValue = "false") boolean confirmCurrentDb) {
        adminScopeGuard.assertSchoolOnly();
        Map<String, Object> report = systemService.restoreBackup(fileName, targetDb, confirmCurrentDb);
        return ApiResponse.success("恢复完成（详见 data 读数：目标库/表数/耗时）", report);
    }

    /**
     * 查询日志文件列表（原接口，保留兼容）
     */
    @GetMapping("/admin/system/logs")
    public ApiResponse<List<Map<String, Object>>> getLogFiles() {
        return getLogs();
    }

    /**
     * 读取指定日志内容（原接口，保留兼容）
     */
    @GetMapping("/admin/system/logs/{fileName}")
    public ApiResponse<String> readLog(
            @PathVariable String fileName,
            @RequestParam(defaultValue = "100") int lines,
            @RequestParam(required = false) String keyword) {
        adminScopeGuard.assertSchoolOnly();
        String logContent = systemService.readLogFile(fileName, lines, keyword);
        // DR-33：必须用双参重载。ApiResponse.success(String) 与 success(T data) 并存时，
        // String 入参绑定到「仅提示语」的那个，日志内容会跑到 message 里、data 变 null；
        // 又因 spring.jackson.default-property-inclusion=non_null，data 键整体消失，
        // 调用方按契约读 res.data 恒为空。本文件其余 ApiResponse<String> 端点均已用双参形式。
        return ApiResponse.success("读取成功", logContent);
    }

    // ==================== AI 智能监控 ====================

    /**
     * AI 异常监控：汇总最近登录/操作日志，调用 LLM 检测异常（暴力破解、成绩异动等）
     * GET /admin/ai-monitor
     */
    @GetMapping("/admin/ai-monitor")
    public Callable<ApiResponse<Map<String, Object>>> aiMonitor() {
        adminScopeGuard.assertSchoolOnly();
        return () -> ApiResponse.success(systemService.aiMonitor());
    }
}
