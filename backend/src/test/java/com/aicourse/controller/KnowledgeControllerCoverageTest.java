package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.service.AIService;
import com.aicourse.service.KnowledgeGraphService;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.math.BigDecimal;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖率测试：KnowledgeController（独立 MockMvc，不加载 Spring 上下文）。
 * 覆盖知识点 CRUD、子资源、图谱、关系、搜索、概览，以及 AI 抽取图谱的异步多分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeController 覆盖率测试")
class KnowledgeControllerCoverageTest {

    @Mock
    private KnowledgePointService knowledgePointService;
    @Mock
    private KnowledgePointRelationService relationService;
    @Mock
    private AIService aiService;
    @Mock
    private KnowledgeGraphService knowledgeGraphService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        KnowledgeController controller = new KnowledgeController(knowledgePointService, relationService, aiService, knowledgeGraphService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        KnowledgePoint kp = makePoint(1L, "A");
        KnowledgePointRelation rel = makeRelation(500L, 1L, 2L);

        lenient().when(knowledgePointService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(kp));
        lenient().when(knowledgePointService.page(any(), any())).thenReturn(pageOf(kp));
        lenient().when(knowledgePointService.getById(any())).thenReturn(kp);
        lenient().when(knowledgePointService.save(any())).thenReturn(true);
        lenient().when(knowledgePointService.updateById(any())).thenReturn(true);
        lenient().when(knowledgePointService.removeById(anyLong())).thenReturn(true);
        lenient().when(knowledgePointService.listByIds(any())).thenReturn(List.of(kp));
        lenient().when(knowledgePointService.getPrerequisites(any())).thenReturn(List.of(kp));
        lenient().when(knowledgePointService.getSubsequentPoints(any())).thenReturn(List.of(kp));
        lenient().when(knowledgePointService.getChildPoints(any())).thenReturn(List.of(kp));
        lenient().when(knowledgePointService.calculateMastery(any(), any())).thenReturn(BigDecimal.ONE);
        lenient().when(knowledgePointService.getKnowledgeTree(any())).thenReturn(List.of(Map.of("id", 1)));
        lenient().when(knowledgePointService.getRootPoints(any())).thenReturn(List.of(kp));
        lenient().when(knowledgePointService.count(any())).thenReturn(3L);

        lenient().when(relationService.list()).thenReturn(List.of(rel));
        lenient().when(relationService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(rel));
        lenient().when(relationService.createRelation(any(), any(), any(), any(), any())).thenReturn(rel);
        lenient().doNothing().when(relationService).deleteRelation(any());
        lenient().when(relationService.getRelationList(anyInt(), anyInt(), any())).thenReturn(pageOf(rel));
        lenient().when(relationService.getRelationsByPoint(any())).thenReturn(List.of(rel));
        lenient().when(relationService.getSourceRelations(any())).thenReturn(List.of(rel));
        lenient().when(relationService.getTargetRelations(any())).thenReturn(List.of(rel));
        lenient().when(relationService.getGraphData(any())).thenReturn(Map.of("nodes", 1));
        lenient().when(relationService.count()).thenReturn(5L);

        lenient().when(aiService.extractKnowledgeGraphJson(any())).thenReturn("{}");
    }

    private KnowledgePoint makePoint(Long id, String name) {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setId(id);
        kp.setName(name);
        kp.setPointType("CONCEPT");
        return kp;
    }

    private KnowledgePointRelation makeRelation(Long id, Long s, Long t) {
        KnowledgePointRelation r = new KnowledgePointRelation();
        r.setId(id);
        r.setSourceId(s);
        r.setTargetId(t);
        return r;
    }

    private <T> Page<T> pageOf(T record) {
        Page<T> p = new Page<>(1, 10);
        p.setRecords(List.of(record));
        p.setTotal(1);
        return p;
    }

    // ===================== 知识点 CRUD / 列表 =====================

