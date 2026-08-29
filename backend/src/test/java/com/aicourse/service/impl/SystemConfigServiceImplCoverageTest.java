package com.aicourse.service.impl;

import com.aicourse.entity.SystemConfig;
import com.aicourse.mapper.SystemConfigMapper;
import com.aicourse.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * 系统配置服务单元测试：最大化 INSTRUCTION 覆盖（ServiceImpl + mock baseMapper）。
 * 注：@Cacheable/@CacheEvict 在纯 Mockito 单测中无 Spring 代理，直接走真实方法逻辑。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SystemConfigServiceImplCoverageTest {

    @Mock
    private SystemConfigMapper baseMapper;

    private SystemConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SystemConfigServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
    }

    private SystemConfig cfg(String key, String value, String group, Boolean isPublic) {
        SystemConfig c = new SystemConfig();
        c.setId(1L);
        c.setConfigKey(key);
        c.setConfigValue(value);
        c.setConfigGroup(group);
        c.setIsPublic(isPublic);
        return c;
    }

    // ===================== getConfigValue =====================
    @Test
    @DisplayName("getConfigValue: 命中返回值")
    void getConfigValue_found() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(cfg("site.name", "AI平台", "base", true));
        assertEquals("AI平台", service.getConfigValue("site.name"));
    }

    @Test
    @DisplayName("getConfigValue: 未命中返回 null")
    void getConfigValue_null() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertNull(service.getConfigValue("missing"));
    }

    // ===================== getConfigsByGroup =====================
    @Test
    @DisplayName("getConfigsByGroup: 返回分组列表")
    void getConfigsByGroup() {
        List<SystemConfig> list = Arrays.asList(cfg("a", "1", "g", true), cfg("b", "2", "g", false));
        when(baseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(list);
        assertEquals(list, service.getConfigsByGroup("g"));
    }

    // ===================== updateConfig =====================
    @Test
    @DisplayName("updateConfig: 配置不存在抛异常")
    void updateConfig_notFound() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateConfig("k", "v"));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("updateConfig: 成功更新")
    void updateConfig_success() {
        SystemConfig c = cfg("k", "old", "g", true);
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(c);
        when(baseMapper.updateById(any(SystemConfig.class))).thenReturn(1);
        service.updateConfig("k", "new");
        assertEquals("new", c.getConfigValue());
        verify(baseMapper).updateById(c);
    }

    // ===================== batchUpdateConfigs =====================
    @Test
    @DisplayName("batchUpdateConfigs: null/空 -> 直接返回")
    void batchUpdateConfigs_empty() {
        assertDoesNotThrow(() -> service.batchUpdateConfigs(null));
        assertDoesNotThrow(() -> service.batchUpdateConfigs(Collections.emptyMap()));
    }

    @Test
    @DisplayName("batchUpdateConfigs: 循环更新多条")
    void batchUpdateConfigs_multiple() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("k1", "v1");
        map.put("k2", "v2");
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(cfg("k", "o", "g", true));
        when(baseMapper.updateById(any(SystemConfig.class))).thenReturn(1);
        service.batchUpdateConfigs(map);
        verify(baseMapper, times(2)).updateById(any(SystemConfig.class));
    }

    // ===================== getPublicConfigs =====================
    @Test
    @DisplayName("getPublicConfigs: 返回公开配置")
    void getPublicConfigs() {
        List<SystemConfig> list = Arrays.asList(cfg("a", "1", "g", true));
        when(baseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(list);
        assertEquals(list, service.getPublicConfigs());
    }

    // ===================== getConfigValueOrDefault =====================
    @Test
    @DisplayName("getConfigValueOrDefault: 有值返回原值")
    void getConfigValueOrDefault_value() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(cfg("k", "real", "g", true));
        assertEquals("real", service.getConfigValueOrDefault("k", "def"));
    }

    @Test
    @DisplayName("getConfigValueOrDefault: 无值返回默认值")
    void getConfigValueOrDefault_default() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertEquals("def", service.getConfigValueOrDefault("k", "def"));
    }

    // ===================== searchConfigs =====================
    @Test
    @DisplayName("searchConfigs: 模糊匹配")
    void searchConfigs() {
        List<SystemConfig> list = Arrays.asList(cfg("site.x", "1", "g", true));
        when(baseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(list);
        assertEquals(list, service.searchConfigs("site"));
    }
}
