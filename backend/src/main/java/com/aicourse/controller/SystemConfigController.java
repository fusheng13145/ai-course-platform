package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.common.ApiResponse;
import com.aicourse.security.AccessSupport;
import com.aicourse.vo.SystemConfigVO;
import com.aicourse.entity.SystemConfig;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.common.ApiCode;
import com.aicourse.security.AdminScopeGuard;
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

    private static final Logger log = LoggerFactory.getLogger(SystemConfigController.class);

    private final SystemConfigService systemConfigService;
    private final EmailService emailService;
    private final UserService userService;
    private final AdminScopeGuard adminScopeGuard;

    public SystemConfigController(SystemConfigService systemConfigService,
                                  EmailService emailService,
                                  UserService userService,
                                  AdminScopeGuard adminScopeGuard) {
        this.systemConfigService = systemConfigService;
        this.emailService = emailService;
        this.userService = userService;
        this.adminScopeGuard = adminScopeGuard;
    }

    /**
     * 获取所有配置列表
     * @return 配置列表
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<SystemConfigVO>> list() {
        List<SystemConfig> configs = systemConfigService.list();
        return ApiResponse.success(SystemConfigVO.from(configs));
    }

    /**
     * 根据分组获取配置列表（修复 #7：读侧可见性收敛）
     *
     * <p>原端点无任何角色校验且不校验 {@code is_public}，已实测
     * {@code GET /system-configs/group/ai} 向学生返回 3 条 {@code isPublic=false} 配置。
     * 现口径：ADMIN 返回全部分组配置；非 ADMIN 仅返回 {@code is_public=true} 的公开项，
     * 并对敏感键名（含 {@code _key}、{@code secret}、{@code token} 等）做值脱敏。
     * 内部组件不受影响——它们走 {@link SystemConfigService#getConfigValue(String)}。</p>
     * @param group 配置分组（GENERAL/SECURITY/EMAIL/NOTIFICATION/UI）
     * @param auth  认证信息
     * @return 分组配置列表
     */
    @GetMapping("/group/{group}")
    public ApiResponse<List<SystemConfigVO>> getByGroup(@PathVariable String group, Authentication auth) {
        List<SystemConfig> configs = systemConfigService.getConfigsByGroup(group);
        if (AccessSupport.isAdmin(auth)) {
            return ApiResponse.success(SystemConfigVO.from(configs));
        }
        // 非管理员：仅保留公开项，并脱敏敏感键名。
        // getConfigsByGroup 结果来自 @Cacheable，VO 工厂只读不写，不会污染缓存。
        List<SystemConfigVO> visible = SystemConfigVO.masked(configs.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsPublic()))
                .toList());
        return ApiResponse.success(visible);
    }

    /**
     * 获取所有公开配置（前端可用）
     * @return 公开配置列表
     */
    @GetMapping("/public")
    public ApiResponse<List<SystemConfigVO>> getPublic() {
        List<SystemConfig> configs = systemConfigService.getPublicConfigs();
        return ApiResponse.success(SystemConfigVO.from(configs));
    }

    /**
     * 根据键名获取单个配置值（修复 #7：读侧可见性收敛）
     *
     * <p>原端点无任何角色校验且直接回原始值，导致 {@code isPublic=false} 的配置
     * （如 {@code ai_api_key}）可被任意登录用户读取。现口径：
     * ADMIN 放行；非 ADMIN 仅当该键 {@code is_public=true} 时放行，否则 403。
     * 非 ADMIN 命中敏感键名时对值脱敏。</p>
     * @param key  配置键名
     * @param auth 认证信息
     * @return 配置值字符串
     */
    @GetMapping("/key/{key}")
    public ApiResponse<String> getValue(@PathVariable String key, Authentication auth) {
        SystemConfig config = systemConfigService.getConfigByKey(key);
        String value = config != null ? config.getConfigValue() : null;

        if (!AccessSupport.isAdmin(auth)) {
            // 键不存在时无信息可泄露，保持原有「返回 null」语义；
            // 存在但非公开 → 显式 403。
            if (config != null && !Boolean.TRUE.equals(config.getIsPublic())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "无权读取该配置项");
            }
            value = SystemConfigVO.maskValue(key, value);
        }
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
        adminScopeGuard.assertSchoolOnly();
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
        adminScopeGuard.assertSchoolOnly();
        systemConfigService.batchUpdateConfigs(configs);
        return ApiResponse.success("批量更新成功", null);
    }

    /**
     * 按关键字搜索配置（修复 #7 同源缺口：读侧可见性收敛）
     *
     * <p>与 /group、/key 同属读端点，原实现同样不校验角色与 {@code is_public}，
     * 可被用于按关键词枚举并读取非公开配置。现口径与 {@link #getByGroup} 对齐：
     * ADMIN 全量；非 ADMIN 仅公开项并脱敏敏感键名。</p>
     * @param keyword 搜索关键字
     * @param auth    认证信息
     * @return 匹配的配置列表
     */
    @GetMapping("/search")
    public ApiResponse<List<SystemConfigVO>> search(@RequestParam String keyword, Authentication auth) {
        List<SystemConfig> configs = systemConfigService.searchConfigs(keyword);
        if (AccessSupport.isAdmin(auth)) {
            return ApiResponse.success(SystemConfigVO.from(configs));
        }
        List<SystemConfigVO> visible = SystemConfigVO.masked(configs.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsPublic()))
                .toList());
        return ApiResponse.success(visible);
    }

    /**
     * 发送测试邮件（验证邮件配置有效性，发送至当前管理员邮箱）
     * @param auth 认证信息
     * @return 操作结果
     */
    @PostMapping("/test-email")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> testEmail(Authentication auth) {
        adminScopeGuard.assertSchoolOnly();
        Long userId = (Long) auth.getPrincipal();
        User user = userService.getById(userId);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "当前管理员未配置邮箱，无法发送测试邮件");
        }
        emailService.sendVerificationCode(user.getEmail(), "888888", "TEST");
        return ApiResponse.success("测试邮件已发送", null);
    }

}
