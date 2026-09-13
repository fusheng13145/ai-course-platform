package com.aicourse.service.component.sys;

import com.aicourse.entity.SystemConfig;
import com.aicourse.mapper.SystemConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SysConfigComponent 单元测试（纯 Mockito）。
 * <p>覆盖系统概览与配置读写（查询/保存更新/保存新增/分组列表）。</p>
 */
@ExtendWith(MockitoExtension.class)
class SysConfigComponentTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    private SysConfigComponent component;

    @BeforeEach
    void setUp() {
        component = new SysConfigComponent(systemConfigMapper);
    }

    // ===================== 概览 =====================
    @Test
    @DisplayName("getOverview 返回系统时间与服务状态")
    void getOverview() {
        Map<String, Object> r = component.getOverview();
        assertEquals("RUNNING", r.get("serviceStatus"));
        assertNotNull(r.get("systemTime"));
    }

    // ===================== getConfig =====================
    @Test
    @DisplayName("getConfig 无记录返回 null")
    void getConfig_null() {
        when(systemConfigMapper.selectOne(any())).thenReturn(null);
        assertNull(component.getConfig("k"));
    }

    @Test
    @DisplayName("getConfig 有记录返回值")
    void getConfig_value() {
        SystemConfig c = new SystemConfig();
        c.setConfigValue("v");
        when(systemConfigMapper.selectOne(any())).thenReturn(c);
        assertEquals("v", component.getConfig("k"));
    }

    // ===================== saveConfig =====================
    @Test
    @DisplayName("saveConfig 已存在则更新（含分组分支）")
    void saveConfig_existing() {
        SystemConfig c = new SystemConfig();
        c.setId(1L);
        when(systemConfigMapper.selectOne(any())).thenReturn(c);
        component.saveConfig("k", "v", "GRP");
        assertEquals("GRP", c.getConfigGroup());
        verify(systemConfigMapper).updateById(c);

        // 分组为 null -> 不修改分组
        SystemConfig c2 = new SystemConfig();
        c2.setId(2L);
        c2.setConfigGroup("OLD");
        when(systemConfigMapper.selectOne(any())).thenReturn(c2);
        component.saveConfig("k", "v", null);
        assertEquals("OLD", c2.getConfigGroup());
        verify(systemConfigMapper, times(2)).updateById(any(SystemConfig.class));
    }

    @Test
    @DisplayName("saveConfig 不存在则插入")
    void saveConfig_new() {
        when(systemConfigMapper.selectOne(any())).thenReturn(null);
        component.saveConfig("k", "v", "g");
        verify(systemConfigMapper).insert(any(SystemConfig.class));
    }

    // ===================== getConfigs =====================
    @Test
    @DisplayName("getConfigs 按分组/不带分组查询")
    void getConfigs() {
        SystemConfig c = new SystemConfig();
        c.setConfigKey("k");
        c.setConfigValue("v");
        c.setConfigGroup("g");
        c.setConfigType("STRING");
        c.setDescription("d");
        when(systemConfigMapper.selectList(any())).thenReturn(Collections.singletonList(c));
        List<Map<String, Object>> r = component.getConfigs("g");
        assertEquals(1, r.size());
        assertEquals("k", r.get(0).get("key"));

        when(systemConfigMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertTrue(component.getConfigs(null).isEmpty());
    }
}
