package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.SystemService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@PreAuthorize("hasRole('ADMIN')")
public class SystemAdminController {
    private final SystemService systemService;

    public SystemAdminController(SystemService systemService) {
        this.systemService = systemService;
    }

    @GetMapping("/admin/system/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.success(systemService.getOverview());
    }

    @GetMapping("/admin/system/configs")
    public ApiResponse<List<Map<String, Object>>> configs(@RequestParam(required = false) String group) {
        return ApiResponse.success(systemService.getConfigs(group));
    }

    @PostMapping("/admin/system/configs")
    public ApiResponse<Void> saveConfig(@RequestBody Map<String, String> body) {
        systemService.saveConfig(body.get("key"), body.get("value"), body.get("group"));
        return ApiResponse.success("保存成功", null);
    }

    @GetMapping("/admin/system/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(systemService.getHealth());
    }

    @PostMapping("/admin/backup")
    public ApiResponse<String> createBackup() {
        return ApiResponse.success("备份成功", systemService.createBackup());
    }

    @GetMapping("/admin/backup")
    public ApiResponse<List<Map<String, Object>>> getBackups() {
        return ApiResponse.success(systemService.getBackups());
    }

    @PostMapping("/admin/system/backup/{fileName}/restore")
    public ApiResponse<Void> restoreBackup(@PathVariable String fileName) {
        systemService.restoreBackup(fileName);
        return ApiResponse.success("恢复成功", null);
    }

    @GetMapping("/admin/system/logs")
    public ApiResponse<List<Map<String, Object>>> getLogFiles() {
        return ApiResponse.success(systemService.getLogFiles());
    }

    @GetMapping("/admin/system/logs/{fileName}")
    public ApiResponse<String> readLog(@PathVariable String fileName,
                                        @RequestParam(defaultValue = "100") int lines,
                                        @RequestParam(required = false) String keyword) {
        return ApiResponse.success(systemService.readLogFile(fileName, lines, keyword));
    }
}
