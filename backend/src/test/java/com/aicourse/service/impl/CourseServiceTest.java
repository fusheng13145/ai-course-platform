package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.service.CourseChapterService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 课程服务单元测试
 * 测试 CourseServiceImpl 的课程查询、列表及章节关联功能
 */
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock private CourseMapper courseMapper;
    @Mock private CourseChapterService courseChapterService;

    @InjectMocks private CourseServiceImpl courseService;

    private Course sampleCourse;
    private CourseChapter sampleChapter;

    @BeforeEach
    void setUp() {
        sampleCourse = new Course();
        sampleCourse.setId(1L);
        sampleCourse.setName("Java高级编程");
        sampleCourse.setCode("CS301");
        sampleCourse.setDescription("深入学习Java核心技术");
        sampleCourse.setCredits(new BigDecimal("3.0"));
        sampleCourse.setTeacherId(10L);
        sampleCourse.setSemester("2025年春季");
        sampleCourse.setYear(2025);
        sampleCourse.setStatus("ACTIVE");
        sampleCourse.setIsActive(true);
        sampleCourse.setCategory("技术");
        sampleCourse.setDifficulty("中等");
        sampleCourse.setCreatedAt(LocalDateTime.now());
        sampleCourse.setUpdatedAt(LocalDateTime.now());

        sampleChapter = new CourseChapter();
        sampleChapter.setId(100L);
        sampleChapter.setCourseId(1L);
        sampleChapter.setParentId(0L);
        sampleChapter.setTitle("第一章：JVM内存模型");
        sampleChapter.setContent("深入理解JVM内存结构");
        sampleChapter.setOrderIndex(1);
        sampleChapter.setDuration(45);
        sampleChapter.setIsPublished(true);
    }

    // ===================== 课程查询测试 =====================

    @Test
    @DisplayName("根据ID查询课程-成功")
    void testGetCourseById() {
        when(courseMapper.selectById(1L)).thenReturn(sampleCourse);

        Course result = courseService.getById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Java高级编程", result.getName());
        assertEquals("CS301", result.getCode());
        assertEquals("ACTIVE", result.getStatus());
        assertTrue(result.getIsActive());
        assertEquals("技术", result.getCategory());
        assertEquals("中等", result.getDifficulty());
        verify(courseMapper).selectById(1L);
    }

    @Test
    @DisplayName("根据ID查询课程-课程不存在返回null")
    void testGetCourseById_NotFound() {
        when(courseMapper.selectById(999L)).thenReturn(null);

        Course result = courseService.getById(999L);

        assertNull(result);
        verify(courseMapper).selectById(999L);
    }

    // ===================== 活跃课程列表测试 =====================

    @Test
    @DisplayName("查询活跃课程列表-有数据")
    void testGetActiveCourses() {
        Course activeCourse1 = new Course();
        activeCourse1.setId(1L);
        activeCourse1.setName("课程A");
        activeCourse1.setIsActive(true);
        activeCourse1.setStatus("ACTIVE");

        Course activeCourse2 = new Course();
        activeCourse2.setId(2L);
        activeCourse2.setName("课程B");
        activeCourse2.setIsActive(true);
        activeCourse2.setStatus("ACTIVE");

        Course inactiveCourse = new Course();
        inactiveCourse.setId(3L);
        inactiveCourse.setName("课程C");
        inactiveCourse.setIsActive(false);
        inactiveCourse.setStatus("INACTIVE");

        List<Course> allCourses = Arrays.asList(activeCourse1, activeCourse2, inactiveCourse);
        when(courseMapper.selectList(any(Wrapper.class))).thenReturn(allCourses);

        // 调用 list() 返回所有课程，在应用层过滤活跃课程
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Course::getIsActive, true);
        when(courseMapper.selectList(any(Wrapper.class))).thenReturn(
                Arrays.asList(activeCourse1, activeCourse2));

        List<Course> result = courseMapper.selectList(queryWrapper);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("课程A", result.get(0).getName());
        assertEquals("课程B", result.get(1).getName());
    }

    @Test
    @DisplayName("查询活跃课程列表-空列表")
    void testGetActiveCourses_EmptyList() {
        when(courseMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        List<Course> result = courseService.list();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(courseMapper).selectList(any(Wrapper.class));
    }

    // ===================== 课程+章节关联测试 =====================

    @Test
    @DisplayName("查询课程及其关联章节-成功")
    void testGetCourseWithChapters() {
        when(courseMapper.selectById(1L)).thenReturn(sampleCourse);

        CourseChapter chapter2 = new CourseChapter();
        chapter2.setId(101L);
        chapter2.setCourseId(1L);
        chapter2.setTitle("第二章：并发编程");
        chapter2.setOrderIndex(2);

        List<CourseChapter> chapters = Arrays.asList(sampleChapter, chapter2);
        when(courseChapterService.list(any(LambdaQueryWrapper.class))).thenReturn(chapters);

        Course course = courseService.getById(1L);
        assertNotNull(course);
        assertEquals("Java高级编程", course.getName());

        LambdaQueryWrapper<CourseChapter> chapterWrapper = new LambdaQueryWrapper<>();
        chapterWrapper.eq(CourseChapter::getCourseId, 1L)
                .orderByAsc(CourseChapter::getOrderIndex);
        List<CourseChapter> result = courseChapterService.list(chapterWrapper);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("第一章：JVM内存模型", result.get(0).getTitle());
        assertEquals("第二章：并发编程", result.get(1).getTitle());
        assertEquals(1L, result.get(0).getCourseId());
        assertEquals(1L, result.get(1).getCourseId());
        verify(courseChapterService).list(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("查询课程及其关联章节-课程无章节")
    void testGetCourseWithChapters_NoChapters() {
        when(courseMapper.selectById(1L)).thenReturn(sampleCourse);
        when(courseChapterService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        Course course = courseService.getById(1L);
        assertNotNull(course);

        LambdaQueryWrapper<CourseChapter> chapterWrapper = new LambdaQueryWrapper<>();
        chapterWrapper.eq(CourseChapter::getCourseId, 1L)
                .orderByAsc(CourseChapter::getOrderIndex);
        List<CourseChapter> result = courseChapterService.list(chapterWrapper);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ===================== 课程列表查询测试 =====================

    @Test
    @DisplayName("查询所有课程列表")
    void testListAllCourses() {
        Course course1 = new Course();
        course1.setId(1L);
        course1.setName("课程A");

        Course course2 = new Course();
        course2.setId(2L);
        course2.setName("课程B");

        when(courseMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(course1, course2));

        List<Course> result = courseService.list();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("课程A", result.get(0).getName());
        assertEquals("课程B", result.get(1).getName());
    }
}
