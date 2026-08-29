package com.aicourse.service.impl;

import com.aicourse.entity.GradeItem;
import com.aicourse.entity.GradePolicy;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.GradeItemMapper;
import com.aicourse.mapper.GradePolicyMapper;
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
 * save/list/getById/updateById 走 policyMapper；upsertItem/computeFinal/courseReport 额外走 itemMapper.selectOne/selectList。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GradeServiceImplTest {

    @Mock private GradePolicyMapper policyMapper;
    @Mock private GradeItemMapper itemMapper;

    private GradeServiceImpl service;

    private static final String COMPS =
            "[{\"key\":\"exam\",\"name\":\"期末考试\",\"weight\":60},{\"key\":\"hw\",\"name\":\"作业\",\"weight\":40}]";

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GradePolicy.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GradeItem.class);

        service = new GradeServiceImpl(policyMapper, itemMapper);
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
        when(policyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(policyWithComps()));
        assertNotNull(service.getPolicy(10L));
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

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> r = service.computeFinal(7L, 1L);
        assertEquals(new BigDecimal("84.00"), r.get("finalScore"));
        @SuppressWarnings("unchecked")
        List<?> detail = (List<?>) r.get("components");
        assertEquals(2, detail.size());
    }

    @Test
    @DisplayName("computeFinal 方案不存在抛异常")
    void compute_noPolicy() {
        when(policyMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.computeFinal(7L, 1L));
    }

    @Test
    @DisplayName("computeFinal components 为空则零分")
    void compute_blankComponents() {
        GradePolicy p = new GradePolicy();
        p.setId(7L);
        p.setCourseId(10L);
        p.setComponents("");
        when(policyMapper.selectById(anyLong())).thenReturn(p);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> r = service.computeFinal(7L, 1L);
        assertEquals(new BigDecimal("0.00"), r.get("finalScore"));
        @SuppressWarnings("unchecked")
        List<?> detail = (List<?>) r.get("components");
        assertTrue(detail.isEmpty());
    }

    @Test
    @DisplayName("computeFinal components 非法 JSON 降级为空")
    void compute_badComponents() {
        GradePolicy p = new GradePolicy();
        p.setId(7L);
        p.setCourseId(10L);
        p.setComponents("not-json");
        when(policyMapper.selectById(anyLong())).thenReturn(p);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> r = service.computeFinal(7L, 1L);
        assertEquals(new BigDecimal("0.00"), r.get("finalScore"));
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

        List<?> r = service.courseReport(10L);
        assertEquals(2, r.size());
    }

    @Test
    @DisplayName("courseReport 无方案抛异常")
    void report_noPolicy() {
        assertThrows(BusinessException.class, () -> service.courseReport(10L));
    }
}
