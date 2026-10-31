package com.aicourse.controller;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aicourse.service.AIService;
import com.aicourse.service.KnowledgeGraphService;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.vo.KnowledgeGraphDataVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KnowledgeController 分支/长方法补测（覆盖率门禁专项）。
 *
 * <p>与 {@link KnowledgeControllerTest} 的分工：那份用例只走「每个端点各一次正常返回 + 401 + 部分 403」，
 * 因此本控制器里三块真正的分支密集区没被进到：
 * <ol>
 *     <li>{@code extractGraph} 的异步 Callable 主体（AI 返回结构的逐节点/逐关系解析、
 *         去重、题型白名单、单条关系失败的内部 catch、整体解析失败的对外降级）；</li>
 *     <li>{@code parseKgJson}（ingest / subgraph 共用）的四类降级：空返回、503 占位、
 *         JSON 解析异常、NullNode 转 Map 得 null；</li>
 *     <li>{@code listPointsPaged} 整个方法（size/page 双端钳位）、{@code overview} 的全平台口径与
 *         空集合分支、{@code calculateMastery} 的 {@code #userId == authentication.principal} 本人分支。</li>
 * </ol>
 *
 * <p>条件过滤（keyword 空白即不拼 LIKE）用「触发 getSqlSegment() 后读 wrapper 参数」实证，
 * 而不是只看返回码——否则把 {@code like(cond, ...)} 的条件写反也能过。
 */
@WebMvcTest(KnowledgeController.class)
class KnowledgeControllerCoverageTest extends ControllerTestBase {

    @MockitoBean private KnowledgePointService knowledgePointService;
    @MockitoBean private KnowledgePointRelationService relationService;
    @MockitoBean private AIService aiService;
    @MockitoBean private KnowledgeGraphService knowledgeGraphService;
    @MockitoBean private com.aicourse.security.CourseAccessControl courseAccessControl;

    @BeforeEach
    void registerEntityMetadata() {
        // LambdaQueryWrapper 的列名与参数在 getSqlSegment() 时才落地，需先注册实体元数据，
        // 否则下面 wrapperParams(...) 读不到 where 条件值。
        for (Class<?> entity : new Class<?>[]{KnowledgePoint.class, KnowledgePointRelation.class}) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entity);
        }
    }

    private static KnowledgePoint point(Long id, String name) {
        KnowledgePoint p = new KnowledgePoint();
        p.setId(id);
        p.setName(name);
        p.setCourseId(1L);
        return p;
    }

    private static KnowledgePointRelation relation(Long id, Long sourceId, Long targetId) {
        KnowledgePointRelation r = new KnowledgePointRelation();
        r.setId(id);
        r.setSourceId(sourceId);
        r.setTargetId(targetId);
        r.setRelationType("PREREQUISITE");
        return r;
    }

    /** 触发 wrapper 生成 SQL 片段并取出参数值（等价于真实 SQL 的过滤条件）。 */
    private static Collection<Object> wrapperParams(Wrapper<?> wrapper) {
        assertTrue(wrapper != null, "查询条件 wrapper 不得为 null");
        AbstractWrapper<?, ?, ?> aw = (AbstractWrapper<?, ?, ?>) wrapper;
        aw.getSqlSegment();
        return aw.getParamNameValuePairs().values();
    }

    /** 构造一个既非空也非「无过滤条件」的分页结果，供 page(...) 桩返回。 */
    private Page<KnowledgePoint> knowledgePage(List<KnowledgePoint> records) {
        Page<KnowledgePoint> p = new Page<>();
        p.setRecords(records);
        p.setTotal(records.size());
        return p;
    }

    /**
     * 模拟真实 MyBatis-Plus 分页：把控制器传入的 page/size 原样回填到结果页，
     * 这样响应信封里的 page/pageSize 就能实证「钳位后的值确实下传给了分页查询」，
     * 而不是桩对象的默认值。
     */
    private Page<KnowledgePoint> echoedPage(Page<KnowledgePoint> sent, List<KnowledgePoint> records) {
        Page<KnowledgePoint> p = knowledgePage(records);
        p.setCurrent(sent.getCurrent());
        p.setSize(sent.getSize());
        return p;
    }

    // ==================== GET /knowledge/points：可选过滤条件 ====================

    @Test
    @DisplayName("GET /knowledge/points - 不带任何过滤条件：wrapper 不产生任何 where 参数")
    void listPoints_withoutFilters_buildsEmptyWrapper() throws Exception {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of(point(1L, "栈")));

        mockMvc.perform(get("/knowledge/points").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("栈"));

        ArgumentCaptor<Wrapper<KnowledgePoint>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(knowledgePointService).list(cap.capture());
        assertTrue(wrapperParams(cap.getValue()).isEmpty(),
                "无 courseId/keyword 时不应拼出任何条件，实际=" + wrapperParams(cap.getValue()));
    }

    @Test
    @DisplayName("GET /knowledge/points - keyword 为空串：不拼 LIKE（空关键词等于全量）")
    void listPoints_blankKeyword_doesNotAddLike() throws Exception {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());

        mockMvc.perform(get("/knowledge/points").param("keyword", "").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        ArgumentCaptor<Wrapper<KnowledgePoint>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(knowledgePointService).list(cap.capture());
        assertTrue(wrapperParams(cap.getValue()).isEmpty(),
                "keyword 为空串时必须短路掉 like，实际=" + wrapperParams(cap.getValue()));
    }

    @Test
    @DisplayName("GET /knowledge/points - keyword 非空：按 courseId 等值 + name 模糊组合过滤")
    void listPoints_withCourseAndKeyword_appliesBothConditions() throws Exception {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());

        mockMvc.perform(get("/knowledge/points").param("courseId", "7").param("keyword", "栈")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Wrapper<KnowledgePoint>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(knowledgePointService).list(cap.capture());
        Collection<Object> params = wrapperParams(cap.getValue());
        assertTrue(params.contains(7L), "courseId 等值条件须存在，实际=" + params);
        assertTrue(params.stream().anyMatch(v -> String.valueOf(v).contains("栈")),
                "keyword 须作为 LIKE 参数（可能带 % 包裹），实际=" + params);
    }

    // ==================== GET /knowledge/points/page：size/page 双端钳位（整个方法此前未测） ====================

    @Test
    @DisplayName("GET /knowledge/points/page - size 超上限：钳到 200，页码原样透传")
    void listPointsPaged_sizeAboveCap_clampedTo200() throws Exception {
        AtomicReference<Page<KnowledgePoint>> sent = new AtomicReference<>();
        when(knowledgePointService.page(any(), any())).thenAnswer(inv -> {
            Page<KnowledgePoint> sentPage = inv.getArgument(0);
            sent.set(sentPage);
            return echoedPage(sentPage, List.of(point(1L, "栈")));
        });

        mockMvc.perform(get("/knowledge/points/page").param("page", "3").param("size", "5000")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.page").value(3))
                .andExpect(jsonPath("$.data.pageSize").value(200));

        assertEquals(3L, sent.get().getCurrent(), "页码在合法区间内应原样下传");
        assertEquals(200L, sent.get().getSize(), "单页条数必须钳到上限 200，防止一次拉穿内存");
    }

    @Test
    @DisplayName("GET /knowledge/points/page - 非法页码与条数（0/负数）：双双钳回 1")
    void listPointsPaged_nonPositiveParams_clampedToOne() throws Exception {
        AtomicReference<Page<KnowledgePoint>> sent = new AtomicReference<>();
        when(knowledgePointService.page(any(), any())).thenAnswer(inv -> {
            Page<KnowledgePoint> sentPage = inv.getArgument(0);
            sent.set(sentPage);
            return echoedPage(sentPage, List.of());
        });

        mockMvc.perform(get("/knowledge/points/page").param("page", "-2").param("size", "0")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(1));

        assertEquals(1L, sent.get().getCurrent(), "page<=0 须钳为 1，否则 MyBatis-Plus 分页会算出负偏移");
        assertEquals(1L, sent.get().getSize(), "size<=0 须钳为 1");
    }

    @Test
    @DisplayName("GET /knowledge/points/page - 缺省参数：page=1/size=10，且空 keyword 不拼 LIKE")
    void listPointsPaged_defaults_noKeywordCondition() throws Exception {
        AtomicReference<Page<KnowledgePoint>> sent = new AtomicReference<>();
        AtomicReference<Wrapper<KnowledgePoint>> sentQw = new AtomicReference<>();
        when(knowledgePointService.page(any(), any())).thenAnswer(inv -> {
            sent.set(inv.getArgument(0));
            sentQw.set(inv.getArgument(1));
            return knowledgePage(List.of());
        });

        mockMvc.perform(get("/knowledge/points/page").param("keyword", "").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(1L, sent.get().getCurrent());
        assertEquals(10L, sent.get().getSize());
        assertTrue(wrapperParams(sentQw.get()).isEmpty(),
                "keyword 空白 + 无 courseId 时不该有 where 参数，实际=" + wrapperParams(sentQw.get()));
    }

    @Test
    @DisplayName("GET /knowledge/points/page - courseId + 非空 keyword：两个条件都拼进分页查询")
    void listPointsPaged_withCourseAndKeyword_appliesBothConditions() throws Exception {
        AtomicReference<Wrapper<KnowledgePoint>> sentQw = new AtomicReference<>();
        when(knowledgePointService.page(any(), any())).thenAnswer(inv -> {
            sentQw.set(inv.getArgument(1));
            return knowledgePage(List.of(point(1L, "栈与队列")));
        });

        mockMvc.perform(get("/knowledge/points/page").param("courseId", "9").param("keyword", "栈")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(1));

        Collection<Object> params = wrapperParams(sentQw.get());
        assertTrue(params.contains(9L), "分页查询同样须带 courseId 等值条件，实际=" + params);
        assertTrue(params.stream().anyMatch(v -> String.valueOf(v).contains("栈")),
                "非空 keyword 须作为 LIKE 参数下传，实际=" + params);
    }

    // ==================== GET /knowledge/search：仅关键词的检索口径 ====================

    @Test
    @DisplayName("GET /knowledge/search - 不带 courseId：仅按名称模糊，不加课程等值条件")
    void search_withoutCourseId_onlyKeywordCondition() throws Exception {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of(point(1L, "栈")));

        mockMvc.perform(get("/knowledge/search").param("keyword", "栈").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));

        ArgumentCaptor<Wrapper<KnowledgePoint>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(knowledgePointService).list(cap.capture());
        Collection<Object> params = wrapperParams(cap.getValue());
        assertEquals(1, params.size(), "只应有一个 LIKE 参数，实际=" + params);
        assertTrue(params.stream().anyMatch(v -> String.valueOf(v).contains("栈")),
                "关键词须作为 LIKE 参数，实际=" + params);
    }

    // ==================== 权限：掌握度的「本人或教师/管理员」SpEL 分支 ====================

    @Test
    @DisplayName("GET /knowledge/points/{id}/mastery/{userId} - 学生查自己：SpEL 本人分支放行")
    void calculateMastery_studentSelfAllowedByPrincipalMatch() throws Exception {
        when(knowledgePointService.calculateMastery(eq(11L), eq(2L))).thenReturn(new BigDecimal("0.72"));

        mockMvc.perform(get("/knowledge/points/11/mastery/2").with(withUser(2L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(0.72));
        verify(knowledgePointService).calculateMastery(11L, 2L);
    }

    @Test
    @DisplayName("GET /knowledge/points/{id}/mastery/{userId} - 学生查他人掌握度：403 且不进控制器")
    void calculateMastery_studentOtherForbidden() throws Exception {
        mockMvc.perform(get("/knowledge/points/11/mastery/999").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(knowledgePointService, never()).calculateMastery(anyLong(), anyLong());
    }

    // ==================== GET /knowledge/points/{id}/relations：节点集合去重 ====================

    @Test
    @DisplayName("GET /knowledge/points/{id}/relations - 多条关系：两端 ID 合并去重后一次查节点")
    void getPointRelations_collectsBothEndpointsInOneBatch() throws Exception {
        when(relationService.list(any(Wrapper.class))).thenReturn(List.of(
                relation(1L, 10L, 20L), relation(2L, 10L, 30L)));
        when(knowledgePointService.listByIds(any())).thenReturn(
                List.of(point(10L, "甲"), point(20L, "乙"), point(30L, "丙")));

        mockMvc.perform(get("/knowledge/points/10/relations").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.edges.length()").value(2))
                .andExpect(jsonPath("$.data.nodes.length()").value(3));

        ArgumentCaptor<Collection<Long>> cap = ArgumentCaptor.forClass(Collection.class);
        verify(knowledgePointService, times(1)).listByIds(cap.capture());
        Collection<Long> ids = cap.getValue();
        // 10 出现两次（两条关系的源），必须去重为 3 个 ID；漏掉 targetId 会让图谱缺一半节点
        assertEquals(3, ids.size(), "节点 ID 应去重，实际=" + ids);
        assertTrue(ids.containsAll(List.of(10L, 20L, 30L)), "两端 ID 都要收集，实际=" + ids);
    }

    // ==================== GET /knowledge/overview：统计口径分支 ====================

    @Test
    @DisplayName("GET /knowledge/overview - 不传 courseId：走全平台口径（无 where 的 list()）")
    void overview_withoutCourseId_usesPlatformScope() throws Exception {
        when(knowledgePointService.list()).thenReturn(List.of(point(1L, "甲"), point(2L, "乙")));
        when(relationService.list(any(Wrapper.class))).thenReturn(List.of(relation(9L, 1L, 2L)));

        mockMvc.perform(get("/knowledge/overview").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPoints").value(2))
                .andExpect(jsonPath("$.data.totalRelations").value(1))
                .andExpect(jsonPath("$.data.coverageRate").value(1.0))
                // 全局 jackson default-property-inclusion=non_null：全平台口径的 courseId 为 null，键直接缺席
                .andExpect(jsonPath("$.data.courseId").doesNotExist());
        // 全平台口径不得再走带条件的 list(Wrapper)
        verify(knowledgePointService, times(1)).list();
        verify(knowledgePointService, never()).list(any(Wrapper.class));
    }

    @Test
    @DisplayName("GET /knowledge/overview - 无任何知识点：关系表不查、覆盖度 0 而非除零")
    void overview_noPoints_skipsRelationQueryAndZeroCoverage() throws Exception {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());

        mockMvc.perform(get("/knowledge/overview").param("courseId", "3").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPoints").value(0))
                .andExpect(jsonPath("$.data.totalRelations").value(0))
                .andExpect(jsonPath("$.data.corePoints").value(0))
                .andExpect(jsonPath("$.data.coverageRate").value(0.0))
                .andExpect(jsonPath("$.data.courseId").value(3));
        verify(relationService, never()).list(any(Wrapper.class));
    }

    @Test
    @DisplayName("GET /knowledge/overview - 重要度非空但低于阈值：不计入核心知识点")
    void overview_importanceBelowThreshold_notCore() throws Exception {
        KnowledgePoint low = point(1L, "低重要度");
        low.setImportance(7);
        KnowledgePoint nullImportance = point(2L, "未标注");
        nullImportance.setImportance(null);
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of(low, nullImportance));
        when(relationService.list(any(Wrapper.class))).thenReturn(List.of());

        mockMvc.perform(get("/knowledge/overview").param("courseId", "1").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPoints").value(2))
                .andExpect(jsonPath("$.data.totalRelations").value(0))
                .andExpect(jsonPath("$.data.corePoints").value(0))
                // 有关系才会覆盖；无关系时覆盖度为 0
                .andExpect(jsonPath("$.data.coverageRate").value(0.0));
    }

    @Test
    @DisplayName("GET /knowledge/overview - 重要度达阈值：计入核心知识点")
    void overview_importanceAtOrAboveThreshold_countsAsCore() throws Exception {
        KnowledgePoint core = point(1L, "核心");
        core.setImportance(8);
        KnowledgePoint aboveCore = point(2L, "超核心");
        aboveCore.setImportance(10);
        KnowledgePoint low = point(3L, "次要");
        low.setImportance(1);
        KnowledgePoint edge = point(4L, "边缘");
        edge.setImportance(7);
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of(core, aboveCore, low, edge));
        when(relationService.list(any(Wrapper.class))).thenReturn(List.of(relation(9L, 1L, 2L)));

        mockMvc.perform(get("/knowledge/overview").param("courseId", "1").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPoints").value(4))
                .andExpect(jsonPath("$.data.totalRelations").value(1))
                // 阈值 8 为「不低于」：8 与 10 计入，1/7 不计入
                .andExpect(jsonPath("$.data.corePoints").value(2))
                .andExpect(jsonPath("$.data.coverageRate").value(0.5))
                .andExpect(jsonPath("$.data.courseId").value(1));
    }

    // ==================== POST /knowledge/path/plan：maxHops 与提示语分支 ====================

    @Test
    @DisplayName("POST /knowledge/path/plan - 学生被 SecurityConfig 的 POST /knowledge/** 写保护挡在 403")
    void planLearningPath_studentForbiddenByUrlRule() throws Exception {
        mockMvc.perform(post("/knowledge/path/plan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":1,\"targetId\":2}")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        // 路由层拦下后不得触达服务层
        verify(knowledgeGraphService, never()).planLearningPath(any(), any(), anyInt());
    }

    @Test
    @DisplayName("POST /knowledge/path/plan - 传 maxHops 且服务层未给 note：使用内置默认提示，404")
    void planLearningPath_withMaxHopsAndNullNote_usesDefaultMessage() throws Exception {
        com.aicourse.vo.KnowledgePathPlanVO vo = new com.aicourse.vo.KnowledgePathPlanVO();
        vo.setSourceId(1L);
        vo.setTargetId(2L);
        vo.setFound(false);
        vo.setMode("LOCAL");
        when(knowledgeGraphService.planLearningPath(1L, 2L, 3)).thenReturn(vo);

        mockMvc.perform(post("/knowledge/path/plan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":1,\"targetId\":2,\"maxHops\":3}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("未找到从起点到目标的学习路径"));
        verify(knowledgeGraphService).planLearningPath(1L, 2L, 3);
    }

    @Test
    @DisplayName("POST /knowledge/path/plan - 起点等于终点：短路返回专门提示，而不是普通成功")
    void planLearningPath_sameSourceAndTarget_returnsShortCircuitMessage() throws Exception {
        com.aicourse.vo.KnowledgePathPlanVO vo = new com.aicourse.vo.KnowledgePathPlanVO();
        vo.setSourceId(4L);
        vo.setTargetId(4L);
        vo.setFound(true);
        vo.setHops(0);
        vo.setPath(List.of(point(4L, "自己")));
        when(knowledgeGraphService.planLearningPath(4L, 4L, 0)).thenReturn(vo);

        mockMvc.perform(post("/knowledge/path/plan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":4,\"targetId\":4}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("起点与终点相同，无需规划"))
                .andExpect(jsonPath("$.data.found").value(true))
                .andExpect(jsonPath("$.data.hops").value(0));
    }

    @Test
    @DisplayName("POST /knowledge/path/plan - 缺少起点：400 且不调用图谱服务")
    void planLearningPath_missingSourceId_rejected400() throws Exception {
        mockMvc.perform(post("/knowledge/path/plan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":2}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verify(knowledgeGraphService, never()).planLearningPath(any(), any(), anyInt());
    }

    @Test
    @DisplayName("POST /knowledge/path/plan - 服务层给出 note：404 沿用引擎原文而非默认提示")
    void planLearningPath_notFoundWithServiceNote_echoesNote() throws Exception {
        com.aicourse.vo.KnowledgePathPlanVO vo = new com.aicourse.vo.KnowledgePathPlanVO();
        vo.setSourceId(5L);
        vo.setTargetId(6L);
        vo.setFound(false);
        vo.setMode("ENGINE");
        vo.setNote("图中不存在从 5 到 6 的可达路径");
        when(knowledgeGraphService.planLearningPath(5L, 6L, 0)).thenReturn(vo);

        mockMvc.perform(post("/knowledge/path/plan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":5,\"targetId\":6}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("图中不存在从 5 到 6 的可达路径"));
    }

    @Test
    @DisplayName("POST /knowledge/path/plan - 找到路径且起止不同：普通成功信封，不改写提示语")
    void planLearningPath_foundDifferentEndpoints_plainSuccess() throws Exception {
        com.aicourse.vo.KnowledgePathPlanVO vo = new com.aicourse.vo.KnowledgePathPlanVO();
        vo.setSourceId(7L);
        vo.setTargetId(8L);
        vo.setFound(true);
        vo.setHops(2);
        vo.setMode("ENGINE");
        vo.setPath(List.of(point(7L, "甲"), point(9L, "中继"), point(8L, "乙")));
        when(knowledgeGraphService.planLearningPath(7L, 8L, 4)).thenReturn(vo);

        mockMvc.perform(post("/knowledge/path/plan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":7,\"targetId\":8,\"maxHops\":4}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.hops").value(2))
                .andExpect(jsonPath("$.data.path.length()").value(3))
                .andExpect(jsonPath("$.data.mode").value("ENGINE"));
    }

    // ==================== POST /knowledge/ingest + GET /knowledge/subgraph：parseKgJson 四类降级 ====================

    @Test
    @DisplayName("POST /knowledge/ingest - 引擎返回 null：本地降级 mode=LOCAL 并给出说明")
    void ingestGraph_nullResponse_fallsBackToLocal() throws Exception {
        when(knowledgeGraphService.ingest(1L)).thenReturn(null);

        mockMvc.perform(post("/knowledge/ingest").param("courseId", "1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("LOCAL"))
                .andExpect(jsonPath("$.data.note").value("知识图谱服务返回为空，已使用本地降级"));
    }

    @Test
    @DisplayName("POST /knowledge/ingest - 引擎返回空白串：同样判为「返回为空」")
    void ingestGraph_blankResponse_fallsBackToLocal() throws Exception {
        when(knowledgeGraphService.ingest(2L)).thenReturn("   ");

        mockMvc.perform(post("/knowledge/ingest").param("courseId", "2").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("LOCAL"))
                .andExpect(jsonPath("$.data.note").value(containsString("返回为空")));
    }

    @Test
    @DisplayName("POST /knowledge/ingest - 引擎返回 503 占位：明确标为 UNAVAILABLE 而非伪造成功数据")
    void ingestGraph_engineUnavailable_marksUnavailable() throws Exception {
        when(knowledgeGraphService.ingest(3L)).thenReturn("{\"code\":503,\"msg\":\"engine down\"}");

        mockMvc.perform(post("/knowledge/ingest").param("courseId", "3").with(withUser(9L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.note").value("知识图谱引擎未接入或不可用"));
    }

    @Test
    @DisplayName("POST /knowledge/ingest - 引擎返回正常 JSON：原样转成 Map 透传")
    void ingestGraph_normalJson_passedThroughAsMap() throws Exception {
        when(knowledgeGraphService.ingest(4L)).thenReturn("{\"code\":200,\"ingested\":12}");

        mockMvc.perform(post("/knowledge/ingest").param("courseId", "4").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(200))
                .andExpect(jsonPath("$.data.ingested").value(12))
                .andExpect(jsonPath("$.data.mode").doesNotExist());
    }

    @Test
    @DisplayName("POST /knowledge/ingest - 引擎返回 JSON 字面量 null：转 Map 得到 null，退化为最小 LOCAL 结构")
    void ingestGraph_jsonNull_returnsMinimalLocalMap() throws Exception {
        when(knowledgeGraphService.ingest(5L)).thenReturn("null");

        mockMvc.perform(post("/knowledge/ingest").param("courseId", "5").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("LOCAL"))
                // Map.of("mode","LOCAL") 这条路径不带 note
                .andExpect(jsonPath("$.data.note").doesNotExist());
    }

    @Test
    @DisplayName("POST /knowledge/ingest - 引擎返回非法 JSON：解析异常降级，不得 500")
    void ingestGraph_malformedJson_fallsBackToLocal() throws Exception {
        when(knowledgeGraphService.ingest(6L)).thenReturn("{不是合法 JSON}");

        mockMvc.perform(post("/knowledge/ingest").param("courseId", "6").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("LOCAL"))
                .andExpect(jsonPath("$.data.note").value(containsString("解析失败")));
    }

    @Test
    @DisplayName("POST /knowledge/ingest - 缺少 courseId：400 且不调用引擎")
    void ingestGraph_missingCourseId_returns400() throws Exception {
        mockMvc.perform(post("/knowledge/ingest").with(withUser(1L, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verify(knowledgeGraphService, never()).ingest(any());
    }

    @Test
    @DisplayName("GET /knowledge/subgraph/{courseId} - 传 maxHops：按给定跳数下传引擎")
    void getSubGraph_withMaxHops_passesValue() throws Exception {
        when(knowledgeGraphService.getSubGraph(7L, 2)).thenReturn("{\"nodes\":[],\"edges\":[]}");

        mockMvc.perform(get("/knowledge/subgraph/7").param("maxHops", "2").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes").isArray())
                .andExpect(jsonPath("$.data.edges").isArray());
        verify(knowledgeGraphService).getSubGraph(7L, 2);
    }

    @Test
    @DisplayName("GET /knowledge/subgraph/{courseId} - 不传 maxHops：下传 0（表示不限跳数）")
    void getSubGraph_withoutMaxHops_passesZero() throws Exception {
        when(knowledgeGraphService.getSubGraph(eq(8L), eq(0))).thenReturn(null);

        mockMvc.perform(get("/knowledge/subgraph/8").with(withUser(9L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("LOCAL"));
        verify(knowledgeGraphService).getSubGraph(8L, 0);
    }

    @Test
    @DisplayName("POST /knowledge/ingest 与 GET /knowledge/subgraph - STUDENT 角色：403 拦截在鉴权层")
    void ingestAndSubGraph_studentForbidden() throws Exception {
        mockMvc.perform(post("/knowledge/ingest").param("courseId", "1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/knowledge/subgraph/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
        verify(knowledgeGraphService, never()).ingest(any());
        verify(knowledgeGraphService, never()).getSubGraph(any(), anyInt());
    }

    // ==================== 入参校验：知识点与关系 ====================

    @Test
    @DisplayName("POST /knowledge/points - 名称空白：400 且不落库")
    void createPoint_blankName_rejected400() throws Exception {
        mockMvc.perform(post("/knowledge/points").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"courseId\":1}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("知识点名称不能为空"));
        verify(knowledgePointService, never()).save(any(KnowledgePoint.class));
    }

    @Test
    @DisplayName("POST /knowledge/relations - 缺目标知识点：400 且不调用 createRelation")
    void createRelation_missingTargetId_rejected400() throws Exception {
        mockMvc.perform(post("/knowledge/relations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":1,\"relationType\":\"PREREQUISITE\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("目标知识点ID不能为空")));
        verify(relationService, never()).createRelation(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /knowledge/relations - 关联类型超长（>50）：400 拒绝")
    void createRelation_overlongType_rejected400() throws Exception {
        mockMvc.perform(post("/knowledge/relations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":1,\"targetId\":2,\"relationType\":\"" + "T".repeat(60) + "\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("50")));
        verify(relationService, never()).createRelation(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /knowledge/relations - 带强度与描述：全参数透传服务层")
    void createRelation_withStrengthAndDescription_passesAllArgs() throws Exception {
        when(knowledgePointService.getById(1L)).thenReturn(point(1L, "a"));
        when(knowledgePointService.getById(2L)).thenReturn(point(2L, "b"));
        when(relationService.createRelation(1L, 2L, "PREREQUISITE", new BigDecimal("0.8"), "先修"))
                .thenReturn(relation(5L, 1L, 2L));

        mockMvc.perform(post("/knowledge/relations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":1,\"targetId\":2,\"relationType\":\"PREREQUISITE\","
                                + "\"strength\":0.8,\"description\":\"先修\"}")
                        .with(withUser(9L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.relationType").value("PREREQUISITE"));
    }

    @Test
    @DisplayName("GET /knowledge/relations - 分页参数原样下传（无课程筛选时为 null）")
    void getRelationList_passesPagingAndNullCourse() throws Exception {
        when(relationService.getRelationList(eq(2), eq(20), isNull())).thenReturn(new Page<>());

        mockMvc.perform(get("/knowledge/relations").param("page", "2").param("size", "20")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list").isArray());
        verify(relationService).getRelationList(2, 20, null);
    }

    // ==================== POST /knowledge/extract：异步主体（分支密集区） ====================

    /** 抽取端点的公共桩：save 时补 ID（关系要靠 nameToId 里的 ID 才能连边）。 */
    private void stubSaveAssigningIds(long firstId) {
        AtomicLongHolder holder = new AtomicLongHolder(firstId);
        when(knowledgePointService.save(any(KnowledgePoint.class))).thenAnswer(inv -> {
            KnowledgePoint p = inv.getArgument(0);
            p.setId(holder.next());
            return true;
        });
    }

    /** 极简自增持有器（避免在 lambda 里改局部变量）。 */
    private static final class AtomicLongHolder {
        private long current;

        AtomicLongHolder(long start) {
            this.current = start;
        }

        long next() {
            return ++current;
        }
    }

    @Test
    @DisplayName("POST /knowledge/extract - 完整结构：跳过空白名/无名/已存在，未知题型回落 CONCEPT，关系按名称解析成边")
    void extractGraph_fullStructure_createsNodesAndEdges() throws Exception {
        // 库中已有：一条正常名称（用于命中去重分支）、一条名称为 null（走 ep.getName() != null 为假的守卫）
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(
                List.of(point(500L, "已存在知识点"), point(501L, null)));
        stubSaveAssigningIds(900L);
        when(relationService.createRelation(any(), any(), any(), isNull(), isNull()))
                .thenAnswer(inv -> relation(700L, inv.getArgument(0), inv.getArgument(1)));

        String aiJson = "{\"nodes\":["
                + "{\"name\":\" 栈 \",\"content\":\"LIFO 结构\",\"type\":\"principle\"},"
                + "{\"name\":\"   \",\"content\":\"空白名应跳过\"},"
                + "{\"content\":\"没有 name 应跳过\"},"
                + "{\"name\":\"已存在知识点\"},"
                + "{\"name\":\"新点\",\"type\":\"weird-type\"},"
                + "{\"name\":\"队列\"}"
                + "],\"relations\":["
                + "{\"source\":\"栈\",\"target\":\"队列\",\"relationType\":\"prerequisite\"},"
                + "{\"target\":\"队列\"},"
                + "{\"source\":\"栈\",\"target\":\"栈\"},"
                + "{\"source\":\"查无此点\",\"target\":\"队列\"},"
                + "{\"source\":\"队列\"}"
                + "]}";
        when(aiService.extractKnowledgeGraphJson("课程内容")).thenReturn(aiJson);

        var started = mockMvc.perform(post("/knowledge/extract").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"content\":\"课程内容\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("AI 抽取完成，共写入 3 个知识点"))
                .andExpect(jsonPath("$.data.nodeCount").value(3))
                .andExpect(jsonPath("$.data.nodes.length()").value(3))
                .andExpect(jsonPath("$.data.relationCount").value(1))
                .andExpect(jsonPath("$.data.relations[0].source").value("栈"))
                .andExpect(jsonPath("$.data.relations[0].target").value("队列"))
                .andExpect(jsonPath("$.data.relations[0].relationType").value("PREREQUISITE"))
                .andExpect(jsonPath("$.data.parseError").doesNotExist());

        ArgumentCaptor<KnowledgePoint> cap = ArgumentCaptor.forClass(KnowledgePoint.class);
        verify(knowledgePointService, times(3)).save(cap.capture());
        KnowledgeTypeAssertions.assertAll(cap.getAllValues());
        // 关系两端由名称解析为 ID：栈=901、队列=903（902 是「新点」）
        verify(relationService).createRelation(901L, 903L, "PREREQUISITE", null, null);
        // 5 条候选关系只成了 1 条：其余分别因缺 source、自环、名称查不到、缺 target 被跳过
        verify(relationService, times(1)).createRelation(any(), any(), any(), isNull(), isNull());
    }

    @Test
    @DisplayName("POST /knowledge/extract - content 字段缺省：判为未提供内容，400 且不调用大模型")
    void extractGraph_contentAbsent_returnsBusinessError400() throws Exception {
        var started = mockMvc.perform(post("/knowledge/extract").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请提供待抽取的课程内容"));
        verify(aiService, never()).extractKnowledgeGraphJson(any());
    }

    @Test
    @DisplayName("POST /knowledge/extract - content 为全空白：同样拦在调用大模型之前")
    void extractGraph_blankContent_returnsBusinessError400() throws Exception {
        var started = mockMvc.perform(post("/knowledge/extract").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"content\":\"   \"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        verify(aiService, never()).extractKnowledgeGraphJson(any());
        verify(knowledgePointService, never()).save(any(KnowledgePoint.class));
    }

    @Test
    @DisplayName("POST /knowledge/extract - 缺少 courseId：400 且不启动异步任务")
    void extractGraph_missingCourseId_rejected400() throws Exception {
        mockMvc.perform(post("/knowledge/extract").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"文本\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("课程ID不能为空"));
    }

    @Test
    @DisplayName("POST /knowledge/extract - 大模型返回无 JSON 结构的纯文本：报「未返回有效结构」而不是 500")
    void extractGraph_noJsonObject_reportsParseError() throws Exception {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());
        when(aiService.extractKnowledgeGraphJson("纯文本没有花括号")).thenReturn("模型说它无法抽取");

        var started = mockMvc.perform(post("/knowledge/extract").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"content\":\"纯文本没有花括号\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.parseError").value("AI 未返回有效结构"))
                .andExpect(jsonPath("$.data.nodeCount").value(0))
                .andExpect(jsonPath("$.data.nodes").isArray());
        verify(knowledgePointService, never()).save(any(KnowledgePoint.class));
    }

    @Test
    @DisplayName("POST /knowledge/extract - 有花括号但 JSON 语法坏：整体解析失败降级，不影响接口可用性")
    void extractGraph_malformedJson_reportsParseFailure() throws Exception {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());
        when(aiService.extractKnowledgeGraphJson(any())).thenReturn("{nodes: [坏掉的 JSON}");

        var started = mockMvc.perform(post("/knowledge/extract").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"content\":\"任意内容\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseError").value("AI 返回结构解析失败，请稍后重试"))
                .andExpect(jsonPath("$.data.nodeCount").value(0));
    }

    @Test
    @DisplayName("POST /knowledge/extract - nodes 是对象而非数组：不进入节点循环，也不误建知识点")
    void extractGraph_nodesNotArray_createsNothing() throws Exception {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());
        when(aiService.extractKnowledgeGraphJson(any())).thenReturn("{\"nodes\":{\"a\":1},\"relations\":[]}");

        var started = mockMvc.perform(post("/knowledge/extract").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"content\":\"内容\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeCount").value(0))
                .andExpect(jsonPath("$.data.relationCount").value(0));
        verify(knowledgePointService, never()).save(any(KnowledgePoint.class));
    }

    @Test
    @DisplayName("POST /knowledge/extract - relations 是对象而非数组：跳过连边但节点照常写入")
    void extractGraph_relationsNotArray_stillCreatesNodes() throws Exception {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());
        stubSaveAssigningIds(950L);
        when(aiService.extractKnowledgeGraphJson(any()))
                .thenReturn("{\"nodes\":[{\"name\":\"甲\"}],\"relations\":{\"x\":1}}");

        var started = mockMvc.perform(post("/knowledge/extract").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"content\":\"内容\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeCount").value(1))
                .andExpect(jsonPath("$.data.relationCount").value(0));
        verify(relationService, never()).createRelation(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /knowledge/extract - 单条关系建边失败：内部吞掉，其余节点与响应不受影响")
    void extractGraph_singleRelationFailure_swallowedAndContinues() throws Exception {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());
        stubSaveAssigningIds(980L);
        when(aiService.extractKnowledgeGraphJson(any()))
                .thenReturn("{\"nodes\":[{\"name\":\"甲\"},{\"name\":\"乙\"}],"
                        + "\"relations\":[{\"source\":\"甲\",\"target\":\"乙\"}]}");
        when(relationService.createRelation(any(), any(), any(), isNull(), isNull()))
                .thenThrow(new IllegalStateException("关系类型不合法"));

        var started = mockMvc.perform(post("/knowledge/extract").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"content\":\"内容\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeCount").value(2))
                .andExpect(jsonPath("$.data.relationCount").value(0))
                .andExpect(jsonPath("$.data.relations").isArray())
                .andExpect(jsonPath("$.data.relations.length()").value(0))
                .andExpect(jsonPath("$.data.parseError").doesNotExist());
    }

    @Test
    @DisplayName("POST /knowledge/extract - 大模型返回空对象：既无 nodes 也无 relations，计数全 0")
    void extractGraph_emptyObject_returnsZeroCounts() throws Exception {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());
        when(aiService.extractKnowledgeGraphJson(any())).thenReturn("{}");

        var started = mockMvc.perform(post("/knowledge/extract").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"content\":\"内容\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeCount").value(0))
                .andExpect(jsonPath("$.data.relationCount").value(0))
                .andExpect(jsonPath("$.data.parseError").doesNotExist());
    }

    @Test
    @DisplayName("POST /knowledge/extract - ```json 代码块包裹：extractJsonObject 先剥壳再解析")
    void extractGraph_codeBlockWrappedJson_parsed() throws Exception {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());
        stubSaveAssigningIds(990L);
        when(aiService.extractKnowledgeGraphJson(any()))
                .thenReturn("这是模型的废话前缀\n```json\n{\"nodes\":[{\"name\":\"归一化\"}]}\n```");

        var started = mockMvc.perform(post("/knowledge/extract").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"content\":\"内容\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeCount").value(1))
                .andExpect(jsonPath("$.data.nodes[0].name").value("归一化"))
                .andExpect(jsonPath("$.data.nodes[0].pointType").value("CONCEPT"));
    }

    /**
     * 抽取出的知识点必须满足：courseId 归属正确、名称已 trim、缺省类型为 CONCEPT、
     * 未知类型回落 CONCEPT（否则前端按类型分支渲染会取不到值）。
     */
    private static final class KnowledgeTypeAssertions {
        static void assertAll(List<KnowledgePoint> saved) {
            assertEquals(3, saved.size());
            KnowledgePoint stack = saved.get(0);
            assertEquals("栈", stack.getName(), "名称必须 trim 后入库");
            assertEquals(1L, stack.getCourseId());
            assertEquals("LIFO 结构", stack.getDescription());
            assertEquals("PRINCIPLE", stack.getPointType());
            assertEquals("CONCEPT", saved.get(1).getPointType(), "非法题型必须回落 CONCEPT");
            assertEquals("", saved.get(2).getDescription(), "缺 content 时描述应为空串而不是 null");
            assertEquals("队列", saved.get(2).getName());
        }
    }
}
