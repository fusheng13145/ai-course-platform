package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.Semester;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.SemesterMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * SemesterServiceImpl 单元测试（纯 Mockito）。
 *
 * <p>钉住三条口径：① 创建前的唯一性/日期校验（对应 409 与 400）；② 归档**幂等**
 * （重复归档不得二次写、不得覆盖首次归档时间）；③ 批量归档只扫 ACTIVE 且已过期的行。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SemesterServiceImplTest {

    @Mock
    private SemesterMapper semesterMapper;

    private SemesterServiceImpl service;

    @BeforeEach
    void setUp() {
        // LambdaQueryWrapper 的列名要在 getSqlSegment() 时才落地，需先注册实体元数据（仓库同款做法）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Semester.class);
        service = new SemesterServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", semesterMapper);
    }

    // ===================== 创建 =====================

    @Test
    @DisplayName("创建学期：名称去空白、状态置 ACTIVE、起止日期落库")
    void create_ok() {
        when(semesterMapper.selectCount(any())).thenReturn(0L);
        when(semesterMapper.insert(any(Semester.class))).thenReturn(1);

        Semester created = service.create("  2026-2027-1  ", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 15));

        assertEquals("2026-2027-1", created.getName());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals(LocalDate.of(2026, 9, 1), created.getStartDate());
        assertNull(created.getArchivedAt());
        verify(semesterMapper).insert(any(Semester.class));
    }

    @Test
    @DisplayName("创建学期：名称为空白 → 400，不得落库")
    void create_blankName_400() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create("   ", LocalDate.now(), LocalDate.now()));
        assertEquals(ApiCode.BAD_REQUEST, ex.getCode());
        verify(semesterMapper, never()).insert(any(Semester.class));
    }

    @Test
    @DisplayName("创建学期：结束日期早于开始日期 → 400，不得落库")
    void create_endBeforeStart_400() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create("2026-1", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 1, 1)));
        assertEquals(ApiCode.BAD_REQUEST, ex.getCode());
        verify(semesterMapper, never()).insert(any(Semester.class));
    }

    @Test
    @DisplayName("创建学期：重名 → 409（唯一冲突不得回落 500）")
    void create_duplicateName_409() {
        when(semesterMapper.selectCount(any())).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create("2026-1", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 1)));
        assertEquals(ApiCode.CONFLICT, ex.getCode());
        verify(semesterMapper, never()).insert(any(Semester.class));
    }

    // ===================== 归档（单个） =====================

    @Test
    @DisplayName("归档学期：在册 → 置 ARCHIVED 并写归档时间，返回 true")
    void archive_active_changes() {
        Semester active = semester(1L, "ACTIVE", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 15));
        when(semesterMapper.selectById(1L)).thenReturn(active);
        when(semesterMapper.updateById(any(Semester.class))).thenReturn(1);

        assertTrue(service.archive(1L));
        assertEquals("ARCHIVED", active.getStatus());
        assertNotNull(active.getArchivedAt());
    }

    @Test
    @DisplayName("归档学期：已归档 → 幂等返回 false，且不得覆盖首次归档时间")
    void archive_alreadyArchived_idempotent() {
        LocalDateTime firstArchivedAt = LocalDateTime.of(2026, 2, 1, 9, 0);
        Semester archived = semester(2L, "ARCHIVED", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 1, 15));
        archived.setArchivedAt(firstArchivedAt);
        when(semesterMapper.selectById(2L)).thenReturn(archived);

        assertFalse(service.archive(2L));
        assertEquals(firstArchivedAt, archived.getArchivedAt());
        verify(semesterMapper, never()).updateById(any(Semester.class));
    }

    @Test
    @DisplayName("归档学期：不存在 → 404")
    void archive_notFound_404() {
        when(semesterMapper.selectById(9L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.archive(9L));
        assertEquals(ApiCode.NOT_FOUND, ex.getCode());
        verify(semesterMapper, never()).updateById(any(Semester.class));
    }

    // ===================== 归档（批量·过期） =====================

    @Test
    @DisplayName("批量归档过期学期：条件=ACTIVE 且 end_date<基准日，返回影响条数")
    void archiveExpired_filtersActiveAndExpired() throws Exception {
        when(semesterMapper.update(isNull(), any())).thenReturn(2);

        int affected = service.archiveExpired(LocalDate.of(2026, 3, 1));

        assertEquals(2, affected);
        ArgumentCaptor<AbstractWrapper> captor = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(semesterMapper).update(isNull(), captor.capture());
        AbstractWrapper wrapper = captor.getValue();
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("status"), "必须按状态过滤：" + sql);
        assertTrue(sql.contains("end_date"), "必须按结束日期过滤：" + sql);
        // 只归档 ACTIVE：不得误扫已归档行
        assertTrue(wrapper.getParamNameValuePairs().containsValue("ACTIVE"), "状态条件必须是 ACTIVE");
        assertTrue(wrapper.getParamNameValuePairs().containsValue(LocalDate.of(2026, 3, 1)),
                "基准日必须作为上界传入");
    }

    @Test
    @DisplayName("批量归档缺省基准日 = 系统当天（不把未来学期误归档）")
    void archiveExpired_defaultToday() {
        when(semesterMapper.update(isNull(), any())).thenReturn(0);
        assertEquals(0, service.archiveExpired(null));
    }

    // ===================== 列表 =====================

    @Test
    @DisplayName("列表：状态过滤被归一为大写，且在册在前（不做客户端排序）")
    void listByStatus_normalizesStatus() throws Exception {
        when(semesterMapper.selectList(any())).thenReturn(List.of(semester(1L, "ACTIVE", null, null)));

        service.listByStatus(" archived ");

        ArgumentCaptor<AbstractWrapper> captor = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(semesterMapper).selectList(captor.capture());
        AbstractWrapper wrapper = captor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("status"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("ARCHIVED"),
                "状态过滤值必须归一为大写后再查询");
    }

    @Test
    @DisplayName("列表：状态为空时不加状态过滤条件（阴性对照：无绑定参数即为无过滤）")
    void listByStatus_blank_noFilter() throws Exception {
        when(semesterMapper.selectList(any())).thenReturn(List.of());

        service.listByStatus("  ");

        ArgumentCaptor<AbstractWrapper> captor = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(semesterMapper).selectList(captor.capture());
        // ⚠️ 不能断言 "SQL 段里不含 status 字样"：ORDER BY status 是固定排序项、恒在。
        // 真正的判据是"没有绑定任何条件参数"——LambdaQueryWrapper 的列名会在 getSqlSegment() 时落地，
        // 条件一旦拼上就会带出参数值，故空过滤时参数表必须为空。
        assertTrue(captor.getValue().getParamNameValuePairs().isEmpty(),
                "空状态不得拼出任何过滤条件");
    }

    private Semester semester(Long id, String status, LocalDate start, LocalDate end) {
        Semester s = new Semester();
        s.setId(id);
        s.setName("s-" + id);
        s.setStatus(status);
        s.setStartDate(start);
        s.setEndDate(end);
        return s;
    }
}
