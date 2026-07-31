package com.aicourse.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.common.ApiResponse;
import com.aicourse.entity.SystemConfig;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.common.ApiCode;
import com.aicourse.service.EmailService;
import com.aicourse.service.SystemConfigService;
import com.aicourse.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统配置管理接口控制器
 */
@Tag(name = "系统配置", description = "系统配置项的增删改查、分组查询、邮件测试")
@RestController
@RequestMapping("/system-configs")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;
    private final EmailService emailService;
    private final UserService userService;

    public SystemConfigController(SystemConfigService systemConfigService,
                                  EmailService emailService,
                                  UserService userService) {
        this.systemConfigService = systemConfigService;
        this.emailService = emailService;
        this.userService = userService;
    }

    /**
     * 获取所有配置列表
     * @return 配置列表
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<SystemConfig>> list() {
        List<SystemConfig> configs = systemConfigService.list();
        return ApiResponse.success(configs);
    }

    /**
     * 根据分组获取配置列表
     * @param group 配置分组（GENERAL/SECURITY/EMAIL/NOTIFICATION/UI）
     * @return 分组配置列表
     */
    @GetMapping("/group/{group}")
    public ApiResponse<List<SystemConfig>> getByGroup(@PathVariable String group) {
        List<SystemConfig> configs = systemConfigService.getConfigsByGroup(group);
        return ApiResponse.success(configs);
    }

    /**
     * 获取所有公开配置（前端可用）
     * @return 公开配置列表
     */
    @GetMapping("/public")
    public ApiResponse<List<SystemConfig>> getPublic() {
        List<SystemConfig> configs = systemConfigService.getPublicConfigs();
        return ApiResponse.success(configs);
    }

    /**
     * 根据键名获取单个配置值
     * @param key 配置键名
     * @return 配置值字符串
     */
    @GetMapping("/key/{key}")
    public ApiResponse<String> getValue(@PathVariable String key) {
        String value = systemConfigService.getConfigValue(key);
        return ApiResponse.success("获取成功", value);
    }

    /**
     * 更新单个配置项
     * @param key   配置键名
     * @param body  请求体（包含value字段）
     * @return 操作结果
     */
    @PutMapping("/key/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateByKey(@PathVariable String key, @Valid @RequestBody Map<String, String> body) {
        String value = body.get("value");
        systemConfigService.updateConfig(key, value);
        return ApiResponse.success("更新成功", null);
    }

    /**
     * 批量更新配置项
     * @param configs 配置键值对映射
     * @return 操作结果
     */
    @PostMapping("/batch-update")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> batchUpdate(@Valid @RequestBody Map<String, String> configs) {
        systemConfigService.batchUpdateConfigs(configs);
        return ApiResponse.success("批量更新成功", null);
    }

    /**
     * 按关键字搜索配置
     * @param keyword 搜索关键字
     * @return 匹配的配置列表
     */
    @GetMapping("/search")
    public ApiResponse<List<SystemConfig>> search(@RequestParam String keyword) {
        List<SystemConfig> configs = systemConfigService.searchConfigs(keyword);
        return ApiResponse.success(configs);
    }

    /**
     * 发送测试邮件（验证邮件配置有效性，发送至当前管理员邮箱）
     * @param auth 认证信息
     * @return 操作结果
     */
    @PostMapping("/test-email")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> testEmail(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        User user = userService.getById(userId);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "当前管理员未配置邮箱，无法发送测试邮件");
        }
        emailService.sendVerificationCode(user.getEmail(), "888888", "TEST");
        return ApiResponse.success("测试邮件已发送", null);
    }
}
