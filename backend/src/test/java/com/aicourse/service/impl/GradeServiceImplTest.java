package com.aicourse.service.impl;

import com.aicourse.entity.GradeItem;
import com.aicourse.entity.GradePolicy;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.GradeItemMapper;
import com.aicourse.mapper.GradePolicyMapper;
import com.aicourse.service.UserService;
import com.aicourse.vo.FinalGradeVO;
import com.aicourse.vo.GradeComponentDetailVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GradeServiceImpl 单元测试（B 主战场新文件类，缺口 435）。
 * extends ServiceImpl<GradePolicyMapper, GradePolicy>：手动构造 + 注入 baseMapper(mock) + 独立 itemMapper(mock)；
 * 实体 lambda wrapper 涉及 GradePolicy / GradeItem → 注册 TableInfo；
 * objectMapper 为真实实例（不可 mock），components 须提供合法 JSON 以驱动 parseComponents；
 * save/list/getById/updateById 走 policyMapper；upsertItem/computeFinal/courseReport 额外走 itemMapper.selectOne/selectList；
 * 学生姓名走 userService.listByIds（未打桩时返回空名单，姓名退回「学生 #id」）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GradeServiceImplTest {

    @Mock private GradePolicyMapper policyMapper;
    @Mock private GradeItemMapper itemMapper;
    @Mock private UserService userService;

    private GradeServiceImpl service;

    private static final String COMPS =
            "[{\"key\":\"exam\",\"name\":\"期末考试\",\"weight\":60},{\"key\":\"hw\",\"name\":\"作业\",\"weight\":40}]";

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GradePolicy.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GradeItem.class);

        service = new GradeServiceImpl(policyMapper, itemMapper, userService,
                org.mockito.Mockito.mock(com.aicourse.service.component.org.TeachingClassSnapshotResolver.class));
        ReflectionTestUtils.setField(service, "baseMapper", policyMapper);

        GradePolicy p = new GradePolicy();
        p.setId(7L);
        p.setCourseId(10L);
        p.setComponents(COMPS);

        when(policyMapper.selectById(anyLong())).thenReturn(p);
        when(policyMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(policyMapper.insert(any(GradePolicy.class))).thenReturn(1);
        when(policyMapper.updateById(any(GradePolicy.class))).thenReturn(1);
        when(itemMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(itemMapper.insert(any(GradeItem.class))).thenReturn(1);
        when(itemMapper.updateById(any(GradeItem.class))).thenReturn(1);
    }

    private GradePolicy policyWithComps() {
        GradePolicy p = new GradePolicy();
        p.setId(7L);
        p.setCourseId(10L);
        p.setComponents(COMPS);
        return p;
    }

    // ===================== savePolicy =====================

    @Test
    @DisplayName("savePolicy 课程ID为空抛异常")
    void save_noCourse() {
        GradePolicy p = new GradePolicy();
        assertThrows(BusinessException.class, () -> service.savePolicy(p, 1L));
    }

    @Test
    @DisplayName("savePolicy 无现存方案则新建")
    void save_new() {
        GradePolicy p = new GradePolicy();
        p.setCourseId(10L);
        GradePolicy r = service.savePolicy(p, 1L);
        assertTrue(r.getIsDefault());
        assertEquals(1L, r.getCreatorId());
        verify(policyMapper).insert(any(GradePolicy.class));
        verify(policyMapper, never()).updateById(any(GradePolicy.class));
    }

    @Test
    @DisplayName("savePolicy 已有方案则更新")
    void save_update() {
        GradePolicy existing = policyWithComps();
        existing.setCreatedAt(java.time.LocalDateTime.now());
        when(policyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));
        GradePolicy p = new GradePolicy();
        p.setCourseId(10L);
        GradePolicy r = service.savePolicy(p, 1L);
        assertEquals(7L, r.getId());
        verify(policyMapper).updateById(any(GradePolicy.class));
        verify(policyMapper, never()).insert(any(GradePolicy.class));
    }

    // ===================== getPolicy =====================

    @Test
    @DisplayName("getPolicy 命中返回方案")
    void getPolicy_ok() {
        GradePolicy stored = policyWithComps();
        when(policyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stored));
        GradePolicy result = service.getPolicy(10L);
        assertNotNull(result);
        // 真实业务断言：返回的必须是命中的那条方案本体（ID/课程/组件配置透传），而非任意对象
        assertSame(stored, result, "应返回命中查询的那条方案");
        assertEquals(7L, result.getId());
        assertEquals(10L, result.getCourseId());
        assertEquals(COMPS, result.getComponents());
        // getPolicy 通过课程维度的列表查询定位方案，必须实际触发一次 selectList
        verify(policyMapper).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("getPolicy 未配置返回 null")
    void getPolicy_empty() {
        assertNull(service.getPolicy(10L));
    }

    @Test
    @DisplayName("getPolicyById 直接查 ID")
    void getPolicyById() {
        assertNotNull(service.getPolicyById(7L));
    }

    // ===================== upsertItem =====================

    @Test
    @DisplayName("upsertItem 不存在则插入")
    void upsert_new() {
        service.upsertItem(7L, 1L, "exam", new BigDecimal("80"), new BigDecimal("80"), 1L, 2L, "备注");
        verify(itemMapper).insert(any(GradeItem.class));
        verify(itemMapper, never()).updateById(any(GradeItem.class));
    }

    @Test
    @DisplayName("upsertItem 已存在则更新")
    void upsert_existing() {
        GradeItem it = new GradeItem();
        it.setId(99L);
        when(itemMapper.selectOne(any(Wrapper.class))).thenReturn(it);
        service.upsertItem(7L, 1L, "exam", new BigDecimal("80"), new BigDecimal("80"), 1L, 2L, "备注");
        verify(itemMapper).updateById(any(GradeItem.class));
        verify(itemMapper, never()).insert(any(GradeItem.class));
    }

    // ===================== computeFinal =====================

    @Test
    @DisplayName("computeFinal 加权计算期末成绩")
    void compute_ok() {
        GradeItem exam = new GradeItem();
        exam.setComponentKey("exam");
        exam.setScore(new BigDecimal("80"));
        GradeItem hw = new GradeItem();
        hw.setComponentKey("hw");
        hw.setScore(new BigDecimal("90"));
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(exam, hw));

        FinalGradeVO r = service.computeFinal(7L, 1L);
        assertEquals(new BigDecimal("84.00"), r.getFinalScore());
        List<GradeComponentDetailVO> detail = r.getComponents();
        assertEquals(2, detail.size());
    }

    @Test
    @DisplayName("computeFinal 方案不存在抛异常")
    void compute_noPolicy() {
        when(policyMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.computeFinal(7L, 1L));
    }

    @Test
    @DisplayName("computeFinal components 为空则零分并提示未配置")
    void compute_blankComponents() {
        GradePolicy p = new GradePolicy();
        p.setId(7L);
        p.setCourseId(10L);
        p.setComponents("");
        when(policyMapper.selectById(anyLong())).thenReturn(p);
        FinalGradeVO r = service.computeFinal(7L, 1L);
        assertEquals(new BigDecimal("0.00"), r.getFinalScore());
        List<GradeComponentDetailVO> detail = r.getComponents();
        assertTrue(detail.isEmpty());
        assertEquals(List.of("成绩方案未配置构成项，无法计算总评"), r.getWarnings(),
                "空方案只提示「未配置」，不该再叠加权重合计告警");
    }

    @Test
    @DisplayName("computeFinal components 非法 JSON 降级为空")
    void compute_badComponents() {
        GradePolicy p = new GradePolicy();
        p.setId(7L);
        p.setCourseId(10L);
        p.setComponents("not-json");
        when(policyMapper.selectById(anyLong())).thenReturn(p);
        FinalGradeVO r = service.computeFinal(7L, 1L);
        assertEquals(new BigDecimal("0.00"), r.getFinalScore());
    }

    // ===================== computeFinal 权重脏数据 =====================

    private void policy(String components) {
        GradePolicy p = new GradePolicy();
        p.setId(7L);
        p.setCourseId(10L);
        p.setComponents(components);
        when(policyMapper.selectById(anyLong())).thenReturn(p);
    }

    @Test
    @DisplayName("computeFinal 权重缺失：按 0 权重降级并给出修正提示，不再 NPE 500")
    void compute_missingWeight_degradesToZero() {
        policy("[{\"key\":\"exam\",\"name\":\"期末\"}]");
        GradeItem exam = new GradeItem();
        exam.setComponentKey("exam");
        exam.setScore(new BigDecimal("80"));
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(exam));

        FinalGradeVO r = service.computeFinal(7L, 1L);
        assertEquals(new BigDecimal("0.00"), r.getFinalScore(), "缺权重应按 0 计而非抛异常");
        assertEquals(0d, r.getComponents().get(0).getWeight());
        assertTrue(r.getWarnings().get(0).contains("权重缺失或不是数字"),
                "脏配置必须显式提示教师修正，实际=" + r.getWarnings());
    }

    @Test
    @DisplayName("computeFinal 权重写成数字字符串：容错解析；分母只取已评分权重并归一")
    void compute_stringWeight_parsed() {
        policy("[{\"key\":\"exam\",\"name\":\"期末\",\"weight\":\"70\"}]");
        GradeItem exam = new GradeItem();
        exam.setComponentKey("exam");
        exam.setScore(new BigDecimal("80"));
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(exam));

        // 语义变更（2026-10-04）：总评分母改为「已评分维度的权重合计」，
        // 80 分 × 70 / 70 = 80，而不是旧实现的 80×70%=56（旧算法把未配的 30% 当成 0 分扣掉）。
        // 权重合计≠100 属方案不合规，新方案已在 savePolicy 拒绝，历史脏方案靠 warnings 提示。
        FinalGradeVO r = service.computeFinal(7L, 1L);
        assertEquals(new BigDecimal("80.00"), r.getFinalScore());
        assertEquals(70d, r.getComponents().get(0).getWeight());
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("权重合计为 70%")),
                "应提示方案权重不配平，实际=" + r.getWarnings());
    }

    @Test
    @DisplayName("computeFinal 权重写成非数字字符串：按 0 计，不抛 NumberFormatException")
    void compute_nonNumericWeight_degradesToZero() {
        policy("[{\"key\":\"exam\",\"name\":\"期末\",\"weight\":\"六十\"}]");
        FinalGradeVO r = service.computeFinal(7L, 1L);
        assertEquals(new BigDecimal("0.00"), r.getFinalScore());
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("所有维度的权重均为 0")),
                "实际=" + r.getWarnings());
    }

    // ===================== computeFinal 未评分剔除（2026-10-04 语义） =====================

    @Test
    @DisplayName("computeFinal 未评分维度不进分母：按已评分权重归一，并在明细与提示中标注")
    void compute_ungradedExcludedFromDenominator() {
        GradeItem exam = new GradeItem();
        exam.setComponentKey("exam");
        exam.setScore(new BigDecimal("80"));
        GradeItem hw = new GradeItem();
        hw.setComponentKey("hw");
        hw.setScore(null); // 未评分：既不是 0 分，也不该占分母
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(exam, hw));

        FinalGradeVO r = service.computeFinal(7L, 1L);
        assertEquals(new BigDecimal("80.00"), r.getFinalScore(),
                "只评了 60% 权重的期末时，总评应等于 80 而非 48");

        GradeComponentDetailVO examRow = r.getComponents().get(0);
        assertTrue(examRow.isGraded());
        assertEquals(new BigDecimal("80.00"), examRow.getContribution(), "贡献分=80×60/60");

        GradeComponentDetailVO hwRow = r.getComponents().get(1);
        assertFalse(hwRow.isGraded(), "未评分维度须标记为不计入");
        assertNull(hwRow.getScore(), "未评分不得冒充 0 分");
        assertEquals(new BigDecimal("0.00"), hwRow.getContribution());

        assertEquals(1, r.getWarnings().size(), "方案本身配平（60+40），只应有 1 条缺评提示，实际=" + r.getWarnings());
        assertTrue(r.getWarnings().get(0).contains("作业"), "提示须点名缺评维度，实际=" + r.getWarnings());
        assertTrue(r.getWarnings().get(0).contains("60%"), "提示须说明归一依据，实际=" + r.getWarnings());
    }

    @Test
    @DisplayName("computeFinal 全部维度未评分：总评 0 并提示无任何已评分维度")
    void compute_allUngraded() {
        GradeItem exam = new GradeItem();
        exam.setComponentKey("exam");
        exam.setScore(null);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(exam));

        FinalGradeVO r = service.computeFinal(7L, 1L);
        assertEquals(new BigDecimal("0.00"), r.getFinalScore());
        assertTrue(r.getWarnings().get(0).contains("尚无任何计入总评的已评分维度"),
                "实际=" + r.getWarnings());
    }

    // ===================== savePolicy 构成项校验 =====================

    private GradePolicy policyToSave(String components) {
        GradePolicy p = new GradePolicy();
        p.setCourseId(10L);
        p.setComponents(components);
        return p;
    }

    @Test
    @DisplayName("savePolicy 构成项留空允许；重复 key / 权重越界 / 结构非法一律拒绝")
    void save_validateComponents() {
        // 先建壳方案后配维度是合法用法
        assertNotNull(service.savePolicy(policyToSave(null), 1L));
        assertNotNull(service.savePolicy(policyToSave("[]"), 1L));

        assertThrows(BusinessException.class,
                () -> service.savePolicy(policyToSave("[{\"key\":\"a\",\"weight\":60},{\"key\":\"a\",\"weight\":40}]"), 1L),
                "重复 key 会让明细互相覆盖，必须拒绝");
        assertThrows(BusinessException.class,
                () -> service.savePolicy(policyToSave("[{\"key\":\"a\",\"weight\":120}]"), 1L));
        assertThrows(BusinessException.class,
                () -> service.savePolicy(policyToSave("[{\"key\":\"a\",\"weight\":-5}]"), 1L),
                "负权重可把总评压成负分");
        assertThrows(BusinessException.class,
                () -> service.savePolicy(policyToSave("[{\"name\":\"无标识\",\"weight\":60}]"), 1L));
        assertThrows(BusinessException.class,
                () -> service.savePolicy(policyToSave("这不是JSON"), 1L),
                "坏配置若入库，computeFinal 会静默把全班总评算成 0");
        assertThrows(BusinessException.class,
                () -> service.savePolicy(policyToSave("{\"key\":\"a\"}"), 1L),
                "对象而非数组同样要拒");
    }

    @Test
    @DisplayName("savePolicy 权重合计强制 100%（0.01 容差），带中文提示")
    void save_weightSumMustBe100() {
        assertNotNull(service.savePolicy(policyToSave(
                "[{\"key\":\"usual\",\"weight\":40},{\"key\":\"final\",\"weight\":60}]"), 1L), "配平方案须放行");
        assertNotNull(service.savePolicy(policyToSave(
                "[{\"key\":\"a\",\"weight\":60.5},{\"key\":\"b\",\"weight\":39.5}]"), 1L), "小数配平须放行");

        BusinessException below = assertThrows(BusinessException.class,
                () -> service.savePolicy(policyToSave("[{\"key\":\"a\",\"weight\":60}]"), 1L));
        assertTrue(below.getMessage().contains("100%") && below.getMessage().contains("60"),
                "提示应同时给出「须为 100%」和当前合计，实际=" + below.getMessage());

        assertThrows(BusinessException.class,
                () -> service.savePolicy(policyToSave(
                        "[{\"key\":\"a\",\"weight\":60},{\"key\":\"b\",\"weight\":50}]"), 1L),
                "超额 110% 同样要拒");
        BusinessException tiny = assertThrows(BusinessException.class,
                () -> service.savePolicy(policyToSave(
                        "[{\"key\":\"a\",\"weight\":50},{\"key\":\"b\",\"weight\":49.9}]"), 1L),
                "99.9% 超出 0.01 容差，属真实缺额");
        assertTrue(tiny.getMessage().contains("99.9"), "实际=" + tiny.getMessage());
    }

    @Test
    @DisplayName("savePolicy 校验失败时不得写库")
    void save_invalidDoesNotPersist() {
        assertThrows(BusinessException.class,
                () -> service.savePolicy(policyToSave("[{\"key\":\"a\",\"weight\":999}]"), 1L));
        verify(policyMapper, never()).insert(any(GradePolicy.class));
        verify(policyMapper, never()).updateById(any(GradePolicy.class));
    }

    // ===================== courseReport =====================

    @Test
    @DisplayName("courseReport 按学生生成成绩排名")
    void report_ok() {
        GradeItem a = new GradeItem();
        a.setStudentId(1L);
        a.setComponentKey("exam");
        a.setScore(new BigDecimal("80"));
        GradeItem b = new GradeItem();
        b.setStudentId(2L);
        b.setComponentKey("hw");
        b.setScore(new BigDecimal("90"));
        when(policyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(policyWithComps()));
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(a, b));
        // 只返回 1 号学生的账号：2 号命中「账号缺失」分支，姓名须退回学生ID而不是留空
        User u1 = new User();
        u1.setId(1L);
        u1.setFullName("张三");
        when(userService.listByIds(anyCollection())).thenReturn(List.of(u1));

        List<FinalGradeVO> r = service.courseReport(10L);
        assertEquals(2, r.size());
        // 两人得分相同、排序不稳定，按 studentId 取行再断言姓名
        assertEquals("张三", r.stream().filter(v -> v.getStudentId() == 1L).findFirst().orElseThrow().getStudentName());
        assertEquals("学生 #2", r.stream().filter(v -> v.getStudentId() == 2L).findFirst().orElseThrow().getStudentName());
    }

    @Test
    @DisplayName("courseReport 姓名为空时退回学生ID")
    void report_blankNameFallsBackToId() {
        GradeItem a = new GradeItem();
        a.setStudentId(5L);
        a.setComponentKey("exam");
        a.setScore(new BigDecimal("70"));
        when(policyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(policyWithComps()));
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(a));
        User blank = new User();
        blank.setId(5L);
        blank.setFullName("   ");
        when(userService.listByIds(anyCollection())).thenReturn(List.of(blank));

        assertEquals("学生 #5", service.courseReport(10L).get(0).getStudentName());
    }

    @Test
    @DisplayName("courseReport 无成绩明细时返回空报表且不查库")
    void report_noItemsSkipsUserLookup() {
        when(policyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(policyWithComps()));
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertTrue(service.courseReport(10L).isEmpty());
        verify(userService, never()).listByIds(anyCollection());
    }

    @Test
    @DisplayName("computeFinal 单学生查询也带姓名")
    void computeFinal_fillsStudentName() {
        GradeItem a = new GradeItem();
        a.setStudentId(9L);
        a.setComponentKey("exam");
        a.setScore(new BigDecimal("88"));
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(a));
        User u = new User();
        u.setId(9L);
        u.setFullName("李四");
        when(userService.listByIds(anyCollection())).thenReturn(List.of(u));

        FinalGradeVO vo = service.computeFinal(7L, 9L);
        assertEquals("李四", vo.getStudentName());
        assertEquals(new BigDecimal("88.00"), vo.getFinalScore());
    }

    @Test
    @DisplayName("courseReport 无方案抛异常")
    void report_noPolicy() {
        assertThrows(BusinessException.class, () -> service.courseReport(10L));
    }
}
