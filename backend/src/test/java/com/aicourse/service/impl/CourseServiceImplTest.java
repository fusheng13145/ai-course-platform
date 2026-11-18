package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.TeachingClass;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.QuestionMapper;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.service.component.org.CourseDefaultTeachingClassSync;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 纯 Mockito 单元测试：覆盖 CourseServiceImpl 全部写/读编排逻辑与错误分支。
 * 不加载 Spring 上下文，@Cacheable/@CacheEvict 注解在单元测试中不起作用，直接验证方法体。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseServiceImplTest {

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private TeachingClassMapper teachingClassMapper;

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private com.aicourse.service.CourseDefinitionService definitionService;

    @Mock
    private com.aicourse.service.UserService userService;

    @Mock
    private com.aicourse.service.SemesterService semesterService;

    private CourseServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Course.class);
        service = new CourseServiceImpl(new CourseDefaultTeachingClassSync(teachingClassMapper),
                semesterService, questionMapper, definitionService, userService);
        ReflectionTestUtils.setField(service, "baseMapper", courseMapper);
    }

    @Test
    @DisplayName("getById 委托 baseMapper.selectById 命中")
    void getById_hit() {
        Course c = new Course();
        c.setId(1L);
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(c);
        assertEquals(c, service.getById(1L));
    }

    @Test
    @DisplayName("getById 返回 null")
    void getById_null() {
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(null);
        assertNull(service.getById(2L));
    }

    @Test
    @DisplayName("list 委托 baseMapper.selectList")
    void list_delegates() {
        Course c = new Course();
        when(courseMapper.selectList(any())).thenReturn(List.of(c));
        List<Course> r = service.list();
        assertEquals(1, r.size());
    }

    @Test
    @DisplayName("list 空结果")
    void list_empty() {
        when(courseMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.list().isEmpty());
    }

    @Test
    @DisplayName("save 成功返回 true")
    void save_ok() {
        Course c = new Course();
        when(courseMapper.insert(any(Course.class))).thenReturn(1);
        assertTrue(service.save(c));
        verify(courseMapper).insert(any(Course.class));
    }

    @Test
    @DisplayName("updateById 成功返回 true")
    void updateById_ok() {
        Course c = new Course();
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        assertTrue(service.updateById(c));
    }

    @Test
    @DisplayName("completeCourse 结课写入时间并关闭教学班")
    void completeCourse_closesClasses() {
        Course course = new Course();
        course.setId(1L);
        course.setTeacherId(7L);
        course.setDefinitionId(10L);
        course.setStatus("ACTIVE");
        TeachingClass section = new TeachingClass();
        section.setId(9L);
        section.setCourseId(1L);
        section.setStatus("ACTIVE");
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(course);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(section));

        Course result = service.completeCourse(1L, 7L, false);

        assertEquals("INACTIVE", result.getStatus());
        assertFalse(result.getIsActive());
        assertNotNull(result.getCompletedAt());
        assertEquals("CLOSED", section.getStatus());
    }

    @Test
    @DisplayName("removeById 成功返回 true")
    void removeById_ok() {
        when(courseMapper.deleteById(any(Serializable.class))).thenReturn(1);
        assertTrue(service.removeById(5L));
    }

    @Test
    @DisplayName("remove(wrapper) 成功返回 true")
    void remove_wrapper() {
        when(courseMapper.delete(any(Wrapper.class))).thenReturn(1);
        assertTrue(service.remove(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Course>()));
    }

    @Test
    @DisplayName("createCourse 设置 teacherId 并保存")
    void createCourse_ok() {
        com.aicourse.entity.User teacher = new com.aicourse.entity.User();
        teacher.setId(7L);
        teacher.setRole("TEACHER");
        when(userService.getById(7L)).thenReturn(teacher);

        com.aicourse.entity.CourseDefinition definition = new com.aicourse.entity.CourseDefinition();
        definition.setId(10L);
        definition.setCode("CS101");
        definition.setName("计算机导论");
        definition.setCredits(java.math.BigDecimal.valueOf(3));
        when(definitionService.resolveFromCourse(any(), eq(7L))).thenReturn(10L);
        when(definitionService.requireActive(10L)).thenReturn(definition);

        com.aicourse.entity.Semester semester = new com.aicourse.entity.Semester();
        semester.setId(3L);
        semester.setName("2026-2027-1");
        semester.setStartDate(java.time.LocalDate.of(2026, 9, 1));
        semester.setEndDate(java.time.LocalDate.of(2027, 1, 15));
        when(semesterService.listByStatus("ACTIVE")).thenReturn(List.of(semester));
        when(semesterService.getById(3L)).thenReturn(semester);
        when(courseMapper.selectCount(any())).thenReturn(0L);
        when(courseMapper.insert(any(Course.class))).thenReturn(1);

        Course c = new Course();
        c.setSemesterId(3L);
        Course r = service.createCourse(c, 7L);
        assertEquals(7L, r.getTeacherId());
        assertEquals(10L, r.getDefinitionId());
        verify(courseMapper).insert(any(Course.class));
    }

    @Test
    @DisplayName("updateCourse 课程不存在抛异常")
    void updateCourse_notFound() {
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> service.updateCourse(1L, new Course(), 2L, false));
    }

    @Test
    @DisplayName("updateCourse 无权限抛异常")
    void updateCourse_noPermission() {
        Course exist = new Course();
        exist.setId(1L);
        exist.setTeacherId(99L);
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(exist);
        assertThrows(BusinessException.class,
                () -> service.updateCourse(1L, new Course(), 2L, false));
    }

    @Test
    @DisplayName("updateCourse 管理员可修改")
    void updateCourse_admin() {
        Course exist = new Course();
        exist.setId(1L);
        exist.setTeacherId(99L);
        Course updated = new Course();
        updated.setId(1L);
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(exist).thenReturn(updated);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        Course r = service.updateCourse(1L, new Course(), 2L, true);
        assertNotNull(r);
        verify(courseMapper).updateById(any(Course.class));
    }

    @Test
    @DisplayName("updateCourse 属主可修改")
    void updateCourse_owner() {
        Course exist = new Course();
        exist.setId(1L);
        exist.setTeacherId(2L);
        Course updated = new Course();
        updated.setId(1L);
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(exist).thenReturn(updated);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        Course r = service.updateCourse(1L, new Course(), 2L, false);
        assertNotNull(r);
    }

    @Test
    @DisplayName("updateCourseById 直接更新并返回")
    void updateCourseById_ok() {
        Course updated = new Course();
        updated.setId(1L);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(updated);
        Course r = service.updateCourseById(1L, new Course());
        assertEquals(1L, r.getId());
    }

    @Test
    @DisplayName("deleteCourseById 删除")
    void deleteCourseById_ok() {
        when(courseMapper.deleteById(any(Serializable.class))).thenReturn(1);
        service.deleteCourseById(1L);
        verify(courseMapper).deleteById(any(Serializable.class));
    }

    @Test
    @DisplayName("deleteCourseById 有题目的课程拒绝删除（V27 FK RESTRICT 前置业务提示）")
    void deleteCourseById_withQuestions() {
        when(questionMapper.selectCount(any())).thenReturn(2L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteCourseById(1L));
        assertTrue(ex.getMessage().contains("2 道题目"));
        verify(courseMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    @DisplayName("approveCourse 不存在抛异常")
    void approveCourse_notFound() {
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.approveCourse(1L));
    }

    @Test
    @DisplayName("approveCourse 成功置为 APPROVED/ACTIVE")
    void approveCourse_ok() {
        Course c = new Course();
        c.setId(1L);
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(c);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        Course r = service.approveCourse(1L);
        assertEquals("APPROVED", r.getApprovalStatus());
        assertEquals("ACTIVE", r.getStatus());
    }

    @Test
    @DisplayName("approveCourse(id, operatorId) 落审批人")
    void approveCourse_withOperator() {
        Course c = new Course();
        c.setId(1L);
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(c);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        Course r = service.approveCourse(1L, 8L);
        assertEquals("APPROVED", r.getApprovalStatus());
        assertEquals(8L, r.getApprovedBy());
    }

    @Test
    @DisplayName("approveCourse(id, operatorId) 已通过时不覆盖审批人")
    void approveCourse_alreadyApproved_keepsOperator() {
        Course c = new Course();
        c.setId(1L);
        c.setApprovalStatus("APPROVED");
        c.setStatus("ACTIVE");
        c.setApprovedBy(9L);
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(c);
        Course r = service.approveCourse(1L, 8L);
        assertEquals(9L, r.getApprovedBy());
        verify(courseMapper, never()).updateById(any(Course.class));
    }

    @Test
    @DisplayName("resubmitCourse 非驳回状态抛异常")
    void resubmitCourse_notRejected() {
        Course c = new Course();
        c.setId(1L);
        c.setApprovalStatus("PENDING");
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(c);
        assertThrows(BusinessException.class, () -> service.resubmitCourse(1L));
    }

    @Test
    @DisplayName("resubmitCourse REJECTED -> PENDING 并清空审批字段")
    void resubmitCourse_ok() {
        Course c = new Course();
        c.setId(1L);
        c.setApprovalStatus("REJECTED");
        c.setApprovalComment("资料不全");
        c.setApprovedBy(9L);
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(c);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        Course r = service.resubmitCourse(1L);
        assertEquals("PENDING", r.getApprovalStatus());
        assertEquals("ACTIVE", r.getStatus());
        assertNull(r.getApprovalComment());
        assertNull(r.getApprovedBy());
        assertNull(r.getApprovalTime());
    }

    @Test
    @DisplayName("toggleCourseStatus 不存在抛异常")
    void toggleCourseStatus_notFound() {
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.toggleCourseStatus(1L));
    }

    @Test
    @DisplayName("toggleCourseStatus ACTIVE -> INACTIVE")
    void toggleCourseStatus_toInactive() {
        Course c = new Course();
        c.setId(1L);
        c.setStatus("ACTIVE");
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(c);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        Course r = service.toggleCourseStatus(1L);
        assertEquals("INACTIVE", r.getStatus());
    }

    @Test
    @DisplayName("toggleCourseStatus 非 ACTIVE -> ACTIVE")
    void toggleCourseStatus_toActive() {
        Course c = new Course();
        c.setId(1L);
        c.setStatus("INACTIVE");
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(c);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        Course r = service.toggleCourseStatus(1L);
        assertEquals("ACTIVE", r.getStatus());
    }

    @Test
    @DisplayName("rejectCourse 不存在抛异常")
    void rejectCourse_notFound() {
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.rejectCourse(1L, "bad"));
    }

    @Test
    @DisplayName("rejectCourse 带原因写入 comment")
    void rejectCourse_withReason() {
        Course c = new Course();
        c.setId(1L);
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(c);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        Course r = service.rejectCourse(1L, "不合规");
        assertEquals("REJECTED", r.getApprovalStatus());
        assertEquals("不合规", r.getApprovalComment());
    }

    @Test
    @DisplayName("rejectCourse 原因为 null 不写 comment")
    void rejectCourse_nullReason() {
        Course c = new Course();
        c.setId(1L);
        when(courseMapper.selectById(any(Serializable.class))).thenReturn(c);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        Course r = service.rejectCourse(1L, null);
        assertEquals("REJECTED", r.getApprovalStatus());
        assertNull(r.getApprovalComment());
    }
}
