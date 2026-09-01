package com.aicourse.controller;
import com.aicourse.controller.KnowledgeController;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.vo.KnowledgeTreeNodeVO;
import com.aicourse.service.AIService;
import com.aicourse.service.KnowledgeGraphService;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.vo.KnowledgeGraphDataVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * KnowledgeController 接口测试（升级版）。
 * 将「伪覆盖弱断言」改造为真实业务断言：对每个 endpoint 依赖的 service 方法打桩返回合理业务对象，
 * 断言 HTTP 200 + ApiResponse.code=200 + 关键字段透传（jsonPath），并 verify 路由正确委托与参数透传。
 */
@WebMvcTest(KnowledgeController.class)
class KnowledgeControllerTest extends ControllerTestBase {

    @MockitoBean
    private KnowledgePointService knowledgePointService;
    @MockitoBean
    private KnowledgePointRelationService relationService;
    @MockitoBean
    private AIService aiService;
    @MockitoBean
    private KnowledgeGraphService knowledgeGraphService;

    private KnowledgePoint samplePoint() {
        KnowledgePoint p = new KnowledgePoint();
        p.setId(1L);
        p.setName("sample-point");
        return p;
    }

    private KnowledgePointRelation sampleRelation() {
        KnowledgePointRelation r = new KnowledgePointRelation();
        r.setId(1L);
        return r;
    }

