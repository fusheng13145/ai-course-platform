package com.aicourse.service;

import com.aicourse.entity.SystemConfig;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 系统配置管理服务接口
 */
public interface SystemConfigService extends IService<SystemConfig> {

    /**
     * 根据配置键名获取单个配置值
     * <p><b>内部通道</b>：这是应用内部组件（如 AI Provider 读取 ai_* 配置、
     * ProctorService 读取阈值）直接获取原始值的通道，**刻意不做 is_public 判定**，
     * 以免收紧 HTTP 暴露面时误伤内部调用。请勿将其暴露给未经鉴权的调用方。</p>
     * @param key 配置键名
     * @return 配置值
     */
    String getConfigValue(String key);

    /**
     * 根据键名获取配置实体（含 {@code isPublic} 可见性标记）。
     * <p>专供 HTTP 读侧（Controller）判断「该键对当前调用方是否可见」，不参与缓存。
     * 内部组件请继续使用 {@link #getConfigValue(String)}。</p>
     * @param key 配置键名
     * @return 配置实体；不存在时返回 {@code null}
     */
    SystemConfig getConfigByKey(String key);

    /**
     * 获取指定分组的配置列表（按排序序号升序）
     * @param group 配置分组
     * @return 配置列表
     */
    List<SystemConfig> getConfigsByGroup(String group);

    /**
     * 更新指定配置项的值
     * @param key   配置键名
     * @param value 新配置值
     */
    void updateConfig(String key, String value);

    /**
     * 批量更新多个配置项
     * @param configs 配置键值对映射
     */
    void batchUpdateConfigs(Map<String, String> configs);

    /**
     * 获取所有公开配置（前端可直接访问）
     * @return 公开配置列表
     */
    List<SystemConfig> getPublicConfigs();

    /**
     * 获取配置值，查询不到时返回默认值
     * @param key          配置键名
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    String getConfigValueOrDefault(String key, String defaultValue);

    /**
     * 按关键字搜索配置（模糊匹配键名、描述）
     * @param keyword 搜索关键字
     * @return 匹配的配置列表
     */
    List<SystemConfig> searchConfigs(String keyword);
}