    @Test
    @DisplayName("知识点列表/分页/详情/CRUD")
    void points() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/points")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/points").param("courseId", "10").param("keyword", "A"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/points/page")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/points/page").param("page", "2").param("size", "5")
                        .param("courseId", "10").param("keyword", "A")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/points/1")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.post("/knowledge/points")
                        .contentType("application/json").content("{\"name\":\"X\",\"pointType\":\"CONCEPT\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.put("/knowledge/points/1")
                        .contentType("application/json").content("{\"name\":\"Y\"}")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.delete("/knowledge/points/1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("知识点子资源")
    void pointSubResources() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/points/1/relations")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/points/1/prerequisites")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/points/1/successors")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/points/1/children")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/points/1/mastery/2")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/points/tree/10")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/points/root/10")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /knowledge/points/graph")
    void graph() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/points/graph").param("courseId", "10"))
                .andExpect(status().isOk());
    }

    // ===================== 关系 =====================

    @Test
    @DisplayName("关系 CRUD / 列表 / 图数据")
    void relations() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/knowledge/relations")
                        .contentType("application/json")
                        .content("{\"sourceId\":1,\"targetId\":2,\"relationType\":\"RELATED_TO\",\"strength\":0.8,\"description\":\"d\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.delete("/knowledge/relations/500")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/relations")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/relations").param("page", "2").param("size", "5")
                        .param("courseId", "10")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/relations/point/1")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/relations/source/1")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/relations/target/2")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/relations/graph/10")).andExpect(status().isOk());
    }

    // ===================== 查询与概览 =====================

    @Test
    @DisplayName("搜索 / 概览")
    void searchAndOverview() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/search").param("keyword", "java")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/search").param("keyword", "java").param("courseId", "10"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/overview")).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/knowledge/overview").param("courseId", "10")).andExpect(status().isOk());
    }

    // ===================== 学习路径规划 =====================

    @Test
    @DisplayName("POST /knowledge/path/plan 找到路径 -> 200")
    void planPathFound() throws Exception {
        com.aicourse.vo.KnowledgePathPlanVO vo = new com.aicourse.vo.KnowledgePathPlanVO();
        vo.setSourceId(1L);
        vo.setTargetId(2L);
        vo.setFound(true);
        vo.setHops(1);
        vo.setMode("LOCAL");
        vo.setPath(List.of(makePoint(1L, "A"), makePoint(2L, "B")));
        vo.setEdges(List.of(makeRelation(500L, 1L, 2L)));
        when(knowledgeGraphService.planLearningPath(eq(1L), eq(2L), anyInt())).thenReturn(vo);

        mockMvc.perform(MockMvcRequestBuilders.post("/knowledge/path/plan")
                        .contentType("application/json").content("{\"sourceId\":1,\"targetId\":2}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /knowledge/path/plan 未找到路径 -> 404")
    void planPathNotFound() throws Exception {
        com.aicourse.vo.KnowledgePathPlanVO vo = new com.aicourse.vo.KnowledgePathPlanVO();
        vo.setSourceId(1L);
        vo.setTargetId(99L);
        vo.setFound(false);
        vo.setMode("LOCAL");
        vo.setNote("在 2 跳范围内未找到从起点到目标的可达路径");
        when(knowledgeGraphService.planLearningPath(eq(1L), eq(99L), anyInt())).thenReturn(vo);

        mockMvc.perform(MockMvcRequestBuilders.post("/knowledge/path/plan")
                        .contentType("application/json").content("{\"sourceId\":1,\"targetId\":99}"))
                .andExpect(status().isNotFound());
    }

    // ===================== AI 抽取图谱（异步） =====================

    @Test
    @DisplayName("POST /knowledge/extract 内容为空 -> 直接报错")
    void extractBlank() throws Exception {
        String body = "{\"courseId\":1,\"content\":\"   \"}";
        MvcResult mvc = mockMvc.perform(MockMvcRequestBuilders.post("/knowledge/extract")
                        .contentType("application/json").content(body))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(mvc)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /knowledge/extract 正常解析（节点跳过/创建/类型兜底 + 关系创建）")
    void extractNormal() throws Exception {
        // 已存在同名节点 A -> 跳过；B 新建；空白名跳过；关系 A->B 创建
        KnowledgePoint existing = makePoint(200L, "A");
        when(knowledgePointService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(existing));
        doAnswer(inv -> {
            KnowledgePoint kp = inv.getArgument(0);
            kp.setId(101L);
            return true;
        }).when(knowledgePointService).save(any());

        String json = "{"
                + "\"nodes\":["
                + "{\"name\":\"A\",\"content\":\"cA\",\"type\":\"CONCEPT\"},"
                + "{\"name\":\"B\",\"content\":\"cB\",\"type\":\"WEIRD\"},"
                + "{\"name\":\"\",\"type\":\"CONCEPT\"}"
                + "],"
                + "\"relations\":[{\"source\":\"A\",\"target\":\"B\",\"relationType\":\"RELATED_TO\"}]"
                + "}";
        when(aiService.extractKnowledgeGraphJson(any())).thenReturn(json);

        String body = "{\"courseId\":1,\"content\":\"some content\"}";
        MvcResult mvc = mockMvc.perform(MockMvcRequestBuilders.post("/knowledge/extract")
                        .contentType("application/json").content(body))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(mvc)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /knowledge/extract JSON 解析失败 -> 解析异常分支")
    void extractParseError() throws Exception {
        when(aiService.extractKnowledgeGraphJson(any())).thenReturn("not a json at all");
        String body = "{\"courseId\":1,\"content\":\"some content\"}";
        MvcResult mvc = mockMvc.perform(MockMvcRequestBuilders.post("/knowledge/extract")
                        .contentType("application/json").content(body))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(mvc)).andExpect(status().isOk());
    }
}
