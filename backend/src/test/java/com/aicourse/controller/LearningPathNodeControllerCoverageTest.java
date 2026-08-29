package com.aicourse.controller;

import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.exception.GlobalExceptionHandler;
import com.aicourse.service.LearningPathNodeService;
import com.aicourse.service.LearningPathService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖率测试：LearningPathNodeController（独立 MockMvc，不加载 Spring 上下文）。
 * 覆盖路径节点查询/开始/完成/跳过/当前节点，以及归属校验（assertPathOwner / assertNodeOwner）
 * 的未认证、管理员放行、本人放行、非本人拒绝、路径不存在、节点不存在等分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LearningPathNodeController 覆盖率测试")
class LearningPathNodeControllerCoverageTest {

    @Mock
    private LearningPathNodeService nodeService;
    @Mock
    private LearningPathService learningPathService;

    private MockMvc mockMvc;

    private LearningPathNode sampleNode() {
        LearningPathNode n = new LearningPathNode();
        n.setId(1L);
        n.setLearningPathId(1L);
        return n;
    }

    private LearningPath ownerPath() {
        LearningPath p = new LearningPath();
        p.setId(1L);
        p.setStudentId(1L);
        return p;
    }

    private LearningPath otherPath() {
        LearningPath p = new LearningPath();
        p.setId(1L);
        p.setStudentId(999L);
        return p;
    }

    @BeforeEach
    void setup() {
        LearningPathNodeController controller = new LearningPathNodeController(nodeService, learningPathService);
        // 独立 MockMvc 需手动注册全局异常处理器，使权限/归属/不存在等 BusinessException
        // 被统一转换为 4xx，而非穿透为 500。
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        lenient().when(learningPathService.getById(any())).thenReturn(ownerPath());
        lenient().when(nodeService.getById(any())).thenReturn(sampleNode());
        lenient().when(nodeService.getPathNodes(any())).thenReturn(List.of(sampleNode()));
        lenient().when(nodeService.getCurrentNode(any())).thenReturn(sampleNode());
        lenient().doNothing().when(nodeService).startNode(any());
        lenient().doNothing().when(nodeService).completeNode(any(), any(), any());
        lenient().doNothing().when(nodeService).skipNode(any());
    }

    private static RequestPostProcessor withAuth(Long userId, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        Authentication auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        return request -> {
            request.setUserPrincipal(auth);
            if (userId != null) request.setRemoteUser(String.valueOf(userId));
            return request;
        };
    }

    @Test
    @DisplayName("GET /learning-path-nodes/path/{pathId} 管理员 / 本人(STUDENT) / 非本人 / 路径不存在 / 未认证")
    void getPathNodes() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/learning-path-nodes/path/1").with(withAuth(1L, "ADMIN")))
                .andExpect(status().isOk());
        when(learningPathService.getById(any())).thenReturn(ownerPath());
        mockMvc.perform(MockMvcRequestBuilders.get("/learning-path-nodes/path/1").with(withAuth(1L, "STUDENT")))
                .andExpect(status().isOk());
        when(learningPathService.getById(any())).thenReturn(otherPath());
        mockMvc.perform(MockMvcRequestBuilders.get("/learning-path-nodes/path/1").with(withAuth(1L, "STUDENT")))
                .andExpect(status().is4xxClientError());
        when(learningPathService.getById(any())).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.get("/learning-path-nodes/path/1").with(withAuth(1L, "STUDENT")))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(MockMvcRequestBuilders.get("/learning-path-nodes/path/1")).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /learning-path-nodes/{id}/start 管理员 / 节点不存在 / 非本人")
    void startNode() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/learning-path-nodes/1/start").with(withAuth(1L, "ADMIN")))
                .andExpect(status().isOk());
        when(nodeService.getById(any())).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.post("/learning-path-nodes/1/start").with(withAuth(1L, "STUDENT")))
                .andExpect(status().is4xxClientError());
        when(nodeService.getById(any())).thenReturn(sampleNode());
        when(learningPathService.getById(any())).thenReturn(otherPath());
        // 非本人 -> BusinessException("无权访问该学习路径", code=400) -> 400（非 500）
        mockMvc.perform(MockMvcRequestBuilders.post("/learning-path-nodes/1/start").with(withAuth(1L, "STUDENT")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /learning-path-nodes/{id}/complete 成功（score / actualTime）")
    void completeNode() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/learning-path-nodes/1/complete").with(withAuth(1L, "ADMIN"))
                        .contentType("application/json").content("{\"score\":90,\"actualTime\":30}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /learning-path-nodes/{id}/skip 管理员成功")
    void skipNode() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/learning-path-nodes/1/skip").with(withAuth(1L, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /learning-path-nodes/current/{pathId} 管理员成功 / 路径不存在 / 未认证")
    void getCurrentNode() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/learning-path-nodes/current/1").with(withAuth(1L, "ADMIN")))
                .andExpect(status().isOk());
        when(learningPathService.getById(any())).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.get("/learning-path-nodes/current/1").with(withAuth(1L, "STUDENT")))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(MockMvcRequestBuilders.get("/learning-path-nodes/current/1"))
                .andExpect(status().is4xxClientError());
    }
}
