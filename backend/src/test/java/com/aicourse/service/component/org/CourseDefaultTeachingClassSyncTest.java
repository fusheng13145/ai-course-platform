package com.aicourse.service.component.org;

import com.aicourse.entity.Course;
import com.aicourse.entity.TeachingClass;
import com.aicourse.mapper.TeachingClassMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课程默认教学班同步器单元测试（B-12／P-08 WP4 判据 9A 的运行态延伸）。
 * <p>
 * 起因：2026-11-06 真栈 e2e 实测「教师建课→审批通过→学生选课」返回
 * 409 {@code 课程暂无启用的教学班，暂不可报名}——V14 的回填只覆盖存量课程，
 * 新课程永远没有教学班。本组用例把「落库即有默认班、状态跟随课程」钉成机械断言。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("默认教学班：CourseDefaultTeachingClassSync")
class CourseDefaultTeachingClassSyncTest {

    @Mock
    private TeachingClassMapper teachingClassMapper;

    private CourseDefaultTeachingClassSync sync;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), TeachingClass.class);
        sync = new CourseDefaultTeachingClassSync(teachingClassMapper);
    }

    @Test
    @DisplayName("新课程无任何教学班：插入恰 1 个 ACTIVE 默认班，字段逐项对齐 V14 回填")
    void insertsDefaultClassForNewActiveCourse() {
        givenClasses();

        sync.sync(course(500L, "数据结构", "CS500", "ACTIVE", 7L, 60));

        ArgumentCaptor<TeachingClass> captor = ArgumentCaptor.forClass(TeachingClass.class);
        verify(teachingClassMapper).insert(captor.capture());
        TeachingClass created = captor.getValue();
        assertThat(created.getCourseId()).isEqualTo(500L);
        assertThat(created.getName()).isEqualTo("数据结构 默认教学班");
        assertThat(created.getCode()).isEqualTo("CS500-DEFAULT");
        assertThat(created.getLeadTeacherId()).isEqualTo(7L);
        assertThat(created.getCapacity()).isEqualTo(60);
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(created.getCreatedBy()).isEqualTo(7L);
        verify(teachingClassMapper, never()).updateById(any(TeachingClass.class));
    }

    @Test
    @DisplayName("停用课程：默认班落 DRAFT，不产生可报名入口（对齐 V14 的 CASE 与 D8）")
    void inactiveCourseYieldsDraftClass() {
        givenClasses();

        sync.sync(course(501L, "编译原理", "CS501", "INACTIVE", 7L, null));

        ArgumentCaptor<TeachingClass> captor = ArgumentCaptor.forClass(TeachingClass.class);
        verify(teachingClassMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("已存在默认班但状态漂移：只改状态，不新增第二个默认班")
    void updatesStatusOfExistingDefaultClassWithoutInserting() {
        TeachingClass existing = new TeachingClass();
        existing.setId(9001L);
        existing.setCourseId(500L);
        existing.setName("数据结构 默认教学班");
        existing.setCode("CS500-DEFAULT");
        existing.setStatus("DRAFT");
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(existing));

        sync.sync(course(500L, "数据结构", "CS500", "ACTIVE", 7L, 60));

        verify(teachingClassMapper, never()).insert(any(TeachingClass.class));
        ArgumentCaptor<TeachingClass> captor = ArgumentCaptor.forClass(TeachingClass.class);
        verify(teachingClassMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(9001L);
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("课程改名：默认班随名称漂移（uk_course_name 上不会出现两行默认班）")
    void renamesDefaultClassWhenCourseIsRenamed() {
        TeachingClass existing = new TeachingClass();
        existing.setId(9002L);
        existing.setCourseId(500L);
        existing.setName("旧名 默认教学班");
        existing.setCode("OLD-DEFAULT");
        existing.setStatus("ACTIVE");
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(existing));

        sync.sync(course(500L, "新名", "CS500", "ACTIVE", 7L, 60));

        verify(teachingClassMapper, never()).insert(any(TeachingClass.class));
        ArgumentCaptor<TeachingClass> captor = ArgumentCaptor.forClass(TeachingClass.class);
        verify(teachingClassMapper).updateById(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("新名 默认教学班");
        assertThat(captor.getValue().getCode()).isEqualTo("CS500-DEFAULT");
    }

    @Test
    @DisplayName("教师已自建教学班：不制造第二个默认班（幂等，零写操作）")
    void leavesCourseAloneWhenTeacherAlreadyCreatedClasses() {
        TeachingClass manual = new TeachingClass();
        manual.setId(9003L);
        manual.setCourseId(500L);
        manual.setName("周五班");
        manual.setCode("CS500-A");
        manual.setStatus("ACTIVE");
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(manual));

        sync.sync(course(500L, "数据结构", "CS500", "ACTIVE", 7L, 60));

        verify(teachingClassMapper, never()).insert(any(TeachingClass.class));
        verify(teachingClassMapper, never()).updateById(any(TeachingClass.class));
    }

    @Test
    @DisplayName("状态无漂移：零写操作（重复审批/重复更新不得刷 updated_at）")
    void writesNothingWhenAlreadyInSync() {
        TeachingClass existing = new TeachingClass();
        existing.setId(9004L);
        existing.setCourseId(500L);
        existing.setName("数据结构 默认教学班");
        existing.setCode("CS500-DEFAULT");
        existing.setStatus("ACTIVE");
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(existing));

        sync.sync(course(500L, "数据结构", "CS500", "ACTIVE", 7L, 60));

        verify(teachingClassMapper, never()).insert(any(TeachingClass.class));
        verify(teachingClassMapper, never()).updateById(any(TeachingClass.class));
    }

    @Test
    @DisplayName("主讲为空的课程不建班（对齐 V14 的 WHERE teacher_id IS NOT NULL）")
    void skipsWhenCourseHasNoLeadTeacher() {
        givenClasses();

        sync.sync(course(502L, "无主讲", "CS502", "ACTIVE", null, 30));

        verify(teachingClassMapper, never()).insert(any(TeachingClass.class));
        verify(teachingClassMapper, never()).updateById(any(TeachingClass.class));
    }

    @Test
    @DisplayName("局部更新（实体只带 id/status）：跳过，避免派生出 \"null 默认教学班\"")
    void skipsPartialCourseEntityWithoutName() {
        givenClasses();
        Course partial = new Course();
        partial.setId(500L);
        partial.setTeacherId(7L);
        partial.setStatus("ACTIVE");

        sync.sync(partial);

        verify(teachingClassMapper, never()).insert(any(TeachingClass.class));
        verify(teachingClassMapper, never()).updateById(any(TeachingClass.class));
    }

    private void givenClasses() {
        when(teachingClassMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
    }

    private static Course course(Long id, String name, String code, String status,
                                 Long teacherId, Integer maxStudents) {
        Course course = new Course();
        course.setId(id);
        course.setName(name);
        course.setCode(code);
        course.setStatus(status);
        course.setTeacherId(teacherId);
        course.setMaxStudents(maxStudents);
        return course;
    }
}