    @Test
    @DisplayName("GET /knowledge/points?courseId=1&keyword=test - 返回200且data为知识点列表")
    void listPoints_authorized_returns_success() throws Exception {
        when(knowledgePointService.list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenReturn(List.of(samplePoint()));
        mockMvc.perform(get("/knowledge/points?courseId=1&keyword=test").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(knowledgePointService).list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("GET /knowledge/points?courseId=1&keyword=test - 未认证返回401")
    void listPoints_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/points?courseId=1&keyword=test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /knowledge/points/1 - 返回200且data透传知识点id")
    void getPointDetail_authorized_returns_success() throws Exception {
        when(knowledgePointService.getById(any())).thenReturn(samplePoint());
        mockMvc.perform(get("/knowledge/points/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(knowledgePointService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /knowledge/points/1 - 未认证返回401")
    void getPointDetail_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/points/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /knowledge/points - TEACHER角色返回200且data透传创建对象")
    void createPoint_authorized_returns_success() throws Exception {
        when(knowledgePointService.save(any())).thenReturn(true);
        mockMvc.perform(post("/knowledge/points").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"知识点\"}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
        verify(knowledgePointService).save(any(KnowledgePoint.class));
    }

    @Test
    @DisplayName("POST /knowledge/points - 未认证返回401")
    void createPoint_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/knowledge/points").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /knowledge/points - STUDENT角色返回403")
    void createPoint_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/knowledge/points").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /knowledge/points/1 - TEACHER角色返回200且data透传更新后id")
    void updatePoint_authorized_returns_success() throws Exception {
        when(knowledgePointService.updateById(any())).thenReturn(true);
        when(knowledgePointService.getById(any())).thenReturn(samplePoint());
        mockMvc.perform(put("/knowledge/points/1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"知识点\"}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(knowledgePointService).updateById(any(KnowledgePoint.class));
        verify(knowledgePointService).getById(eq(1L));
    }

    @Test
    @DisplayName("PUT /knowledge/points/1 - 未认证返回401")
    void updatePoint_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/knowledge/points/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /knowledge/points/1 - STUDENT角色返回403")
    void updatePoint_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/knowledge/points/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /knowledge/points/1 - TEACHER角色返回200且正确委托removeById")
    void deletePoint_authorized_returns_success() throws Exception {
        when(knowledgePointService.removeById(any(Long.class))).thenReturn(true);
        mockMvc.perform(delete("/knowledge/points/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(knowledgePointService).removeById(eq(1L));
    }

    @Test
    @DisplayName("DELETE /knowledge/points/1 - 未认证返回401")
    void deletePoint_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/knowledge/points/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /knowledge/points/1 - STUDENT角色返回403")
    void deletePoint_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/knowledge/points/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /knowledge/points/1/relations - 返回200且data含nodes/edges数组")
    void getPointRelations_authorized_returns_success() throws Exception {
        when(relationService.list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenReturn(List.of(sampleRelation()));
        when(knowledgePointService.listByIds(any())).thenReturn(List.of(samplePoint()));
        mockMvc.perform(get("/knowledge/points/1/relations").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nodes").isArray())
                .andExpect(jsonPath("$.data.edges").isArray());
        verify(relationService).list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("GET /knowledge/points/1/relations - 未认证返回401")
    void getPointRelations_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/points/1/relations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /knowledge/points/1/prerequisites - 返回200且data为列表")
    void getPrerequisites_authorized_returns_success() throws Exception {
        when(knowledgePointService.getPrerequisites(any())).thenReturn(List.of(samplePoint()));
        mockMvc.perform(get("/knowledge/points/1/prerequisites").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(knowledgePointService).getPrerequisites(eq(1L));
    }

    @Test
    @DisplayName("GET /knowledge/points/1/prerequisites - 未认证返回401")
    void getPrerequisites_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/points/1/prerequisites"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /knowledge/points/1/successors - 返回200且data为列表")
    void getSubsequentPoints_authorized_returns_success() throws Exception {
        when(knowledgePointService.getSubsequentPoints(any())).thenReturn(List.of(samplePoint()));
        mockMvc.perform(get("/knowledge/points/1/successors").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(knowledgePointService).getSubsequentPoints(eq(1L));
    }

    @Test
    @DisplayName("GET /knowledge/points/1/successors - 未认证返回401")
    void getSubsequentPoints_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/points/1/successors"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /knowledge/points/1/children - 返回200且data为列表")
    void getChildPoints_authorized_returns_success() throws Exception {
        when(knowledgePointService.getChildPoints(any())).thenReturn(List.of(samplePoint()));
        mockMvc.perform(get("/knowledge/points/1/children").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(knowledgePointService).getChildPoints(eq(1L));
    }

    @Test
    @DisplayName("GET /knowledge/points/1/children - 未认证返回401")
    void getChildPoints_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/points/1/children"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /knowledge/points/1/mastery/1 - 返回200且data透传掌握度")
    void calculateMastery_authorized_returns_success() throws Exception {
        when(knowledgePointService.calculateMastery(any(), any())).thenReturn(BigDecimal.valueOf(0.85));
        mockMvc.perform(get("/knowledge/points/1/mastery/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(0.85));
        verify(knowledgePointService).calculateMastery(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("GET /knowledge/points/1/mastery/1 - 未认证返回401")
    void calculateMastery_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/points/1/mastery/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /knowledge/points/tree/1 - 返回200且data为树形列表")
    void getKnowledgeTree_authorized_returns_success() throws Exception {
        when(knowledgePointService.getKnowledgeTree(any())).thenReturn(
                List.of(new KnowledgeTreeNodeVO(1L, "root", null, null, null, null, null, null, List.of())));
        mockMvc.perform(get("/knowledge/points/tree/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(knowledgePointService).getKnowledgeTree(eq(1L));
    }

    @Test
    @DisplayName("GET /knowledge/points/tree/1 - 未认证返回401")
    void getKnowledgeTree_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/points/tree/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /knowledge/points/root/1 - 返回200且data为列表")
    void getRootPoints_authorized_returns_success() throws Exception {
        when(knowledgePointService.getRootPoints(any())).thenReturn(List.of(samplePoint()));
        mockMvc.perform(get("/knowledge/points/root/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(knowledgePointService).getRootPoints(eq(1L));
    }

    @Test
    @DisplayName("GET /knowledge/points/root/1 - 未认证返回401")
    void getRootPoints_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/points/root/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /knowledge/points/graph?courseId=1 - 返回200且data含nodes/totalPoints")
    void getGraph_authorized_returns_success() throws Exception {
        when(knowledgePointService.list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenReturn(List.of(samplePoint()));
        when(relationService.list()).thenReturn(List.of(sampleRelation()));
        mockMvc.perform(get("/knowledge/points/graph?courseId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nodes").isArray())
                .andExpect(jsonPath("$.data.totalPoints").value(1));
        verify(knowledgePointService).list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
        verify(relationService).list();
    }

    @Test
    @DisplayName("GET /knowledge/points/graph?courseId=1 - 未认证返回401")
    void getGraph_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/points/graph?courseId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /knowledge/relations - TEACHER角色返回200且data透传id")
    void createRelation_authorized_returns_success() throws Exception {
        when(relationService.createRelation(any(), any(), any(), any(), any())).thenReturn(sampleRelation());
        mockMvc.perform(post("/knowledge/relations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":1,\"targetId\":2,\"relationType\":\"RELATED_TO\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(relationService).createRelation(eq(1L), eq(2L), eq("RELATED_TO"), any(), any());
    }

    @Test
    @DisplayName("POST /knowledge/relations - 未认证返回401")
    void createRelation_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/knowledge/relations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /knowledge/relations - STUDENT角色返回403")
    void createRelation_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/knowledge/relations").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /knowledge/relations/1 - TEACHER角色返回200且正确委托deleteRelation")
    void deleteRelation_authorized_returns_success() throws Exception {
        doNothing().when(relationService).deleteRelation(any());
        mockMvc.perform(delete("/knowledge/relations/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(relationService).deleteRelation(eq(1L));
    }

    @Test
    @DisplayName("DELETE /knowledge/relations/1 - 未认证返回401")
    void deleteRelation_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/knowledge/relations/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /knowledge/relations/1 - STUDENT角色返回403")
    void deleteRelation_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/knowledge/relations/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /knowledge/relations?page=1&size=1&courseId=1 - 返回200且data.list为数组")
    void getRelationList_authorized_returns_success() throws Exception {
        when(relationService.getRelationList(anyInt(), anyInt(), any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());
        mockMvc.perform(get("/knowledge/relations?page=1&size=1&courseId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
        verify(relationService).getRelationList(eq(1), eq(1), eq(1L));
    }

    @Test
    @DisplayName("GET /knowledge/relations?page=1&size=1&courseId=1 - 未认证返回401")
    void getRelationList_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/relations?page=1&size=1&courseId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /knowledge/relations/point/1 - 返回200且data为列表")
    void getRelationsByPoint_authorized_returns_success() throws Exception {
        when(relationService.getRelationsByPoint(any())).thenReturn(List.of(sampleRelation()));
        mockMvc.perform(get("/knowledge/relations/point/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(relationService).getRelationsByPoint(eq(1L));
    }

    @Test
    @DisplayName("GET /knowledge/relations/point/1 - 未认证返回401")
    void getRelationsByPoint_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/relations/point/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /knowledge/relations/source/1 - 返回200且data为列表")
    void getSourceRelations_authorized_returns_success() throws Exception {
        when(relationService.getSourceRelations(any())).thenReturn(List.of(sampleRelation()));
        mockMvc.perform(get("/knowledge/relations/source/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(relationService).getSourceRelations(eq(1L));
    }

    @Test
    @DisplayName("GET /knowledge/relations/source/1 - 未认证返回401")
    void getSourceRelations_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/relations/source/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /knowledge/relations/target/1 - 返回200且data为列表")
    void getTargetRelations_authorized_returns_success() throws Exception {
        when(relationService.getTargetRelations(any())).thenReturn(List.of(sampleRelation()));
        mockMvc.perform(get("/knowledge/relations/target/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(relationService).getTargetRelations(eq(1L));
    }

    @Test
    @DisplayName("GET /knowledge/relations/target/1 - 未认证返回401")
    void getTargetRelations_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/relations/target/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /knowledge/relations/graph/1 - 返回200且data为图数据")
    void getRelationGraphData_authorized_returns_success() throws Exception {
        when(relationService.getGraphData(any())).thenReturn(new KnowledgeGraphDataVO(List.of(), List.of()));
        mockMvc.perform(get("/knowledge/relations/graph/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nodes").isArray());
        verify(relationService).getGraphData(eq(1L));
    }

    @Test
    @DisplayName("GET /knowledge/relations/graph/1 - 未认证返回401")
    void getRelationGraphData_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/relations/graph/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /knowledge/search?keyword=test&courseId=1 - 返回200且data为列表")
    void search_authorized_returns_success() throws Exception {
        when(knowledgePointService.list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenReturn(List.of(samplePoint()));
        mockMvc.perform(get("/knowledge/search?keyword=test&courseId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(knowledgePointService).list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("GET /knowledge/search?keyword=test&courseId=1 - 未认证返回401")
    void search_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/search?keyword=test&courseId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /knowledge/overview?courseId=1 - 返回200且data透传统计")
    void overview_authorized_returns_success() throws Exception {
        when(knowledgePointService.count(any())).thenReturn(5L);
        when(relationService.count()).thenReturn(3L);
        mockMvc.perform(get("/knowledge/overview?courseId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalPoints").value(5))
                .andExpect(jsonPath("$.data.totalRelations").value(3));
        verify(knowledgePointService).count(any());
        verify(relationService).count();
    }

    @Test
    @DisplayName("GET /knowledge/overview?courseId=1 - 未认证返回401")
    void overview_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/knowledge/overview?courseId=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /knowledge/path/plan - 找到路径返回200且data透传")
    void planLearningPath_found_returns_success() throws Exception {
        com.aicourse.vo.KnowledgePathPlanVO vo = new com.aicourse.vo.KnowledgePathPlanVO();
        vo.setSourceId(1L);
        vo.setTargetId(2L);
        vo.setFound(true);
        vo.setHops(1);
        vo.setMode("LOCAL");
        vo.setPath(List.of(samplePoint()));
        vo.setEdges(List.of(sampleRelation()));
        when(knowledgeGraphService.planLearningPath(eq(1L), eq(2L), anyInt())).thenReturn(vo);
        mockMvc.perform(post("/knowledge/path/plan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":1,\"targetId\":2}").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.found").value(true))
                .andExpect(jsonPath("$.data.hops").value(1));
        verify(knowledgeGraphService).planLearningPath(eq(1L), eq(2L), anyInt());
    }

    @Test
    @DisplayName("POST /knowledge/path/plan - 未找到路径返回404")
    void planLearningPath_notFound_returns_404() throws Exception {
        com.aicourse.vo.KnowledgePathPlanVO vo = new com.aicourse.vo.KnowledgePathPlanVO();
        vo.setSourceId(1L);
        vo.setTargetId(99L);
        vo.setFound(false);
        vo.setMode("LOCAL");
        vo.setNote("在 2 跳范围内未找到从起点到目标的可达路径");
        when(knowledgeGraphService.planLearningPath(eq(1L), eq(99L), anyInt())).thenReturn(vo);
        mockMvc.perform(post("/knowledge/path/plan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":1,\"targetId\":99}").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("POST /knowledge/extract - TEACHER角色返回200且委托AI抽取")
    void extractGraph_authorized_returns_success() throws Exception {
        when(aiService.extractKnowledgeGraphJson(any())).thenReturn("{}");
        when(knowledgePointService.list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenReturn(List.of());
        when(relationService.list()).thenReturn(List.of());
        MvcResult result = mockMvc.perform(post("/knowledge/extract").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"content\":\"some text\"}").with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(aiService).extractKnowledgeGraphJson(any());
    }

    @Test
    @DisplayName("POST /knowledge/extract - 未认证返回401")
    void extractGraph_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/knowledge/extract").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /knowledge/extract - STUDENT角色返回403")
    void extractGraph_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/knowledge/extract").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

}
