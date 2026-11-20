package com.aicourse.service.component.org;

import com.aicourse.entity.Course;
import com.aicourse.entity.TeachingClass;
import com.aicourse.mapper.TeachingClassMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CourseDefaultTeachingClassSync 覆盖率补测（I-04 第七批）。
 *
 * <p>覆盖空课程短路、定义模型审批门禁、占位生成与计划数截断、
 * 历史单班同步、关闭课程、状态映射与默认/多班命名编号。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseDefaultTeachingClassSyncCoverageTest {

    @Mock private TeachingClassMapper teachingClassMapper;
    private CourseDefaultTeachingClassSync sync;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), TeachingClass.class);
    }

    @BeforeEach
    void setUp() {
        sync = new CourseDefaultTeachingClassSync(teachingClassMapper);
    }

    private Course course(Long id, Long teacherId, String name, String status, String approval) {
        Course c = new Course();
        c.setId(id);
        c.setTeacherId(teacherId);
        c.setName(name);
        c.setCode("CN");
        c.setStatus(status);
        c.setApprovalStatus(approval);
        c.setMaxStudents(100);
        return c;
    }

    private TeachingClass tc(Long id, String name, String code, String status) {
        TeachingClass t = new TeachingClass();
        t.setId(id);
        t.setName(name);
        t.setCode(code);
        t.setStatus(status);
        return t;
    }

    @Test
    @DisplayName("sync：空课程/空 ID/空教师/空名称直接返回，定义模型待审或驳回不建班")
    void syncShortCircuits() {
        sync.sync(null);
        sync.sync(course(null, 1L, "C", "ACTIVE", "APPROVED"));
        sync.sync(course(1L, null, "C", "ACTIVE", "APPROVED"));
        sync.sync(course(1L, 1L, null, "ACTIVE", "APPROVED"));
        verifyNoInteractions(teachingClassMapper);

        Course pending = course(1L, 1L, "C", "ACTIVE", "pending");
        pending.setDefinitionId(2L);
        sync.sync(pending);
        Course rejected = course(2L, 1L, "C", "ACTIVE", "Rejected");
        rejected.setDefinitionId(3L);
        sync.sync(rejected);
        verify(teachingClassMapper, never()).insert(any(TeachingClass.class));
    }

    @Test
    @DisplayName("sync：定义模型无班级按 plannedSections 生成 DRAFT，计划数 null/0 回退 1，上限 50")
    void syncDefinitionEmpty() {
        Course c = course(1L, 10L, "数据结构", "ACTIVE", "APPROVED");
        c.setDefinitionId(2L);
        c.setPlannedSections(null);
        when(teachingClassMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        sync.sync(c);

        ArgumentCaptor<TeachingClass> captor = ArgumentCaptor.forClass(TeachingClass.class);
        verify(teachingClassMapper).insert(captor.capture());
        TeachingClass created = captor.getValue();
        assertEquals("数据结构 默认教学班", created.getName());
        assertEquals("CN-DEFAULT", created.getCode());
        assertEquals("DRAFT", created.getStatus());
        assertEquals(10L, created.getLeadTeacherId());
        assertEquals(100, created.getCapacity());

        c.setPlannedSections(3);
        sync.sync(c);
        ArgumentCaptor<TeachingClass> multi = ArgumentCaptor.forClass(TeachingClass.class);
        verify(teachingClassMapper, times(4)).insert(multi.capture());
        assertEquals("数据结构 教学班 1", multi.getAllValues().get(1).getName());
        assertEquals("CN-01", multi.getAllValues().get(1).getCode());
        assertEquals("数据结构 教学班 3", multi.getAllValues().get(3).getName());
        assertEquals("CN-03", multi.getAllValues().get(3).getCode());

        c.setPlannedSections(60);
        sync.sync(c);
        verify(teachingClassMapper, times(4 + 50)).insert(any(TeachingClass.class));
    }

    @Test
    @DisplayName("sync：定义模型已有班级少于计划时补齐，课程停用时非 DRAFT 回落 DRAFT")
    void syncDefinitionTopUp() {
        Course c = course(1L, 10L, "网络", "DRAFT", "APPROVED");
        c.setDefinitionId(2L);
        c.setPlannedSections(3);
        when(teachingClassMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(tc(1L, "网络 教学班 1", "CN-01", "ACTIVE")));
        when(teachingClassMapper.insert(any(TeachingClass.class))).thenReturn(1);
        when(teachingClassMapper.updateById(any(TeachingClass.class))).thenReturn(1);

        sync.sync(c);
        verify(teachingClassMapper, times(2)).insert(any(TeachingClass.class));
        ArgumentCaptor<TeachingClass> update = ArgumentCaptor.forClass(TeachingClass.class);
        verify(teachingClassMapper).updateById(update.capture());
        assertEquals("DRAFT", update.getValue().getStatus());
        assertNotNull(update.getValue().getUpdatedAt());

        c.setStatus("ACTIVE");
        clearInvocations(teachingClassMapper);
        sync.sync(c);
        verify(teachingClassMapper, never()).updateById(any(TeachingClass.class));
    }

    @Test
    @DisplayName("sync：历史模型无班级只建 1 个默认班，状态跟随课程")
    void syncLegacyEmpty() {
        Course active = course(1L, 10L, "旧课", "ACTIVE", null);
        when(teachingClassMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        sync.sync(active);
        ArgumentCaptor<TeachingClass> captor = ArgumentCaptor.forClass(TeachingClass.class);
        verify(teachingClassMapper).insert(captor.capture());
        assertEquals("旧课 默认教学班", captor.getValue().getName());
        assertEquals("ACTIVE", captor.getValue().getStatus());

        Course draft = course(2L, 10L, "停课", "DRAFT", null);
        reset(teachingClassMapper);
        when(teachingClassMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        sync.sync(draft);
        ArgumentCaptor<TeachingClass> draftCaptor = ArgumentCaptor.forClass(TeachingClass.class);
        verify(teachingClassMapper).insert(draftCaptor.capture());
        assertEquals("DRAFT", draftCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("syncLegacyDefault：默认班名称/编号/状态漂移才更新，找不到默认班不动")
    void syncLegacyDrift() {
        Course c = course(1L, 10L, "旧课", "ACTIVE", null);
        when(teachingClassMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        TeachingClass def = tc(1L, "旧名", "OLD-DEFAULT", "DRAFT");
        when(teachingClassMapper.selectList(any(Wrapper.class))).thenReturn(List.of(def));
        when(teachingClassMapper.updateById(any(TeachingClass.class))).thenReturn(1);
        sync.sync(c);
        assertEquals("旧课 默认教学班", def.getName());
        assertEquals("CN-DEFAULT", def.getCode());
        assertEquals("ACTIVE", def.getStatus());
        verify(teachingClassMapper).updateById(def);

        TeachingClass aligned = tc(2L, "旧课 默认教学班", "CN-DEFAULT", "ACTIVE");
        when(teachingClassMapper.selectList(any(Wrapper.class))).thenReturn(List.of(aligned));
        sync.sync(c);
        verify(teachingClassMapper, never()).updateById(aligned);

        TeachingClass other = tc(3L, "教师自建班", "T1", "ACTIVE");
        when(teachingClassMapper.selectList(any(Wrapper.class))).thenReturn(List.of(other));
        sync.sync(c);
        verify(teachingClassMapper, never()).updateById(other);
    }

    @Test
    @DisplayName("close：空课程/空 ID 直接返回，非 CLOSED 批量关闭，已 CLOSED 跳过")
    void close() {
        sync.close(null);
        sync.close(course(null, 1L, "C", "ACTIVE", "APPROVED"));
        verifyNoInteractions(teachingClassMapper);

        when(teachingClassMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(tc(1L, "A", "C1", "ACTIVE"), tc(2L, "B", "C2", "CLOSED")));
        when(teachingClassMapper.updateById(any(TeachingClass.class))).thenReturn(1);
        sync.close(course(1L, 1L, "C", "ACTIVE", "APPROVED"));
        verify(teachingClassMapper, times(1)).updateById(any(TeachingClass.class));
    }

    @Test
    @DisplayName("newClass：课程编号为空时编号为 null，计划 1 个用默认后缀")
    void newClassWithoutCode() {
        Course c = course(1L, 10L, "无编号课", "ACTIVE", "APPROVED");
        c.setCode(null);
        when(teachingClassMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        sync.sync(c);
        ArgumentCaptor<TeachingClass> captor = ArgumentCaptor.forClass(TeachingClass.class);
        verify(teachingClassMapper).insert(captor.capture());
        assertNull(captor.getValue().getCode());
    }
}
