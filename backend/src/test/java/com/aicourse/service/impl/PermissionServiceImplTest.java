package com.aicourse.service.impl;

import com.aicourse.entity.RolePermission;
import com.aicourse.entity.User;
import com.aicourse.mapper.RolePermissionMapper;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PermissionServiceImpl 单元测试（纯 Mockito）。
 * <p>覆盖用户权限聚合（用户不存在兜底 / 菜单过滤）、角色权限查询与写操作委托。
 * 注意：@Cacheable/@CacheEvict 注解在纯 Mockito 下不生效，缓存语义由缓存相关集成测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private RolePermissionMapper rolePermissionMapper;

    private PermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                RolePermission.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                User.class);
        service = new PermissionServiceImpl(userService);
        ReflectionTestUtils.setField(service, "baseMapper", rolePermissionMapper);
    }

    // ===================== getUserPermissions =====================
    @Test
    @DisplayName("getUserPermissions 用户不存在返回空权限与空菜单")
    void getUserPermissions_userMissing() {
        when(userService.getById(1L)).thenReturn(null);
        Map<String, Object> result = service.getUserPermissions(1L);
        assertTrue(((List<?>) result.get("permissions")).isEmpty());
        assertTrue(((List<?>) result.get("menus")).isEmpty());
        // 形状契约：角色字段与按钮映射即使用户不存在也必须存在，
        // 否则前端 effectiveRole/serverRole 与 hasButtonPermission 读到 undefined
        assertEquals("", result.get("role"));
        assertTrue(((Map<?, ?>) result.get("buttons")).isEmpty());
        verify(rolePermissionMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("getUserPermissions 返回角色权限并仅提取 MENU 类型菜单")
    void getUserPermissions_normal() {
        User user = new User();
        user.setId(1L);
        user.setRole("TEACHER");
        when(userService.getById(1L)).thenReturn(user);

        RolePermission menuPerm = new RolePermission();
        menuPerm.setRole("TEACHER");
        menuPerm.setPermissionType("MENU");
        menuPerm.setPermissionKey("course");
        RolePermission buttonPerm = new RolePermission();
        buttonPerm.setRole("TEACHER");
        buttonPerm.setPermissionType("BUTTON");
        buttonPerm.setPermissionKey("course:create");
        when(rolePermissionMapper.selectList(any())).thenReturn(Arrays.asList(menuPerm, buttonPerm));

        Map<String, Object> result = service.getUserPermissions(1L);
        assertEquals(2, ((List<?>) result.get("permissions")).size());
        List<String> menus = (List<String>) result.get("menus");
        assertEquals(1, menus.size());
        assertEquals("course", menus.get(0));
        // 服务端权威角色：前端 stores/auth.effectiveRole 以此压过可篡改的本地 user.role
        assertEquals("TEACHER", result.get("role"));
        // 按钮权限扁平化为 {key: true}，与前端 hasButtonPermission 的读取形状对齐
        Map<String, Boolean> buttons = (Map<String, Boolean>) result.get("buttons");
        assertEquals(1, buttons.size());
        assertEquals(Boolean.TRUE, buttons.get("course:create"));
    }

    // ===================== getRolePermissions =====================
    @Test
    @DisplayName("getRolePermissions 按角色查询已启用权限")
    void getRolePermissions() {
        RolePermission permission = new RolePermission();
        permission.setRole("STUDENT");
        permission.setPermissionType("MENU");
        when(rolePermissionMapper.selectList(any())).thenReturn(Collections.singletonList(permission));

        List<RolePermission> list = service.getRolePermissions("STUDENT");
        assertEquals(1, list.size());
        assertEquals("STUDENT", list.get(0).getRole());
        verify(rolePermissionMapper).selectList(any());
    }

    // ===================== 写操作委托 =====================
    @Test
    @DisplayName("save/updateById/removeById 委托 Mapper 返回结果")
    void writeOps() {
        RolePermission permission = new RolePermission();
        permission.setRole("ADMIN");

        when(rolePermissionMapper.insert(any(RolePermission.class))).thenReturn(1);
        assertTrue(service.save(permission));
        verify(rolePermissionMapper).insert(permission);

        when(rolePermissionMapper.updateById(any(RolePermission.class))).thenReturn(1);
        assertTrue(service.updateById(permission));
        verify(rolePermissionMapper).updateById(permission);

        when(rolePermissionMapper.deleteById(any(Serializable.class))).thenReturn(1);
        assertTrue(service.removeById(1L));
        verify(rolePermissionMapper).deleteById(1L);
    }
}
