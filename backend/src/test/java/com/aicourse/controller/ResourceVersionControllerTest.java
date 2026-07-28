package com.aicourse.controller;
import com.aicourse.controller.ResourceVersionController;
import com.aicourse.service.ResourceVersionService;
import com.aicourse.entity.ResourceVersion;
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
 * ResourceVersionController 接口测试（升级版）。
 * 原生成器弱断言（status != 401 && status != 403）已全部替换为真实业务断言：
 * 对依赖的 ResourceVersionService 方法打桩返回合理业务对象，断言 200/code=200，并校验关键字段透传、verify 路由委托。
 * 对带有 @PreAuthorize("hasAnyRole('TEACHER','ADMIN')") 的端点新增 STUDENT 越权 403 测试。
 */
@WebMvcTest(ResourceVersionController.class)
class ResourceVersionControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.ResourceVersionService resourceVersionService;

    private ResourceVersion sampleVersion(Long id, Integer versionNumber) {
        ResourceVersion v = new ResourceVersion();
        v.setId(id);
        v.setResourceId(1L);
        v.setVersionNumber(versionNumber);
        v.setFileName("v" + versionNumber + ".pdf");
        return v;
    }

    @Test
    @DisplayName("GET /resource-versions/resource/1 - 返回200且版本历史透传")
    void getVersionHistory_authorized_returns_200() throws Exception {
        when(resourceVersionService.getVersionHistory(1L)).thenReturn(List.of(sampleVersion(1L, 2)));
        mockMvc.perform(get("/resource-versions/resource/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(resourceVersionService).getVersionHistory(eq(1L));
    }


    @Test
    @DisplayName("GET /resource-versions/resource/1 - 未认证返回401")
    void getVersionHistory_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/resource-versions/resource/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /resource-versions - 返回200且新版本透传")
    void createNewVersion_authorized_returns_200() throws Exception {
        when(resourceVersionService.createNewVersion(eq(1L), eq("v.pdf"), any(), eq("/x"), any(), any(), any()))
                .thenReturn(sampleVersion(1L, 2));
        mockMvc.perform(post("/resource-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":1,\"fileName\":\"v.pdf\",\"url\":\"/x\"}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.versionNumber").value(2));
        verify(resourceVersionService).createNewVersion(eq(1L), eq("v.pdf"), any(), eq("/x"), any(), any(), any());
    }


    @Test
    @DisplayName("POST /resource-versions - STUDENT 越权返回403")
    void createNewVersion_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/resource-versions").contentType(MediaType.APPLICATION_JSON).content("{\"resourceId\":1,\"fileName\":\"v.pdf\",\"url\":\"/x\"}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /resource-versions - 未认证返回401")
    void createNewVersion_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/resource-versions").contentType(MediaType.APPLICATION_JSON).content("{\"resourceId\":1,\"fileName\":\"v.pdf\",\"url\":\"/x\"}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /resource-versions/1/rollback - 返回200且回滚委托被调用")
    void rollbackToVersion_authorized_returns_200() throws Exception {
        mockMvc.perform(post("/resource-versions/1/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNumber\":2}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(resourceVersionService).rollbackToVersion(eq(1L), eq(2));
    }


    @Test
    @DisplayName("POST /resource-versions/1/rollback - STUDENT 越权返回403")
    void rollbackToVersion_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/resource-versions/1/rollback").contentType(MediaType.APPLICATION_JSON).content("{\"versionNumber\":2}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /resource-versions/1/rollback - 未认证返回401")
    void rollbackToVersion_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/resource-versions/1/rollback").contentType(MediaType.APPLICATION_JSON).content("{\"versionNumber\":2}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /resource-versions/active/1 - 返回200且当前版本透传")
    void getActiveVersion_authorized_returns_200() throws Exception {
        when(resourceVersionService.getActiveVersion(1L)).thenReturn(sampleVersion(1L, 1));
        mockMvc.perform(get("/resource-versions/active/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(resourceVersionService).getActiveVersion(eq(1L));
    }


    @Test
    @DisplayName("GET /resource-versions/active/1 - 未认证返回401")
    void getActiveVersion_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/resource-versions/active/1"))
                .andExpect(status().isUnauthorized());
    }

}
