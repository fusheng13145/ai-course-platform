package com.aicourse.service.impl;

import com.aicourse.entity.Classroom;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.User;
import com.aicourse.mapper.ClassroomMapper;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 课程驾驶舱「班级待批改作业数」聚合逻辑单元测试。
 * 覆盖 countPendingByCourse 的：正常聚合 / 无任务 / 无学生 / 无班级四态。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClassroomServiceImplPendingTest {

    @Mock private ClassroomMapper classroomMapper;
    @Mock private UserService userService;
    @Mock private TaskService taskService;
    @Mock private SubmissionService submissionService;
    @InjectMocks private ClassroomServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", classroomMapper);
    }

    private Classroom classroom(Long id, Long courseId, String name) {
        Classroom c = new Classroom();
        c.setId(id);
        c.setCourseId(courseId);
        c.setName(name);
        c.setIsActive(true);
        return c;
    }

    private User student(Long id, Long classId) {
        User u = new User();
        u.setId(id);
        u.setRole("STUDENT");
        u.setClassId(classId);
        return u;
    }

    private Task task(Long id, Long courseId) {
        Task t = new Task();
        t.setId(id);
        t.setCourseId(courseId);
        return t;
    }

    private Submission submitted(Long id, Long taskId, Long studentId) {
        Submission s = new Submission();
        s.setId(id);
        s.setTaskId(taskId);
        s.setStudentId(studentId);
        s.setStatus("SUBMITTED");
        return s;
    }

    @Test
    @DisplayName("正常聚合-按班级汇总待批改数")
    void countPendingByCourse_aggregatesByClass() {
        List<Classroom> classrooms = List.of(classroom(1L, 10L, "计科一班"), classroom(2L, 10L, "计科二班"));
        when(classroomMapper.selectList(any())).thenReturn(classrooms);
        List<User> students = List.of(student(101L, 1L), student(102L, 1L), student(201L, 2L));
        when(userService.list(any(Wrapper.class))).thenReturn(students);
        List<Task> tasks = List.of(task(501L, 10L), task(502L, 10L));
        when(taskService.list(any(Wrapper.class))).thenReturn(tasks);
        // 提交分布：班1 学生 101 有 2 条（501、502 各一）、102 有 1 条（502）；班2 学生 201 有 1 条（501）
        List<Submission> submissions = List.of(
                submitted(1L, 501L, 101L),   // 班1 待批
                submitted(2L, 502L, 101L),   // 班1 待批（101 第二条）
                submitted(3L, 501L, 201L),   // 班2 待批
                submitted(4L, 502L, 102L));  // 班1 待批
        when(submissionService.list(any(Wrapper.class))).thenReturn(submissions);

        List<Map<String, Object>> result = service.countPendingByCourse(10L);
        assertEquals(2, result.size());
        Map<String, Object> first = result.get(0);
        assertEquals(1L, first.get("classroomId"));
        assertEquals("计科一班", first.get("classroomName"));
        assertEquals(3L, first.get("pendingCount"));
        assertEquals(1L, result.get(1).get("pendingCount"));
    }

    @Test
    @DisplayName("无任务-各班级待批数为真实 0")
    void countPendingByCourse_noTasks() {
        when(classroomMapper.selectList(any())).thenReturn(List.of(classroom(1L, 10L, "一班")));
        when(userService.list(any(Wrapper.class))).thenReturn(List.of(student(101L, 1L)));
        when(taskService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = service.countPendingByCourse(10L);
        assertEquals(1, result.size());
        assertEquals(0L, result.get(0).get("pendingCount"));
    }

    @Test
    @DisplayName("无学生-各班级待批数为真实 0")
    void countPendingByCourse_noStudents() {
        when(classroomMapper.selectList(any())).thenReturn(List.of(classroom(1L, 10L, "一班")));
        when(userService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = service.countPendingByCourse(10L);
        assertEquals(1, result.size());
        assertEquals(0L, result.get(0).get("pendingCount"));
    }

    @Test
    @DisplayName("无关联班级-返回空列表")
    void countPendingByCourse_noClassrooms() {
        when(classroomMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertTrue(service.countPendingByCourse(10L).isEmpty());
    }

    @Test
    @DisplayName("courseId 为 null-返回空列表")
    void countPendingByCourse_nullCourse() {
        assertTrue(service.countPendingByCourse(null).isEmpty());
    }
}
