package com.aicourse.controller;
import com.aicourse.controller.LearningPathController;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.LearningPathNodeService;
import com.aicourse.service.LearningPathService;
import com.aicourse.vo.LearningPathDetailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * LearningPathController 接口测试（升级版）。
 * 将「伪覆盖弱断言」改造为真实业务断言：对每个 endpoint 依赖的 service 方法打桩返回合理业务对象，
 * 断言 HTTP 200 + ApiResponse.code=200 + 关键字段透传（jsonPath），并 verify 路由正确委托与参数透传。
 */
@WebMvcTest(LearningPathController.class)
class LearningPathControllerTest extends ControllerTestBase {

    @MockitoBean
    private LearningPathService learningPathService;
    @MockitoBean
    private LearningPathNodeService learningPathNodeService;
    @MockitoBean
    private CourseEnrollmentService enrollmentService;
    @MockitoBean
    private KnowledgePointService knowledgePointService;

    @BeforeEach
    void stubOwnerLookup() {
        // owner 校验依赖 learningPathService.getById 返回归属当前用户(=1L)的路径
        when(learningPathService.getById(any())).thenReturn(samplePath());
    }

    private LearningPath samplePath() {
        LearningPath p = new LearningPath();
        p.setId(1L);
        p.setStudentId(1L);
        return p;
    }

    private LearningPathNode sampleNode() {
        LearningPathNode n = new LearningPathNode();
        n.setId(1L);
        return n;
    }

