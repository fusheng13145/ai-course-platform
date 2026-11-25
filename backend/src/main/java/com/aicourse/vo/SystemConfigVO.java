package com.aicourse.vo;

import com.aicourse.entity.SystemConfig;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统配置出参。
 *
 * <p>字段与实体一一对应（10 个）。
 *
 * <p>{@link #masked} 用于非管理员读端点：入参是 {@code @Cacheable} 返回的实体，
 * 不能原地改，故由工厂直接产出脱敏副本，替代原先「先 copy 再改」的中间对象。
 */
@Data
public class SystemConfigVO {

    private Long id;
    private String configKey;
    private String configValue;
    private String configGroup;
    private String configType;
    private String description;
    private Boolean isPublic;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 管理员视图：原值。 */
    public static SystemConfigVO from(SystemConfig config) {
        return build(config, config == null ? null : config.getConfigValue());
    }

    /** 非管理员视图：命中敏感键名时值替换为固定掩码，其余字段不动。 */
    public static SystemConfigVO masked(SystemConfig config) {
        if (config == null) {
            return null;
        }
        return build(config, maskValue(config.getConfigKey(), config.getConfigValue()));
    }

    public static List<SystemConfigVO> from(List<SystemConfig> configs) {
        return configs == null ? List.of() : configs.stream().map(SystemConfigVO::from).toList();
    }

    public static List<SystemConfigVO> masked(List<SystemConfig> configs) {
        return configs == null ? List.of() : configs.stream().map(SystemConfigVO::masked).toList();
    }

    /**
     * 敏感键名脱敏规则的唯一实现。键名命中
     * `secret / password / token / credential / api_key / apikey / *_key` 时返回固定掩码。
     *
     * <p>控制器里「按 key 读单值」端点直接调用本方法，保证两条读路径共用同一规则。
     */
    public static String maskValue(String key, String value) {
        if (value == null || key == null) {
            return value;
        }
        String k = key.toLowerCase();
        if (k.contains("secret") || k.contains("password") || k.contains("token")
                || k.contains("credential") || k.contains("api_key") || k.contains("apikey")
                || k.endsWith("_key") || k.endsWith("key")) {
            return "******";
        }
        return value;
    }

    private static SystemConfigVO build(SystemConfig config, String value) {
        if (config == null) {
            return null;
        }
        SystemConfigVO vo = new SystemConfigVO();
        vo.setId(config.getId());
        vo.setConfigKey(config.getConfigKey());
        vo.setConfigValue(value);
        vo.setConfigGroup(config.getConfigGroup());
        vo.setConfigType(config.getConfigType());
        vo.setDescription(config.getDescription());
        vo.setIsPublic(config.getIsPublic());
        vo.setSortOrder(config.getSortOrder());
        vo.setCreatedAt(config.getCreatedAt());
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }
}
