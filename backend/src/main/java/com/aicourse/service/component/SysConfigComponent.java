package com.aicourse.service.component;

import com.aicourse.entity.SystemConfig;
import com.aicourse.mapper.SystemConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统配置协作者：系统概览与配置管理（查询/保存/分组列表）。
 * <p>由 {@code SystemServiceImpl} 门面委托调用。</p>
 */
@Component
public class SysConfigComponent {

    private static final String DEFAULT_GROUP = "DEFAULT";
    private static final String STRING_TYPE = "STRING";
    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern(DATETIME_PATTERN);

    private final SystemConfigMapper systemConfigMapper;

    public SysConfigComponent(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    /** 系统概览（时间 + 运行状态） */
    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new HashMap<>(4);
        overview.put("systemTime", LocalDateTime.now().format(DATETIME_FORMATTER));
        overview.put("serviceStatus", "RUNNING");
        return overview;
    }

    /** 按 key 查询配置值（无记录返回 null） */
    public String getConfig(String key) {
        SystemConfig config = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key)
        );
        return config == null ? null : config.getConfigValue();
    }

    /** 保存配置：已存在则更新，否则插入 */
    public void saveConfig(String key, String value, String group) {
        SystemConfig config = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key)
        );
        LocalDateTime now = LocalDateTime.now();

        if (config != null) {
            config.setConfigValue(value);
            if (group != null) {
                config.setConfigGroup(group);
            }
            config.setUpdatedAt(now);
            systemConfigMapper.updateById(config);
        } else {
            SystemConfig newConfig = new SystemConfig();
            newConfig.setConfigKey(key);
            newConfig.setConfigValue(value);
            newConfig.setConfigGroup(group == null ? DEFAULT_GROUP : group);
            newConfig.setConfigType(STRING_TYPE);
            newConfig.setIsPublic(false);
            newConfig.setCreatedAt(now);
            newConfig.setUpdatedAt(now);
            systemConfigMapper.insert(newConfig);
        }
    }

    /** 按分组查询配置列表（group 为空查询全部） */
    public List<Map<String, Object>> getConfigs(String group) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        if (group != null && !group.isBlank()) {
            wrapper.eq(SystemConfig::getConfigGroup, group);
        }

        List<SystemConfig> configList = systemConfigMapper.selectList(wrapper);
        return configList.stream()
                .map(config -> {
                    Map<String, Object> map = new HashMap<>(6);
                    map.put("key", config.getConfigKey());
                    map.put("value", config.getConfigValue());
                    map.put("group", config.getConfigGroup());
                    map.put("type", config.getConfigType());
                    map.put("description", config.getDescription());
                    return map;
                })
                .collect(Collectors.toList());
    }
}
