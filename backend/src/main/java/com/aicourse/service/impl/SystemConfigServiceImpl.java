package com.aicourse.service.impl;

import com.aicourse.entity.SystemConfig;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.SystemConfigMapper;
import com.aicourse.service.SystemConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 系统配置管理服务实现类
 */
@Service
public class SystemConfigServiceImpl extends ServiceImpl<SystemConfigMapper, SystemConfig> implements SystemConfigService {

    /**
     * 根据配置键名获取单个配置值
     */
    @Override
    public String getConfigValue(String key) {
        LambdaQueryWrapper<SystemConfig> queryWrapper = new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, key)
                .last("LIMIT 1");
        SystemConfig config = this.getOne(queryWrapper);
        return config != null ? config.getConfigValue() : null;
    }

    /**
     * 获取指定分组的配置列表（按排序序号升序）
     */
    @Override
    public List<SystemConfig> getConfigsByGroup(String group) {
        LambdaQueryWrapper<SystemConfig> queryWrapper = new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigGroup, group)
                .orderByAsc(SystemConfig::getSortOrder);
        return this.list(queryWrapper);
    }

    /**
     * 更新指定配置项的值（配置项必须已存在）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(String key, String value) {
        LambdaQueryWrapper<SystemConfig> queryWrapper = new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, key);
        SystemConfig config = this.getOne(queryWrapper);
        if (config == null) {
            throw new BusinessException("配置项不存在: " + key);
        }
        config.setConfigValue(value);
        this.updateById(config);
    }

    /**
     * 批量更新多个配置项（循环调用单条更新）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateConfigs(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            updateConfig(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 获取所有公开配置（前端可直接访问）
     */
    @Override
    public List<SystemConfig> getPublicConfigs() {
        LambdaQueryWrapper<SystemConfig> queryWrapper = new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getIsPublic, true)
                .orderByAsc(SystemConfig::getSortOrder);
        return this.list(queryWrapper);
    }

    /**
     * 获取配置值，查询不到时返回默认值
     */
    @Override
    public String getConfigValueOrDefault(String key, String defaultValue) {
        String value = getConfigValue(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 按关键字搜索配置（模糊匹配键名、描述）
     */
    @Override
    public List<SystemConfig> searchConfigs(String keyword) {
        LambdaQueryWrapper<SystemConfig> queryWrapper = new LambdaQueryWrapper<SystemConfig>()
                .and(wrapper -> wrapper
                        .like(SystemConfig::getConfigKey, keyword)
                        .or()
                        .like(SystemConfig::getDescription, keyword))
                .orderByAsc(SystemConfig::getSortOrder);
        return this.list(queryWrapper);
    }
}
