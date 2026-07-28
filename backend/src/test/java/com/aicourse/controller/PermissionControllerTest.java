package com.aicourse.controller;
import com.aicourse.controller.PermissionController;
import com.aicourse.service.PermissionService;
import com.aicourse.entity.RolePermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * PermissionController 接口测试（升级版）。
 * 原生成器弱断言（status != 401 && status != 403）已全部替换为真实业务断言：
 * 对依赖的 PermissionService 方法打桩返回合理业务对象，断言 200/code=200，并校验关键字段透传、verify 路由委托。
 * 该控制器类级别 @PreAuthorize("hasRole('ADMIN')")，原有 6 个 STUDENT 越权 403 测试保留不动。
 */
@WebMvcTest(PermissionController.class)
class PermissionControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.PermissionService permissionService;

    private RolePermission samplePermission(Long id, String role, String key) {
        RolePermission rp = new RolePermission();
        rp.setId(id);
        rp.setRole(role);
        rp.setPermissionKey(key);
        rp.setIsActive(true);
        return rp;
    }

    @Test
    @DisplayName("GET /admin/permissions/role/1 - 返回200且角色权限列表透传")
    void getByRole_authorized_returns_200() throws Exception {
        when(permissionService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(samplePermission(1L, "STUDENT", "course:view")));
        mockMvc.perform(get("/admin/permissions/role/1").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].permissionKey").value("course:view"));
        verify(permissionService).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }


    @Test
    @DisplayName("GET /admin/permissions/role/1 - 未认证返回401")
    void getByRole_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/admin/permissions/role/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /admin/permissions/role/1 - STUDENT角色返回403")
    void getByRole_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/admin/permissions/role/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /admin/permissions/all - 返回200且全部权限透传")
    void getAll_authorized_returns_200() throws Exception {
        when(permissionService.list()).thenReturn(List.of(samplePermission(2L, "TEACHER", "task:create")));
        mockMvc.perform(get("/admin/permissions/all").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].permissionKey").value("task:create"));
        verify(permissionService).list();
    }


    @Test
    @DisplayName("GET /admin/permissions/all - 未认证返回401")
    void getAll_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/admin/permissions/all"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /admin/permissions/all - STUDENT角色返回403")
    void getAll_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/admin/permissions/all").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /admin/permissions/create - 返回200且创建权限透传")
    void create_authorized_returns_200() throws Exception {
        when(permissionService.save(any())).thenReturn(true);
        mockMvc.perform(post("/admin/permissions/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"permissionKey\":\"course:view\",\"permissionName\":\"查看课程\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.permissionKey").value("course:view"));
        verify(permissionService).save(any(RolePermission.class));
    }


    @Test
    @DisplayName("POST /admin/permissions/create - 未认证返回401")
    void create_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/admin/permissions/create").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /admin/permissions/create - STUDENT角色返回403")
    void create_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/admin/permissions/create").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("PUT /admin/permissions/update/1 - 返回200且更新后权限透传")
    void update_authorized_returns_200() throws Exception {
        when(permissionService.getById(1L)).thenReturn(samplePermission(1L, "TEACHER", "task:update"));
        when(permissionService.updateById(any())).thenReturn(true);
        mockMvc.perform(put("/admin/permissions/update/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionName\":\"修改任务\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(permissionService).updateById(any(RolePermission.class));
    }


    @Test
    @DisplayName("PUT /admin/permissions/update/1 - 未认证返回401")
    void update_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/admin/permissions/update/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("PUT /admin/permissions/update/1 - STUDENT角色返回403")
    void update_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/admin/permissions/update/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("DELETE /admin/permissions/delete/1 - 返回200且删除委托被调用")
    void delete_authorized_returns_200() throws Exception {
        when(permissionService.removeById(1L)).thenReturn(true);
        mockMvc.perform(delete("/admin/permissions/delete/1").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(permissionService).removeById(eq(1L));
    }


    @Test
    @DisplayName("DELETE /admin/permissions/delete/1 - 未认证返回401")
    void delete_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/admin/permissions/delete/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("DELETE /admin/permissions/delete/1 - STUDENT角色返回403")
    void delete_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/admin/permissions/delete/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("PATCH /admin/permissions/toggle/1 - 返回200且切换后权限透传")
    void toggleStatus_authorized_returns_200() throws Exception {
        when(permissionService.getById(1L)).thenReturn(samplePermission(1L, "TEACHER", "task:toggle"));
        when(permissionService.updateById(any())).thenReturn(true);
        mockMvc.perform(patch("/admin/permissions/toggle/1").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(permissionService).getById(eq(1L));
        verify(permissionService).updateById(any(RolePermission.class));
    }


    @Test
    @DisplayName("PATCH /admin/permissions/toggle/1 - 未认证返回401")
    void toggleStatus_unauthorized_returns_401() throws Exception {
        mockMvc.perform(patch("/admin/permissions/toggle/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("PATCH /admin/permissions/toggle/1 - STUDENT角色返回403")
    void toggleStatus_student_forbidden_403() throws Exception {
        mockMvc.perform(patch("/admin/permissions/toggle/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

}
