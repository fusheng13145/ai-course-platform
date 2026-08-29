package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseEnrollmentMapper;
import com.aicourse.service.CourseService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseEnrollmentServiceImplTest {

    @Mock
    private CourseEnrollmentMapper enrollmentMapper; // baseMapper
    @Mock
    private CourseService courseService;

    private CourseEnrollmentServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), CourseEnrollment.class);
        service = new CourseEnrollmentServiceImpl(courseService);
        ReflectionTestUtils.setField(service, "baseMapper", enrollmentMapper);
    }

    private CourseEnrollment ce(Long id, Long studentId, Long courseId, boolean active) {
        CourseEnrollment e = new CourseEnrollment();
        e.setId(id);
        e.setStudentId(studentId);
        e.setCourseId(courseId);
        e.setIsActive(active);
        return e;
    }

    // ===== enroll =====
    @Test
    @DisplayName("enroll 已报名抛异常")
    void enroll_alreadyActive() {
        when(enrollmentMapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.enroll(1L, 10L));
    }

    @Test
    @DisplayName("enroll 恢复软退课记录")
    void enroll_restoreInactive() {
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        CourseEnrollment inactive = ce(5L, 1L, 10L, false);
        when(enrollmentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(inactive);
        when(enrollmentMapper.updateById(any(CourseEnrollment.class))).thenReturn(1);
        CourseEnrollment r = service.enroll(1L, 10L);
        assertEquals(5L, r.getId());
        assertTrue(r.getIsActive());
        assertEquals("APPROVED", r.getApprovalStatus());
        assertEquals(BigDecimal.ZERO, r.getProgress());
        verify(enrollmentMapper).updateById(any(CourseEnrollment.class));
    }

    @Test
    @DisplayName("enroll OPEN 课程直接通过")
    void enroll_open() {
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        when(enrollmentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        Course course = new Course();
        course.setId(10L);
        course.setEnrollmentType("OPEN");
        when(courseService.getById(10L)).thenReturn(course);
        when(enrollmentMapper.insert(any(CourseEnrollment.class))).thenReturn(1);
        CourseEnrollment r = service.enroll(1L, 10L);
        assertEquals("APPROVED", r.getApprovalStatus());
        verify(enrollmentMapper).insert(any(CourseEnrollment.class));
    }

    @Test
    @DisplayName("enroll APPROVAL 课程待审核")
    void enroll_approval() {
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        when(enrollmentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        Course course = new Course();
        course.setId(10L);
        course.setEnrollmentType("APPROVAL");
        when(courseService.getById(10L)).thenReturn(course);
        when(enrollmentMapper.insert(any(CourseEnrollment.class))).thenReturn(1);
        CourseEnrollment r = service.enroll(1L, 10L);
        assertEquals("PENDING", r.getApprovalStatus());
    }

    @Test
    @DisplayName("enroll 课程不存在默认 OPEN")
    void enroll_courseNull() {
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        when(enrollmentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(courseService.getById(10L)).thenReturn(null);
        when(enrollmentMapper.insert(any(CourseEnrollment.class))).thenReturn(1);
        CourseEnrollment r = service.enroll(1L, 10L);
        assertEquals("APPROVED", r.getApprovalStatus());
    }

    // ===== enrollStudent =====
    @Test
    @DisplayName("enrollStudent 课程不存在抛异常")
    void enrollStudent_noCourse() {
        when(courseService.getById(10L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.enrollStudent(1L, 10L));
    }

    @Test
    @DisplayName("enrollStudent 已加入抛异常")
    void enrollStudent_already() {
        Course course = new Course();
        course.setId(10L);
        when(courseService.getById(10L)).thenReturn(course);
        when(enrollmentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(ce(5L, 1L, 10L, true));
        assertThrows(BusinessException.class, () -> service.enrollStudent(1L, 10L));
    }

    @Test
    @DisplayName("enrollStudent 正常新增")
    void enrollStudent_ok() {
        Course course = new Course();
        course.setId(10L);
        when(courseService.getById(10L)).thenReturn(course);
        when(enrollmentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(enrollmentMapper.insert(any(CourseEnrollment.class))).thenReturn(1);
        service.enrollStudent(1L, 10L);
        verify(enrollmentMapper).insert(any(CourseEnrollment.class));
    }

    // ===== unenroll =====
    @Test
    @DisplayName("unenroll 记录不存在抛异常")
    void unenroll_notFound() {
        when(enrollmentMapper.selectById(5L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.unenroll(5L, 1L));
    }

    @Test
    @DisplayName("unenroll 非属主抛异常")
    void unenroll_notOwner() {
        when(enrollmentMapper.selectById(5L)).thenReturn(ce(5L, 2L, 10L, true));
        assertThrows(BusinessException.class, () -> service.unenroll(5L, 1L));
    }

    @Test
    @DisplayName("unenroll 正常软退课")
    void unenroll_ok() {
        CourseEnrollment e = ce(5L, 1L, 10L, true);
        when(enrollmentMapper.selectById(5L)).thenReturn(e);
        when(enrollmentMapper.updateById(any(CourseEnrollment.class))).thenReturn(1);
        service.unenroll(5L, 1L);
        assertFalse(e.getIsActive());
        verify(enrollmentMapper).updateById(any(CourseEnrollment.class));
    }
}