    @Test
    @DisplayName("GET /learning-paths - 返回200且data为路径列表")
    void myPaths_authorized_returns_success() throws Exception {
        when(learningPathService.list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenReturn(List.of(samplePath()));
        mockMvc.perform(get("/learning-paths").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(learningPathService).list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("GET /learning-paths - 未认证返回401")
    void myPaths_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/learning-paths"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /learning-paths/1 - 返回200且data透传路径id")
    void getById_authorized_returns_success() throws Exception {
        when(learningPathService.getById(any())).thenReturn(samplePath());
        mockMvc.perform(get("/learning-paths/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(learningPathService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /learning-paths/1 - 未认证返回401")
    void getById_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/learning-paths/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /learning-paths - STUDENT角色返回200且正确委托save")
    void create_authorized_returns_success() throws Exception {
        when(learningPathService.save(any())).thenReturn(true);
        mockMvc.perform(post("/learning-paths").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
        verify(learningPathService).save(any(LearningPath.class));
    }

    @Test
    @DisplayName("POST /learning-paths - 未认证返回401")
    void create_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/learning-paths").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /learning-paths - TEACHER角色返回403（仅STUDENT/ADMIN可建）")
    void create_teacher_forbidden_403() throws Exception {
        mockMvc.perform(post("/learning-paths").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /learning-paths/1 - STUDENT角色返回200且data透传更新路径")
    void update_authorized_returns_success() throws Exception {
        when(learningPathService.updateById(any())).thenReturn(true);
        when(learningPathService.getById(any())).thenReturn(samplePath());
        mockMvc.perform(put("/learning-paths/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(learningPathService).updateById(any(LearningPath.class));
        verify(learningPathService).getById(eq(1L));
    }

    @Test
    @DisplayName("PUT /learning-paths/1 - 未认证返回401")
    void update_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/learning-paths/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /learning-paths/1 - TEACHER角色返回403（仅STUDENT/ADMIN可改）")
    void update_teacher_forbidden_403() throws Exception {
        mockMvc.perform(put("/learning-paths/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /learning-paths/1/nodes - 返回200且data为节点列表")
    void getNodes_authorized_returns_success() throws Exception {
        when(learningPathNodeService.list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenReturn(List.of(sampleNode()));
        mockMvc.perform(get("/learning-paths/1/nodes").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(learningPathNodeService).list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("GET /learning-paths/1/nodes - 未认证返回401")
    void getNodes_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/learning-paths/1/nodes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /learning-paths/1/nodes - 返回200且正确委托save")
    void addNode_authorized_returns_success() throws Exception {
        when(learningPathNodeService.save(any())).thenReturn(true);
        mockMvc.perform(post("/learning-paths/1/nodes").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
        verify(learningPathNodeService).save(any(LearningPathNode.class));
    }

    @Test
    @DisplayName("POST /learning-paths/1/nodes - 未认证返回401")
    void addNode_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/learning-paths/1/nodes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /learning-paths/nodes/1 - 返回200且data透传更新节点")
    void updateNode_authorized_returns_success() throws Exception {
        when(learningPathNodeService.updateById(any())).thenReturn(true);
        when(learningPathNodeService.getById(any())).thenReturn(sampleNode());
        mockMvc.perform(put("/learning-paths/nodes/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(learningPathNodeService).updateById(any(LearningPathNode.class));
        verify(learningPathNodeService).getById(eq(1L));
    }

    @Test
    @DisplayName("PUT /learning-paths/nodes/1 - 未认证返回401")
    void updateNode_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/learning-paths/nodes/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /learning-paths/generate?courseId=1 - 返回200且data为节点列表")
    void generate_authorized_returns_success() throws Exception {
        when(learningPathService.generateLearningPath(anyLong(), anyLong()))
                .thenReturn(List.of(new LearningPathNode()));
        mockMvc.perform(get("/learning-paths/generate?courseId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(learningPathService).generateLearningPath(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("GET /learning-paths/generate?courseId=1 - 未认证返回401")
    void generate_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/learning-paths/generate?courseId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /learning-paths/active/1 - 返回200且data透传路径id")
    void getActivePath_authorized_returns_success() throws Exception {
        when(learningPathService.getActivePath(any(), any())).thenReturn(samplePath());
        mockMvc.perform(get("/learning-paths/active/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(learningPathService).getActivePath(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("GET /learning-paths/active/1 - 未认证返回401")
    void getActivePath_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/learning-paths/active/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /learning-paths/1/advance - 返回200且正确委托advanceToNextStage")
    void advanceToNextStage_authorized_returns_success() throws Exception {
        doNothing().when(learningPathService).advanceToNextStage(any());
        mockMvc.perform(post("/learning-paths/1/advance").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(learningPathService).advanceToNextStage(eq(1L));
    }

    @Test
    @DisplayName("POST /learning-paths/1/advance - 未认证返回401")
    void advanceToNextStage_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/learning-paths/1/advance"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /learning-paths/1/complete - 返回200且正确委托completePath")
    void completePath_authorized_returns_success() throws Exception {
        doNothing().when(learningPathService).completePath(any());
        mockMvc.perform(post("/learning-paths/1/complete").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(learningPathService).completePath(eq(1L));
    }

    @Test
    @DisplayName("POST /learning-paths/1/complete - 未认证返回401")
    void completePath_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/learning-paths/1/complete"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /learning-paths/1/pause - 返回200且正确委托pausePath")
    void pausePath_authorized_returns_success() throws Exception {
        doNothing().when(learningPathService).pausePath(any());
        mockMvc.perform(post("/learning-paths/1/pause").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(learningPathService).pausePath(eq(1L));
    }

    @Test
    @DisplayName("POST /learning-paths/1/pause - 未认证返回401")
    void pausePath_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/learning-paths/1/pause"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /learning-paths/1/resume - 返回200且正确委托resumePath")
    void resumePath_authorized_returns_success() throws Exception {
        doNothing().when(learningPathService).resumePath(any());
        mockMvc.perform(post("/learning-paths/1/resume").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(learningPathService).resumePath(eq(1L));
    }

    @Test
    @DisplayName("POST /learning-paths/1/resume - 未认证返回401")
    void resumePath_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/learning-paths/1/resume"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /learning-paths/1/daily-tasks - 返回200且data为数组")
    void getDailyTasks_authorized_returns_success() throws Exception {
        when(learningPathService.getDailyTasks(any())).thenReturn(List.of(Map.of("task", "morning")));
        mockMvc.perform(get("/learning-paths/1/daily-tasks").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(learningPathService).getDailyTasks(eq(1L));
    }

    @Test
    @DisplayName("GET /learning-paths/1/daily-tasks - 未认证返回401")
    void getDailyTasks_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/learning-paths/1/daily-tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /learning-paths/1/detail - 返回200且data透传详情")
    void getPathDetail_authorized_returns_success() throws Exception {
        LearningPath p = new LearningPath();
        p.setId(1L);
        when(learningPathService.getPathDetail(any())).thenReturn(
                new LearningPathDetailVO(p, List.of(), Map.of(), 0, null, null, null, null));
        mockMvc.perform(get("/learning-paths/1/detail").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.path.id").value(1));
        verify(learningPathService).getPathDetail(eq(1L));
    }

    @Test
    @DisplayName("GET /learning-paths/1/detail - 未认证返回401")
    void getPathDetail_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/learning-paths/1/detail"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /learning-paths/1/progress - 返回200且正确委托updateProgress+getPathDetail")
    void getProgress_authorized_returns_success() throws Exception {
        doNothing().when(learningPathService).updateProgress(any());
        LearningPath p = new LearningPath();
        p.setId(1L);
        when(learningPathService.getPathDetail(any())).thenReturn(
                new LearningPathDetailVO(p, List.of(), Map.of(), 0, null, null, null, null));
        mockMvc.perform(get("/learning-paths/1/progress").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.path.id").value(1));
        verify(learningPathService).updateProgress(eq(1L));
        verify(learningPathService).getPathDetail(eq(1L));
    }

    @Test
    @DisplayName("GET /learning-paths/1/progress - 未认证返回401")
    void getProgress_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/learning-paths/1/progress"))
                .andExpect(status().isUnauthorized());
    }
}
