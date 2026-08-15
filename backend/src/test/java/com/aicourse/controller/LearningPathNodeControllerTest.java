package com.aicourse.controller;
import com.aicourse.controller.LearningPathNodeController;
import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.service.LearningPathNodeService;
import com.aicourse.service.LearningPathService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * LearningPathNodeController 接口测试（升级版）。
 * 将「伪覆盖弱断言」改造为真实业务断言：对每个 endpoint 依赖的 service 方法打桩返回合理业务对象，
 * 断言 HTTP 200 + ApiResponse.code=200 + 关键字段透传（jsonPath），并 verify 路由正确委托与参数透传。
 */
@WebMvcTest(LearningPathNodeController.class)
class LearningPathNodeControllerTest extends ControllerTestBase {

    @MockitoBean
    private LearningPathNodeService nodeService;
    @MockitoBean
    private LearningPathService learningPathService;

    private LearningPathNode sampleNode() {
        LearningPathNode n = new LearningPathNode();
        n.setId(1L);
        n.setLearningPathId(1L);
        return n;
    }

    private LearningPath samplePath() {
        LearningPath p = new LearningPath();
        p.setId(1L);
        p.setStudentId(1L);
        return p;
    }

    @BeforeEach
    void stubOwnerLookup() {
        // owner 校验依赖 learningPathService.getById 返回归属当前用户(=1L)的路径
        when(learningPathService.getById(any())).thenReturn(samplePath());
        // 节点归属校验（assertNodeOwner）先经 nodeService.getById 定位节点所属路径
        when(nodeService.getById(any())).thenReturn(sampleNode());
    }

    @Test
    @DisplayName("GET /learning-path-nodes/path/1 - 返回200且data为节点列表")
    void getPathNodes_authorized_returns_success() throws Exception {
        when(nodeService.getPathNodes(any())).thenReturn(List.of(sampleNode()));
        mockMvc.perform(get("/learning-path-nodes/path/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(nodeService).getPathNodes(eq(1L));
    }

    @Test
    @DisplayName("GET /learning-path-nodes/path/1 - 未认证返回401")
    void getPathNodes_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/learning-path-nodes/path/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /learning-path-nodes/1/start - 返回200且正确委托startNode")
    void startNode_authorized_returns_success() throws Exception {
        doNothing().when(nodeService).startNode(any());
        mockMvc.perform(post("/learning-path-nodes/1/start").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(nodeService).startNode(eq(1L));
    }

    @Test
    @DisplayName("POST /learning-path-nodes/1/start - 未认证返回401")
    void startNode_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/learning-path-nodes/1/start"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /learning-path-nodes/1/complete - 返回200且正确委托completeNode")
    void completeNode_authorized_returns_success() throws Exception {
        doNothing().when(nodeService).completeNode(any(), any(), any());
        mockMvc.perform(post("/learning-path-nodes/1/complete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":90,\"actualTime\":30}").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(nodeService).completeNode(eq(1L), eq(new BigDecimal("90")), eq(30));
    }

    @Test
    @DisplayName("POST /learning-path-nodes/1/complete - 未认证返回401")
    void completeNode_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/learning-path-nodes/1/complete").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /learning-path-nodes/1/skip - 返回200且正确委托skipNode")
    void skipNode_authorized_returns_success() throws Exception {
        doNothing().when(nodeService).skipNode(any());
        mockMvc.perform(post("/learning-path-nodes/1/skip").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(nodeService).skipNode(eq(1L));
    }

    @Test
    @DisplayName("POST /learning-path-nodes/1/skip - 未认证返回401")
    void skipNode_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/learning-path-nodes/1/skip"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /learning-path-nodes/current/1 - 返回200且data透传当前节点")
    void getCurrentNode_authorized_returns_success() throws Exception {
        when(nodeService.getCurrentNode(any())).thenReturn(sampleNode());
        mockMvc.perform(get("/learning-path-nodes/current/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(nodeService).getCurrentNode(eq(1L));
    }

    @Test
    @DisplayName("GET /learning-path-nodes/current/1 - 未认证返回401")
    void getCurrentNode_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/learning-path-nodes/current/1"))
                .andExpect(status().isUnauthorized());
    }
}
