package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.SystemService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/system")
@PreAuthorize("hasRole('ADMIN')")
public class SystemAdminController {
    private final SystemService systemService;

    public SystemAdminController(SystemService systemService) {
        this.systemService = systemService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.success(systemService.getOverview());
    }

    @GetMapping("/configs")
    public ApiResponse<List<Map<String, Object>>> configs(@RequestParam(required = false) String group) {
        return ApiResponse.success(systemService.getConfigs(group));
    }

    @PostMapping("/configs")
    public ApiResponse<Void> saveConfig(@RequestParam String key, @RequestParam String value,
                                         @RequestParam(required = false) String group) {
        systemService.saveConfig(key, value, group);
        return ApiResponse.success("保存成功", null);
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(systemService.getHealth());
    }

    @PostMapping("/backup")
    public ApiResponse<String> createBackup() {
        return ApiResponse.success("备份创建成功", systemService.createBackup());
    }

    @GetMapping("/backup")
    public ApiResponse<List<Map<String, Object>>> getBackups() {
        return ApiResponse.success(systemService.getBackups());
    }

    @PostMapping("/backup/{fileName}/restore")
    public ApiResponse<Void> restoreBackup(@PathVariable String fileName) {
        systemService.restoreBackup(fileName);
        return ApiResponse.success("恢复成功", null);
    }

    @GetMapping("/logs")
    public ApiResponse<List<Map<String, Object>>> getLogFiles() {
        return ApiResponse.success(systemService.getLogFiles());
    }

    @GetMapping("/logs/{fileName}")
    public ApiResponse<String> readLog(@PathVariable String fileName,
                                        @RequestParam(defaultValue = "100") int lines,
                                        @RequestParam(required = false) String keyword) {
        return ApiResponse.success(systemService.readLogFile(fileName, lines, keyword));
    }
}
