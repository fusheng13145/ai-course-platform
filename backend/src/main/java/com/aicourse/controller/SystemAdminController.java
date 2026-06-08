package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.SystemService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统管理接口控制器（仅管理员可访问）
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class SystemAdminController {

    private final SystemService systemService;

    public SystemAdminController(SystemService systemService) {
        this.systemService = systemService;
    }

    /**
     * 获取系统概览统计数据
     * @return 系统统计信息
     */
    @GetMapping("/admin/system/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> overviewData = systemService.getOverview();
        return ApiResponse.success(overviewData);
    }

    /**
     * 查询系统配置项
     * @param group 配置分组（可选）
     * @return 配置列表
     */
    @GetMapping("/admin/system/configs")
    public ApiResponse<List<Map<String, Object>>> configs(@RequestParam(required = false) String group) {
        List<Map<String, Object>> configList = systemService.getConfigs(group);
        return ApiResponse.success(configList);
    }

    /**
     * 保存/更新系统配置
     * @param body 配置键、值、分组
     * @return 操作结果
     */
    @PostMapping("/admin/system/configs")
    public ApiResponse<Void> saveConfig(@RequestBody Map<String, String> body) {
        String key = body.get("key");
        String value = body.get("value");
        String group = body.get("group");
        systemService.saveConfig(key, value, group);
        return ApiResponse.success("保存成功", null);
    }

    /**
     * 检测系统运行状态
     * @return 健康状态信息
     */
    @GetMapping("/admin/system/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> healthInfo = systemService.getHealth();
        return ApiResponse.success(healthInfo);
    }

    /**
     * 执行数据备份
     * @return 备份文件名称
     */
    @PostMapping("/admin/backup")
    public ApiResponse<String> createBackup() {
        String backupFileName = systemService.createBackup();
        return ApiResponse.success("备份成功", backupFileName);
    }

    /**
     * 查询所有备份文件列表
     * @return 备份文件信息集合
     */
    @GetMapping("/admin/backup")
    public ApiResponse<List<Map<String, Object>>> getBackups() {
        List<Map<String, Object>> backupList = systemService.getBackups();
        return ApiResponse.success(backupList);
    }

    /**
     * 从指定备份文件恢复数据
     * @param fileName 备份文件名
     * @return 操作结果
     */
    @PostMapping("/admin/system/backup/{fileName}/restore")
    public ApiResponse<Void> restoreBackup(@PathVariable String fileName) {
        systemService.restoreBackup(fileName);
        return ApiResponse.success("恢复成功", null);
    }

    /**
     * 查询日志文件列表
     * @return 日志文件信息集合
     */
    @GetMapping("/admin/system/logs")
    public ApiResponse<List<Map<String, Object>>> getLogFiles() {
        List<Map<String, Object>> logFileList = systemService.getLogFiles();
        return ApiResponse.success(logFileList);
    }

    /**
     * 读取指定日志内容
     * @param fileName 日志文件名
     * @param lines 读取行数
     * @param keyword 过滤关键字（可选）
     * @return 日志文本内容
     */
    @GetMapping("/admin/system/logs/{fileName}")
    public ApiResponse<String> readLog(
            @PathVariable String fileName,
            @RequestParam(defaultValue = "100") int lines,
            @RequestParam(required = false) String keyword) {
        String logContent = systemService.readLogFile(fileName, lines, keyword);
        return ApiResponse.success(logContent);
    }
}