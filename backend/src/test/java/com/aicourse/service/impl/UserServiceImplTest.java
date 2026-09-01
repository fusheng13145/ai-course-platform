package com.aicourse.service.impl;

import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
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
    @DisplayName("获取教师学生 - 密码脱敏")
    void testGetTeacherStudents() {
        User s = new User(); s.setId(2L); s.setUsername("stu1"); s.setPassword("p1"); s.setRole("STUDENT"); s.setIsActive(true);
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(s));

        List<User> students = userService.getTeacherStudents(10L);

        assertEquals(1, students.size());
        assertNull(students.get(0).getPassword());
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
