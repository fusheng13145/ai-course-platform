package com.aicourse.controller;

import com.aicourse.entity.Course;
import com.aicourse.entity.GradeItem;
import com.aicourse.entity.GradePolicy;
import com.aicourse.mapper.GradeItemMapper;
import com.aicourse.mapper.GradePolicyMapper;
import com.aicourse.service.CourseService;
import com.aicourse.service.impl.GradeServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GradeController 报表/总评的边界与缺陷用例（权限矩阵由 {@link GradeControllerTest} 覆盖，此处不重复）。
 *
 * <p>与 GradeControllerTest 的关键差别：这里 {@code @Import(GradeServiceImpl.class)} 装入<b>真实</b>
 * 成绩服务，只把两张 mapper 与 CourseService 换成 mock，因此「空名单是否 500」「null 分是否被当 0」
 * 这类计算边界才是真的被测到，而不是被 mock 掉的 Service 替我们保证。
 *
 * <p>mapper 用「按 where 条件过滤内存明细」的方式模拟 SQL（见 {@link #selectItems}），
 * 这样若生产代码把 studentId 条件写丢，报表就会串到别的学生的分数上，用例能立刻失败。
 */
@WebMvcTest(GradeController.class)
@Import(GradeServiceImpl.class)
class GradeControllerReportBoundaryTest extends ControllerTestBase {

    private static final long POLICY_ID = 700L;
    private static final long COURSE_ID = 7L;
    private static final long TEACHER_ID = 20L;

    @MockitoBean private GradePolicyMapper policyMapper;
    @MockitoBean private GradeItemMapper itemMapper;
    @MockitoBean private CourseService courseService;

    @Autowired private GradeServiceImpl gradeService;

    /** 内存中的成绩明细，模拟 grade_items 表 */
    private final List<GradeItem> gradeItems = new ArrayList<>();
    /** 花名册（学生ID全集），用于区分「单人查询」与「报表全量查询」的 where 条件 */
    private final Set<Long> roster = new HashSet<>();

    @BeforeEach
    void setUp() throws Exception {
        // LambdaQueryWrapper 的列名与条件参数在 getSqlSegment() 时才落地，需先注册实体元数据，
        // 否则 selectItems 无法读出 where 值、也就无法模拟 SQL 过滤。
        for (Class<?> e : new Class<?>[]{GradePolicy.class, GradeItem.class}) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), e);
        }
        // 切片里 ServiceImpl.baseMapper 的按类型注入不保证生效，显式接上 mock
        ReflectionTestUtils.setField(gradeService, "baseMapper", policyMapper);

        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(itemMapper.selectList(any())).thenAnswer(this::selectItems);
        // insert 落到内存表，好让「先 POST 录入、再 GET 总评」这类跨请求的边界能连贯断言
        when(itemMapper.insert(any(GradeItem.class))).thenAnswer(inv -> {
            GradeItem it = inv.getArgument(0);
            gradeItems.add(it);
            if (it.getStudentId() != null) {
                roster.add(it.getStudentId());
            }
            return 1;
        });
    }

    private static Course course(Long id, Long teacherId) {
        Course c = new Course();
        c.setId(id);
        c.setTeacherId(teacherId);
        return c;
    }

    private static GradePolicy policy(String components) {
        GradePolicy p = new GradePolicy();
        p.setId(POLICY_ID);
        p.setCourseId(COURSE_ID);
        p.setName("平时成绩");
        p.setComponents(components);
        return p;
    }

    /** 装载方案：selectById 供 computeFinal/getPolicyById 用，selectList 供 getPolicy(courseId) 用。 */
    private void givenPolicy(String components) {
        GradePolicy p = policy(components);
        when(policyMapper.selectById(POLICY_ID)).thenReturn(p);
        when(policyMapper.selectList(any())).thenReturn(List.of(p));
    }

    private GradeItem item(Long studentId, String componentKey, String score) {
        GradeItem it = new GradeItem();
        it.setPolicyId(POLICY_ID);
        it.setStudentId(studentId);
        it.setComponentKey(componentKey);
        it.setScore(score == null ? null : new BigDecimal(score));
        gradeItems.add(it);
        if (studentId != null) {
            roster.add(studentId);
        }
        return it;
    }

    /** 触发 wrapper 构建 SQL 片段并取出 where 参数值（等价于真实 SQL 的过滤条件）。 */
    private static Collection<Object> wrapperParams(Wrapper<?> wrapper) {
        assertNotNull(wrapper, "查询条件 wrapper 不得为 null");
        AbstractWrapper<?, ?, ?> aw = (AbstractWrapper<?, ?, ?>) wrapper;
        aw.getSqlSegment();
        return aw.getParamNameValuePairs().values();
    }

    /**
     * 以内存过滤模拟 MyBatis 的 where：条件里出现学生ID 即为「某学生某方案」查询，
     * 只有方案ID 即为报表那次「全量取本方案明细」查询。
     */
    private List<GradeItem> selectItems(InvocationOnMock invocation) {
        Wrapper<?> wrapper = invocation.getArgument(0);
        Collection<Object> params = wrapperParams(wrapper);
        boolean perStudent = roster.stream().anyMatch(params::contains);
        return gradeItems.stream()
                .filter(it -> params.contains(it.getPolicyId()))
                .filter(it -> !perStudent || params.contains(it.getStudentId()))
                .collect(Collectors.toList());
    }

    /** 单维 100% 的常规方案，便于把断言集中在「分数怎么算」上。 */
    private static String homeworkOnly() {
        return "[{\"key\":\"HOMEWORK\",\"name\":\"作业\",\"weight\":100}]";
    }

    // ===================== GET /grade/report/course/{courseId} =====================

    @Test
    @DisplayName("报表 - 方案存在但全班零名学生：返回空数组而非 null，不得 500")
    void courseReport_emptyRoster_returnsEmptyArrayNotNull() throws Exception {
        givenPolicy(homeworkOnly());
        // 无任何明细行 -> 真实 SQL 返回空结果集

        String body = mockMvc.perform(get("/grade/report/course/" + COURSE_ID)
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        assertTrue(data.isArray(), "空名单必须返回集合而非 null，实际=" + data);
        assertFalse(data.isNull(), "data 为 JSON null 会让前端 data.length 直接抛错");
        assertEquals(0, data.size(), "零学生应得到空数组，实际=" + data);
    }

    @Test
    @DisplayName("报表 - 明细含脏数据（学生ID为空）：过滤后按总评降序，不得 500")
    void courseReport_nullStudentIdRowsFiltered_sortedDesc() throws Exception {
        givenPolicy(homeworkOnly());
        item(11L, "HOMEWORK", "90");
        item(12L, "HOMEWORK", "60");
        item(null, "HOMEWORK", "100");   // 脏数据：没有归属学生的明细行

        mockMvc.perform(get("/grade/report/course/" + COURSE_ID)
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                // 花名册只认得 11/12，脏行既不该多出一行也不该让流式过滤抛 NPE
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].studentId").value(11))
                .andExpect(jsonPath("$.data[0].finalScore").value(90.0))
                .andExpect(jsonPath("$.data[1].studentId").value(12))
                .andExpect(jsonPath("$.data[1].finalScore").value(60.0));
    }

    @Test
    @DisplayName("报表 - 课程未配置方案：当前抛业务异常返回 400（而非空报表）")
    void courseReport_policyNotConfigured_currentlyReturns400() throws Exception {
        when(policyMapper.selectList(any())).thenReturn(List.of());

        mockMvc.perform(get("/grade/report/course/" + COURSE_ID)
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                // 当前行为：未配置方案时 500 变 400 提示，而不是返回空列表（前端首屏需自行兜底）
                .andExpect(jsonPath("$.message").value("课程尚未配置成绩方案"));
    }

    @Test
    @DisplayName("报表 - 课程不存在：404 且先于数据查询（越权检查 fail-closed）")
    void courseReport_courseMissing_returns404BeforeAnyQuery() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(null);

        mockMvc.perform(get("/grade/report/course/" + COURSE_ID)
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("课程不存在"));
        // 课程都取不到时不得继续查方案/明细，否则会白跑两次全表扫描
        verify(policyMapper, never()).selectList(any());
        verify(itemMapper, never()).selectList(any());
    }

    // ===================== GET /grade/final/{policyId}/student/{studentId} =====================

    @Test
    @DisplayName("总评 - 方案不存在：控制器拦为 404，且不落到明细查询")
    void computeFinal_policyMissing_returns404() throws Exception {
        when(policyMapper.selectById(999L)).thenReturn(null);

        mockMvc.perform(get("/grade/final/999/student/11").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("成绩方案不存在"));
        verify(itemMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("总评 - 维度缺 weight 字段：按 0 权重降级，不再 NPE 500")
    void computeFinal_componentWithoutWeight_degradesToZero() throws Exception {
        // 权重字段名写错/漏写是配置 JSON 最常见的失误：修复前 ((Number) null).doubleValue() 抛 NPE，
        // 单个学生的总评连带整班报表 500；修复后该维度记 0 权重并落 warn 日志。
        givenPolicy("[{\"key\":\"HOMEWORK\",\"name\":\"作业\"}]");
        item(11L, "HOMEWORK", "90");

        mockMvc.perform(get("/grade/final/" + POLICY_ID + "/student/11")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.components[0].weight").value(0.0))
                .andExpect(jsonPath("$.data.finalScore").value(0.0));
    }

    @Test
    @DisplayName("总评 - 权重合计不足 100：当前不校验，缺的份额直接从总分里扣掉")
    void computeFinal_weightsBelow100_currentlyNoValidation() throws Exception {
        givenPolicy("[{\"key\":\"HOMEWORK\",\"name\":\"作业\",\"weight\":60}]");
        item(11L, "HOMEWORK", "100");

        // 当前行为：满分 100 的作业只配了 60% 权重 -> 总评 60.00，系统不报「权重未配平」
        mockMvc.perform(get("/grade/final/" + POLICY_ID + "/student/11")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalScore").value(60.0))
                .andExpect(jsonPath("$.data.components[0].weight").value(60.0))
                .andExpect(jsonPath("$.data.components[0].contribution").value(60.0));
    }

    @Test
    @DisplayName("总评 - 某维度未录入：当前按 0 分计入，会稀释其余维度的得分")
    void computeFinal_missingDimensionCountedAsZero() throws Exception {
        givenPolicy("[{\"key\":\"HOMEWORK\",\"name\":\"作业\",\"weight\":50},"
                + "{\"key\":\"EXAM\",\"name\":\"考试\",\"weight\":50}]");
        item(11L, "HOMEWORK", "100");   // 考试维度一条明细都没有

        // 当前行为：作业满分 100 但考试缺失 -> 总评 50.00（未录入按 0 加权，而非跳过该维度）
        mockMvc.perform(get("/grade/final/" + POLICY_ID + "/student/11")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalScore").value(50.0))
                .andExpect(jsonPath("$.data.components[1].score").value(0))
                .andExpect(jsonPath("$.data.components[1].contribution").value(0.0));
    }

    @Test
    @DisplayName("总评 - 已录入但分数为 null（未批改）：当前与真实 0 分完全不可区分")
    void computeFinal_nullScoreIndistinguishableFromRealZero() throws Exception {
        givenPolicy(homeworkOnly());
        item(11L, "HOMEWORK", null);   // 已录入但未批改：score 为 null
        item(12L, "HOMEWORK", "0");    // 真实考了 0 分

        JsonNode ungraded = requestFinal(11L);
        JsonNode realZero = requestFinal(12L);

        // 当前行为：null 分被 BigDecimal.ZERO 顶替，出参、总评与真 0 分逐字节相同，
        // 教师无法从接口看出这名学生是「没批」还是「批了零分」
        assertEquals(realZero.path("data").path("finalScore").asText(),
                ungraded.path("data").path("finalScore").asText(),
                "未批改与真实 0 分的总评当前不可区分");
        assertEquals(0.0, ungraded.path("data").path("finalScore").asDouble());
        JsonNode score = ungraded.path("data").path("components").get(0).path("score");
        assertTrue(score.isNumber(), "当前行为：score 为 null 时出参写成数字 0，实际=" + score);
        assertEquals(0, score.asInt());
    }

    private JsonNode requestFinal(long studentId) throws Exception {
        String body = mockMvc.perform(get("/grade/final/" + POLICY_ID + "/student/" + studentId)
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    @DisplayName("总评 - 全维度无任何明细：返回 0.00 空明细不得 500")
    void computeFinal_noItemsAtAll_returnsZeroNot500() throws Exception {
        givenPolicy(homeworkOnly());
        // 该学生在本方案下一条明细都没有

        mockMvc.perform(get("/grade/final/" + POLICY_ID + "/student/11")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalScore").value(0.0))
                .andExpect(jsonPath("$.data.components.length()").value(1));
    }

    @Test
    @DisplayName("总评 - 权重 JSON 无法解析：当前静默降级为 0 分而不是报错")
    void computeFinal_corruptedComponentsJson_currentlySilentZero() throws Exception {
        givenPolicy("这不是合法 JSON");

        // 当前行为：parseComponents 吞掉解析异常返回空列表 -> 总评 0.00、明细为空，教师看不出是配置坏了
        mockMvc.perform(get("/grade/final/" + POLICY_ID + "/student/11")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalScore").value(0.0))
                .andExpect(jsonPath("$.data.components.length()").value(0));
    }

    // ===================== POST /grade/item 分数区间 =====================

    @Test
    @DisplayName("录入成绩 - 负分：区间校验拒绝 400，不落库")
    void upsertItem_negativeScore_rejected400() throws Exception {
        givenPolicy(homeworkOnly());

        mockMvc.perform(post("/grade/item").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyId\":700,\"studentId\":11,\"componentKey\":\"HOMEWORK\","
                                + "\"score\":-5,\"rawScore\":-5,\"sourceId\":88}")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不能小于 0")));

        // 越界分一旦被拒，就不能有任何一条明细落库；同时 graderId 链路无需触发 insert
        verify(itemMapper, never()).insert(any(GradeItem.class));
        assertTrue(gradeItems.isEmpty(), "负分不得入库，实际=" + gradeItems);
    }

    @Test
    @DisplayName("录入成绩 - 超过 100 分：区间校验拒绝 400，不再虚增总评")
    void upsertItem_scoreAboveHundred_rejected400() throws Exception {
        givenPolicy(homeworkOnly());

        mockMvc.perform(post("/grade/item").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyId\":700,\"studentId\":11,\"componentKey\":\"HOMEWORK\",\"score\":150}")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不能大于 100")));

        // 回归：越界分被拦在写入口，150 不会再进权重计算把总评顶到 150
        mockMvc.perform(get("/grade/final/" + POLICY_ID + "/student/11")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalScore").value(0.0));
    }

    @Test
    @DisplayName("录入成绩 - score 为 null（尚未评分）：允许写入，总评按 0 计入")
    void upsertItem_nullScore_accepted() throws Exception {
        givenPolicy(homeworkOnly());

        mockMvc.perform(post("/grade/item").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyId\":700,\"studentId\":11,\"componentKey\":\"HOMEWORK\",\"rawScore\":88}")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").doesNotExist());

        // 边界：0 与 100 两个端点必须放行（满分是合法输入，不能被区间校验误杀）
        mockMvc.perform(post("/grade/item").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyId\":700,\"studentId\":12,\"componentKey\":\"HOMEWORK\",\"score\":100}")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("录入成绩 - 合法分数：逐字段落库且查重键锁定方案+学生+维度")
    void upsertItem_validScore_persistsWithAuditFields() throws Exception {
        givenPolicy(homeworkOnly());

        mockMvc.perform(post("/grade/item").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyId\":700,\"studentId\":11,\"componentKey\":\"HOMEWORK\","
                                + "\"score\":85,\"rawScore\":17,\"sourceId\":88}")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(85));

        // rawScore 与归一化 score 分开保存：5 分制/100 分制换算不能丢原始依据
        ArgumentCaptor<GradeItem> cap = ArgumentCaptor.forClass(GradeItem.class);
        verify(itemMapper).insert(cap.capture());
        GradeItem saved = cap.getValue();
        assertEquals(POLICY_ID, saved.getPolicyId());
        assertEquals(11L, saved.getStudentId());
        assertEquals("HOMEWORK", saved.getComponentKey());
        assertEquals(0, new BigDecimal("85").compareTo(saved.getScore()));
        assertEquals(0, new BigDecimal("17").compareTo(saved.getRawScore()));
        assertEquals(TEACHER_ID, saved.getGraderId(), "录入者必须落库，否则成绩来源无法追溯");

        // upsert 的幂等键必须是 方案+学生+维度，少了学生ID 就会把别的学生成绩覆盖掉
        ArgumentCaptor<Wrapper> oneCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(itemMapper).selectOne(oneCap.capture());
        Collection<Object> params = wrapperParams(oneCap.getValue());
        assertTrue(params.contains(POLICY_ID) && params.contains(11L) && params.contains("HOMEWORK"),
                "查重条件须锁定方案+学生+维度，实际=" + params);
    }

    // ===================== POST /grade/policy 权重配置 =====================
    @Test
    @DisplayName("保存方案 - 权重合计不为 100：当前仅前端提示，后端放行")
    void savePolicy_weightSumNot100_currentlyAccepted() throws Exception {
        mockMvc.perform(post("/grade/policy").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":7,\"name\":\"宽松方案\","
                                + "\"components\":[{\"key\":\"exam\",\"name\":\"期末\",\"weight\":60}]}")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("保存方案 - 权重越界/重复 key/结构非法：全部 400 拒绝，不写入坏配置")
    void savePolicy_invalidComponents_rejected() throws Exception {
        List<String> badBodies = List.of(
                "{\"courseId\":7,\"components\":[{\"key\":\"exam\",\"weight\":120}]}",
                "{\"courseId\":7,\"components\":[{\"key\":\"exam\",\"weight\":-10}]}",
                "{\"courseId\":7,\"components\":[{\"key\":\"exam\",\"weight\":60},{\"key\":\"exam\",\"weight\":40}]}",
                "{\"courseId\":7,\"components\":[{\"name\":\"缺标识\",\"weight\":60}]}",
                "{\"courseId\":7,\"components\":\"这不是JSON\"}");
        for (String body : badBodies) {
            mockMvc.perform(post("/grade/policy").contentType(MediaType.APPLICATION_JSON)
                            .content(body).with(withUser(TEACHER_ID, "TEACHER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
        // 坏配置必须在落库前被拒：任何一次 insert/updateById 都意味着脏数据进了库
        verify(policyMapper, never()).insert(any(GradePolicy.class));
        verify(policyMapper, never()).updateById(any(GradePolicy.class));
    }

    @Test
    @DisplayName("保存方案 - 构成项留空（先建方案后配维度）：允许通过")
    void savePolicy_blankComponents_accepted() throws Exception {
        mockMvc.perform(post("/grade/policy").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":7,\"name\":\"壳方案\"}")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.components").isArray())
                .andExpect(jsonPath("$.data.components.length()").value(0));
    }

    // ===================== 权重脏数据的读取容错 =====================

    @Test
    @DisplayName("总评 - weight 写成数字字符串/非数字字符串：前者容错解析、后者按 0 计")
    void computeFinal_stringWeights_parsedOrDegraded() throws Exception {
        givenPolicy("[{\"key\":\"HOMEWORK\",\"name\":\"作业\",\"weight\":\"八十\"},"
                + "{\"key\":\"EXAM\",\"name\":\"期末\",\"weight\":\"70\"}]");
        gradeItems.add(item(11L, "HOMEWORK", "80"));
        gradeItems.add(item(11L, "EXAM", "90"));

        mockMvc.perform(get("/grade/final/" + POLICY_ID + "/student/11")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.components[0].weight").value(0.0))
                // 数字字符串 "70" 容错接受：90 * 70% = 63
                .andExpect(jsonPath("$.data.components[1].weight").value(70.0))
                .andExpect(jsonPath("$.data.finalScore").value(63.0));
    }
}
