package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseChapterMapper;
import com.aicourse.service.CourseService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CourseChapterServiceImpl 单元测试（纯 Mockito）。
 * <p>覆盖课程管理权限校验（管理员放行 / 授课教师放行 / 无权抛异常）与章节增改删主路径。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseChapterServiceImplTest {

    @Mock
    private CourseService courseService;

    @Mock
    private CourseChapterMapper courseChapterMapper;

    private CourseChapterServiceImpl service;

    private Course course;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Course.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CourseChapter.class);
        service = new CourseChapterServiceImpl(courseService);
        ReflectionTestUtils.setField(service, "baseMapper", courseChapterMapper);

        course = new Course();
        course.setId(10L);
        course.setTeacherId(99L);
    }

    // ===================== createChapter =====================
    @Test
    @DisplayName("createChapter 管理员直接放行")
    void createChapter_admin() {
        CourseChapter chapter = new CourseChapter();
        chapter.setCourseId(10L);
        chapter.setTitle("第一章");
        CourseChapter result = service.createChapter(chapter, 1L, true);
        assertSame(chapter, result);
        verify(courseChapterMapper).insert(chapter);
    }

    @Test
    @DisplayName("createChapter 授课教师放行")
    void createChapter_teacher() {
        when(courseService.getById(10L)).thenReturn(course);
        CourseChapter chapter = new CourseChapter();
        chapter.setCourseId(10L);
        service.createChapter(chapter, 99L, false);
        verify(courseChapterMapper).insert(chapter);
    }

    @Test
    @DisplayName("createChapter 课程不存在抛无权管理")
    void createChapter_courseMissing() {
        when(courseService.getById(10L)).thenReturn(null);
        CourseChapter chapter = new CourseChapter();
        chapter.setCourseId(10L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createChapter(chapter, 99L, false));
        assertEquals("无权管理该课程", ex.getMessage());
        verify(courseChapterMapper, never()).insert(any(CourseChapter.class));
    }

    @Test
    @DisplayName("createChapter 非授课教师抛无权管理")
    void createChapter_notTeacher() {
        when(courseService.getById(10L)).thenReturn(course);
        CourseChapter chapter = new CourseChapter();
        chapter.setCourseId(10L);
        assertThrows(BusinessException.class, () -> service.createChapter(chapter, 88L, false));
        verify(courseChapterMapper, never()).insert(any(CourseChapter.class));
    }

    // ===================== updateChapter =====================
    @Test
    @DisplayName("updateChapter 章节不存在抛异常")
    void updateChapter_missing() {
        when(courseChapterMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> service.updateChapter(1L, new CourseChapter(), 99L, false));
    }

    @Test
    @DisplayName("updateChapter 权限校验通过则更新并返回最新章节")
    void updateChapter_ok() {
        CourseChapter existing = new CourseChapter();
        existing.setId(1L);
        existing.setCourseId(10L);
        when(courseChapterMapper.selectById(1L)).thenReturn(existing);
        when(courseService.getById(10L)).thenReturn(course);

        CourseChapter update = new CourseChapter();
        update.setTitle("新标题");
        CourseChapter result = service.updateChapter(1L, update, 99L, false);
        assertEquals(1L, update.getId());
        assertEquals(1L, result.getId());
        verify(courseChapterMapper).updateById(update);
    }

    // ===================== deleteChapter =====================
    @Test
    @DisplayName("deleteChapter 章节不存在抛异常")
    void deleteChapter_missing() {
        when(courseChapterMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> service.deleteChapter(1L, 99L, false));
    }

    @Test
    @DisplayName("deleteChapter 权限校验通过则删除")
    void deleteChapter_ok() {
        CourseChapter existing = new CourseChapter();
        existing.setId(1L);
        existing.setCourseId(10L);
        when(courseChapterMapper.selectById(1L)).thenReturn(existing);
        when(courseService.getById(10L)).thenReturn(course);

        service.deleteChapter(1L, 99L, false);
        verify(courseChapterMapper).deleteById(1L);
    }

    @Test
    @DisplayName("deleteChapter 非授课教师无权删除")
    void deleteChapter_forbidden() {
        CourseChapter existing = new CourseChapter();
        existing.setId(1L);
        existing.setCourseId(10L);
        when(courseChapterMapper.selectById(1L)).thenReturn(existing);
        when(courseService.getById(10L)).thenReturn(course);

        assertThrows(BusinessException.class, () -> service.deleteChapter(1L, 88L, false));
        verify(courseChapterMapper, never()).deleteById(any(Serializable.class));
    }
}
