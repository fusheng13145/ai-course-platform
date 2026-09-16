package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseEnrollmentMapper;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 用户服务单元测试（@InjectMocks + @Mock + 显式 baseMapper）
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserMapper userMapper;
    // 修复 B5：UserServiceImpl 新增「教师授课范围」依赖，构造注入需要这两个 mock
    @Mock private CourseMapper courseMapper;
    @Mock private CourseEnrollmentMapper courseEnrollmentMapper;
    @InjectMocks private UserServiceImpl userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);

        sampleUser = new User();
        sampleUser.setId(1L);
        sampleUser.setUsername("zhangsan");
        sampleUser.setPassword("secret");
        sampleUser.setFullName("张三");
        sampleUser.setEmail("zhangsan@example.com");
        sampleUser.setRole("STUDENT");
        sampleUser.setClassId(100L);
        sampleUser.setIsActive(true);
        sampleUser.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("获取个人资料 - 成功返回且密码脱敏")
    void testGetUserProfile_success() {
        when(userMapper.selectById(1L)).thenReturn(sampleUser);

        User result = userService.getUserProfile(1L);

        assertEquals("zhangsan", result.getUsername());
        assertNull(result.getPassword(), "密码必须置空");
    }

    @Test
    @DisplayName("获取个人资料 - 用户不存在抛异常")
    void testGetUserProfile_notFound() {
        when(userMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> userService.getUserProfile(99L));
    }

    @Test
    @DisplayName("更新个人资料 - 部分更新")
    void testUpdateProfile_partialUpdate() {
        when(userMapper.selectById(1L)).thenReturn(sampleUser);

        User updates = new User();
        updates.setFullName("张三丰");
        updates.setPhone("13900139000");

        userService.updateProfile(1L, updates);

        // 验证更新后的值
        assertEquals("张三丰", sampleUser.getFullName());
    }

    @Test
    @DisplayName("更新个人资料 - 用户不存在抛异常")
    void testUpdateProfile_notFound() {
        when(userMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> userService.updateProfile(99L, new User()));
    }

    @Test
    @DisplayName("获取同班同学 - 排除自己且密码脱敏")
    void testGetClassmates_success() {
        when(userMapper.selectById(1L)).thenReturn(sampleUser);
        User classmate = new User(); classmate.setId(2L); classmate.setUsername("lisi");
        classmate.setPassword("secret2"); classmate.setClassId(100L); classmate.setIsActive(true);
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(classmate));

        List<User> classmates = userService.getClassmates(1L);

        assertEquals(1, classmates.size());
        assertNull(classmates.get(0).getPassword());
    }

    @Test
    @DisplayName("获取同班同学 - 未加入班级抛异常")
    void testGetClassmates_noClass() {
        sampleUser.setClassId(null);
        when(userMapper.selectById(1L)).thenReturn(sampleUser);
        assertThrows(BusinessException.class, () -> userService.getClassmates(1L));
    }

    @Test
    @DisplayName("获取教师学生 - 仅返回该教师授课范围内有效选课的学生，且密码脱敏（修复 B5）")
    void testGetTeacherStudents_scopedToOwnCourses() {
        // 该教师名下有 1 门课程
        Course course = new Course();
        course.setId(100L);
        course.setTeacherId(10L);
        when(courseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(course));

        // 该课程下有 1 条有效且已审批的选课记录
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourseId(100L);
        enrollment.setStudentId(2L);
        enrollment.setIsActive(true);
        enrollment.setApprovalStatus("APPROVED");
        when(courseEnrollmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of(enrollment));

        User s = new User();
        s.setId(2L);
        s.setUsername("stu1");
        s.setPassword("p1");
        s.setRole("STUDENT");
        s.setIsActive(true);
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(s));

        List<User> students = userService.getTeacherStudents(10L);

        assertEquals(1, students.size());
        assertEquals("stu1", students.get(0).getUsername());
        assertNull(students.get(0).getPassword(), "密码必须置空");
        // 课程查询必须以 teacherId 收敛（修复前完全忽略该入参）
        verify(courseMapper).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("获取教师学生 - 该教师无课程时返回空且不查询学生（修复 B5）")
    void testGetTeacherStudents_noCourses_returnsEmpty() {
        when(courseMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        List<User> students = userService.getTeacherStudents(10L);

        assertTrue(students.isEmpty());
        verify(courseEnrollmentMapper, never()).selectList(any(Wrapper.class));
        verify(userMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("获取教师学生 - teacherId 为空抛业务异常")
    void testGetTeacherStudents_nullTeacherId() {
        assertThrows(BusinessException.class, () -> userService.getTeacherStudents(null));
    }

    @Test
    @DisplayName("用户统计 - 基本信息+同学数")
    void testGetUserStats() {
        when(userMapper.selectById(1L)).thenReturn(sampleUser);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(5L);

        com.aicourse.vo.UserStatsVO stats = userService.getUserStats(1L);

        assertEquals(1L, stats.getUserId());
        assertEquals(5L, stats.getClassmateCount());
    }

    @Test
    @DisplayName("搜索用户 - 分页返回并脱敏")
    void testSearchUsers() {
        Page<User> page = new Page<>(1, 10);
        page.setRecords(List.of(sampleUser));
        page.setTotal(1);
        when(userMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        Page<User> result = userService.searchUsers("zhang", "STUDENT", 1, 10);

        assertEquals(1, result.getTotal());
        assertNull(result.getRecords().get(0).getPassword());
    }
}
