package com.aicourse.service.impl;

import com.aicourse.entity.Classroom;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ClassroomMapper;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 班级管理服务单元测试（B 第一批）。
 * 构造器置 baseMapper=ClassroomMapper 并注入 UserService。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClassroomServiceImplTest {

    @Mock private ClassroomMapper classroomMapper;
    @Mock private UserService userService;
    @Mock private TaskService taskService;
    @Mock private SubmissionService submissionService;
    @InjectMocks private ClassroomServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", classroomMapper);
    }

    private Classroom classroom() {
        Classroom c = new Classroom();
        c.setId(1L);
        c.setStudentCount(0);
        c.setMaxStudents(30);
        c.setAdvisorId(1L);
        return c;
    }

    private User student(Long id) {
        User u = new User();
        u.setId(id);
        u.setRole("STUDENT");
        u.setClassId(null);
        u.setMajor("CS");
        u.setIsActive(true);
        u.setPassword("secret");
        return u;
    }

    @Test
    @DisplayName("班级详情-成功")
    void getClassroomDetail_ok() {
        when(classroomMapper.selectById(1L)).thenReturn(classroom());
        assertNotNull(service.getClassroomDetail(1L).getClassroom());
    }

    @Test
    @DisplayName("班级详情-不存在抛异常")
    void getClassroomDetail_notFound() {
        when(classroomMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getClassroomDetail(1L));
    }

    @Test
    @DisplayName("班级学生列表-密码清空")
    void getClassroomStudents_ok() {
        when(classroomMapper.selectById(1L)).thenReturn(classroom());
        Page<User> p = new Page<>(1, 10);
        p.setRecords(List.of(student(2L)));
        when(userService.page(any(Page.class), any())).thenReturn(p);
        Page<User> r = service.getClassroomStudents(1L, null, 1, 10);
        assertNull(r.getRecords().get(0).getPassword());
    }

    @Test
    @DisplayName("添加学生-成功")
    void addStudent_ok() {
        when(classroomMapper.selectById(1L)).thenReturn(classroom());
        when(userService.getById(2L)).thenReturn(student(2L));
        when(userService.updateById(any(User.class))).thenReturn(true);
        when(classroomMapper.updateById(any(Classroom.class))).thenReturn(1);
        service.addStudent(1L, 2L);
        verify(userService).updateById(any(User.class));
        verify(classroomMapper).updateById(any(Classroom.class));
    }

    @Test
    @DisplayName("添加学生-已在其他班级抛异常")
    void addStudent_alreadyInClass() {
        User s = student(2L);
        s.setClassId(99L);
        when(classroomMapper.selectById(1L)).thenReturn(classroom());
        when(userService.getById(2L)).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.addStudent(1L, 2L));
    }

    @Test
    @DisplayName("添加学生-容量已满抛异常")
    void addStudent_capacityFull() {
        Classroom c = classroom();
        c.setStudentCount(30);
        c.setMaxStudents(30);
        when(classroomMapper.selectById(1L)).thenReturn(c);
        when(userService.getById(2L)).thenReturn(student(2L));
        assertThrows(BusinessException.class, () -> service.addStudent(1L, 2L));
    }

    @Test
    @DisplayName("移除学生-成功")
    void removeStudent_ok() {
        Classroom c = classroom();
        c.setStudentCount(5);
        when(classroomMapper.selectById(1L)).thenReturn(c);
        User s = student(2L);
        s.setClassId(1L);
        when(userService.getById(2L)).thenReturn(s);
        when(userService.updateById(any(User.class))).thenReturn(true);
        when(classroomMapper.updateById(any(Classroom.class))).thenReturn(1);
        service.removeStudent(1L, 2L);
        verify(classroomMapper).updateById(any(Classroom.class));
    }

    @Test
    @DisplayName("移除学生-不在本班抛异常")
    void removeStudent_notInClass() {
        when(classroomMapper.selectById(1L)).thenReturn(classroom());
        User s = student(2L);
        s.setClassId(99L);
        when(userService.getById(2L)).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.removeStudent(1L, 2L));
    }

    @Test
    @DisplayName("导师班级列表-成功")
    void getAdvisorClassrooms_ok() {
        when(classroomMapper.selectList(any())).thenReturn(List.of(classroom()));
        assertEquals(1, service.getAdvisorClassrooms(1L).size());
    }

    @Test
    @DisplayName("班级统计-成功")
    void getClassroomStats_ok() {
        when(classroomMapper.selectById(1L)).thenReturn(classroom());
        when(userService.list(any(Wrapper.class))).thenReturn(List.of(student(2L)));
        assertEquals(1L, service.getClassroomStats(1L).getTotalStudents());
    }

    @Test
    @DisplayName("搜索班级-成功")
    void searchClassrooms_ok() {
        when(classroomMapper.selectList(any())).thenReturn(List.of(classroom()));
        assertEquals(1, service.searchClassrooms(null, null, null).size());
    }
}
