package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.SystemConfig;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.SystemConfigMapper;
import com.aicourse.service.SystemConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 系统配置管理服务实现类
 */
@Service
public class SystemConfigServiceImpl extends ServiceImpl<SystemConfigMapper, SystemConfig> implements SystemConfigService {

    private static final Logger log = LoggerFactory.getLogger(SystemConfigServiceImpl.class);

    private static final String CACHE_NAME = "systemConfig";

    /**
     * 修复 #11：注入 CacheManager，用于批量更新后显式清空（自调用绕过 @CacheEvict 注解代理）。
     */
    @Autowired
    private CacheManager cacheManager;

    /**
     * 根据配置键名获取单个配置值
     * <p>内部通道，保留原始值语义（见接口注释），不在此处做 is_public 判定。</p>
     */
    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "#key")
    public String getConfigValue(String key) {
        LambdaQueryWrapper<SystemConfig> queryWrapper = new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, key)
                .last("LIMIT 1");
        SystemConfig config = this.getOne(queryWrapper);
        return config != null ? config.getConfigValue() : null;
    }

    /**
     * 根据键名获取配置实体（含 isPublic），供 HTTP 读侧做可见性收敛（修复 #7）。
     * <p>刻意不加缓存：可见性判定需要拿到实时 {@code is_public}，且与内部
     * {@code getConfigValue} 的缓存语义解耦，避免相互污染。</p>
     */
    @Override
    public SystemConfig getConfigByKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<SystemConfig> queryWrapper = new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, key)
                .last("LIMIT 1");
        return this.getOne(queryWrapper);
    }

    /**
     * 获取指定分组的配置列表（按排序序号升序）
     */
    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'group:' + #group")
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
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public void updateConfig(String key, String value) {
        log.info("更新系统配置");

        LambdaQueryWrapper<SystemConfig> queryWrapper = new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, key);
        SystemConfig config = this.getOne(queryWrapper);
        if (config == null) {
            throw new BusinessException("配置项不存在: " + key);
        }
        // 同时清除对应分组的缓存
        String group = config.getConfigGroup();
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
        // 修复 #11：循环内 this.updateConfig(...) 属自调用，绕过 Spring 缓存代理，
        // @CacheEvict 注解不会执行；此处直接操作 CacheManager 兜底清空，确保批量保存后立即可见。
        if (cacheManager != null) {
            org.springframework.cache.Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    /**
     * 获取所有公开配置（前端可直接访问）
     */
    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'public'")
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
